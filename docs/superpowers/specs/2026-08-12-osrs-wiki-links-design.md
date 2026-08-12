# OSRS Wiki Item Cards + Drop Rarity — Design Spec

**Date:** 2026-08-12
**Status:** Approved, pending implementation

---

## Overview

Loot and Clue Scroll notifications change from a multi-line list of every dropped item to a single compact card for the **most valuable item only** (by the same GE/high-alch price already computed per stack). The card shows the item's icon, name (linked to its OSRS Wiki page), and value. Optionally, if the user opts in, it also shows the item's drop rarity looked up live from the wiki.

**Behavior change:** this replaces the current "list every item + total" format. Secondary items in a multi-item drop are no longer shown in the notification.

No lookup/API call is needed for the icon or page link — both are built directly from the item's name, since OSRS Wiki page/file titles match RuneLite's `ItemComposition` names almost exactly. Drop rarity is different: it requires a live query against the wiki's Bucket API, so it's a separate opt-in config item per AGENTS.md's third-party-server rule.

---

## Scope

`LootNotifier` and `ClueNotifier` only — both already resolve dropped items via `ItemComposition`, giving a canonical name to link/lookup from. Other notifiers (Pet, Collection Log) reference item names only as raw chat-message substrings and are out of scope.

---

## `OsrsWiki` (new utility class)

**File:** `src/main/java/space/covalent/rocketchat/OsrsWiki.java`

```java
public final class OsrsWiki
{
	private static final String BASE_URL = "https://oldschool.runescape.wiki/w/";

	private OsrsWiki() {}

	public static boolean isLinkable(String itemName)
	{
		return !"Coins".equals(itemName);
	}

	public static String pageUrl(String itemName)
	{
		return BASE_URL + slug(itemName);
	}

	public static String iconUrl(String itemName)
	{
		return BASE_URL + "Special:FilePath/" + slug(itemName + ".png");
	}

	private static String slug(String value)
	{
		String underscored = value.replace(' ', '_');
		try
		{
			return URLEncoder.encode(underscored, "UTF-8").replace("+", "%20");
		}
		catch (UnsupportedEncodingException e)
		{
			return underscored;
		}
	}
}
```

- `pageUrl()` — the item's wiki article, used as `title_link`.
- `iconUrl()` — the wiki's `Special:FilePath` redirect, which resolves directly to the item's icon file (title convention: `<Item name>.png`), used as `thumb_url`. No lookup needed — this is a real MediaWiki feature designed for exactly this.
- `isLinkable()` — **Coins** is excluded from both (its wiki page/icon aren't useful in a drop context); everything else is linked unconditionally.

---

## Drop Rarity Lookup

### Verified API shape

`https://oldschool.runescape.wiki/api.php?action=bucket&format=json&query=bucket('dropsline').select('item_name','drop_json').where('item_name','<Item Name>').run()`

Confirmed live against the real API. Example response for `Abyssal whip`:

```json
{
  "bucketQuery": "...",
  "bucket": [
    {
      "item_name": "Abyssal whip",
      "drop_json": "{\"Rarity\":\"1/512\",\"Dropped from\":\"Abyssal demon#Standard\", ...}"
    },
    {
      "item_name": "Abyssal whip",
      "drop_json": "{\"Rarity\":\"1/512\",\"Dropped from\":\"Abyssal demon#Wilderness Slayer Cave\", ...}"
    },
    {
      "item_name": "Abyssal whip",
      "drop_json": "{\"Rarity\":\"12/128\",\"Dropped from\":\"Unsired\", ...}"
    }
  ]
}
```

- One row per (item, source) pair. `drop_json` is a JSON string that must be parsed a second time.
- `Dropped from` sometimes has a `#Variant` suffix (different kill locations/phases with their own drop table) — match on the part before `#`.
- `Rarity` is usually `"N/M"` but can be non-numeric (`"Varies"`, free-text notes) — only numeric `N/M` values are used; anything else is treated as "not found".
- On `error` in the response body, empty `bucket`, no matching source, or a non-numeric `Rarity`: **no rarity line, notification still sends.**

### `RarityLookupService` (new)

**File:** `src/main/java/space/covalent/rocketchat/RarityLookupService.java`

```java
@Slf4j
@Singleton
public class RarityLookupService
{
	private static final String API_URL = "https://oldschool.runescape.wiki/api.php";
	private static final Pattern FRACTION = Pattern.compile("^(\\d+)/(\\d+)$");

	@Inject OkHttpClient okHttpClient;
	@Inject Gson gson;

	private final Map<String, Rarity> cache = new ConcurrentHashMap<>();

	@Value
	public static class Rarity
	{
		String raw;
		double percent;
	}

	public void lookup(String itemName, String sourceName, Consumer<Rarity> callback)
	{
		String cacheKey = itemName + "|" + sourceName;
		Rarity cached = cache.get(cacheKey);
		if (cached != null)
		{
			callback.accept(cached);
			return;
		}

		String query = "bucket('dropsline').select('item_name','drop_json').where('item_name','"
			+ itemName.replace("'", "\\'") + "').run()";
		HttpUrl url = HttpUrl.parse(API_URL).newBuilder()
			.addQueryParameter("action", "bucket")
			.addQueryParameter("format", "json")
			.addQueryParameter("query", query)
			.build();

		okHttpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				callback.accept(null);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				Rarity rarity = parse(response, sourceName);
				if (rarity != null)
				{
					cache.put(cacheKey, rarity);
				}
				response.close();
				callback.accept(rarity);
			}
		});
	}

	private Rarity parse(Response response, String sourceName)
	{
		try
		{
			BucketResponse body = gson.fromJson(response.body().charStream(), BucketResponse.class);
			if (body == null || body.bucket == null)
			{
				return null;
			}

			for (BucketRow row : body.bucket)
			{
				DropJson drop = gson.fromJson(row.dropJson, DropJson.class);
				if (drop == null || drop.droppedFrom == null || drop.rarity == null)
				{
					continue;
				}

				String source = drop.droppedFrom.split("#", 2)[0];
				if (!source.equalsIgnoreCase(sourceName))
				{
					continue;
				}

				Matcher m = FRACTION.matcher(drop.rarity.trim());
				if (!m.matches())
				{
					continue;
				}

				double percent = Double.parseDouble(m.group(1)) / Double.parseDouble(m.group(2)) * 100;
				return new Rarity(drop.rarity, percent);
			}
		}
		catch (Exception e)
		{
			log.debug("Failed to parse rarity lookup response", e);
		}
		return null;
	}

	private static class BucketResponse
	{
		List<BucketRow> bucket;
	}

	private static class BucketRow
	{
		String item_name;
		@SerializedName("drop_json")
		String dropJson;
	}

	private static class DropJson
	{
		@SerializedName("Rarity")
		String rarity;
		@SerializedName("Dropped from")
		String droppedFrom;
	}
}
```

- In-memory cache (`item|source` key) avoids re-querying the wiki on repeat kills within the same session — no eviction needed, key space is bounded by what the player actually kills.
- Runs entirely on the OkHttp thread pool via `enqueue()` — never blocks the client thread, per AGENTS.md. `callback` fires from that thread; the notifiers below send the webhook from inside it, which is fine since `WebhookClient.send()` itself just does another `enqueue()` and touches nothing on `client`.

---

## Config

New item in the existing "Loot" section (applies to both Loot and Clue notifications):

```java
@ConfigItem(
	keyName = "showDropRarity",
	name = "Show drop rarity",
	description = "Look up and display the item's drop rarity from the OSRS Wiki",
	section = lootSection,
	warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
)
default boolean showDropRarity() { return false; }
```

Default **off** per AGENTS.md's third-party-server config rule. The plain wiki link/icon (no rarity) requires no toggle and no warning — it's just URL construction, no network call from the plugin.

---

## Changes to `LootNotifier` / `ClueNotifier`

Both notifiers change from "build item lines for every stack + total" to "find the single highest-value stack, send one card for it":

```java
ItemStack bestStack = null;
ItemComposition bestComp = null;
long bestPrice = -1;

for (ItemStack stack : items)
{
	ItemComposition comp = itemManager.getItemComposition(stack.getId());
	long price = (ironManMode != null && ironManMode.isIronman())
		? (long) comp.getHaPrice() * stack.getQuantity()
		: (long) itemManager.getItemPrice(stack.getId()) * stack.getQuantity();
	if (price > bestPrice)
	{
		bestPrice = price;
		bestStack = stack;
		bestComp = comp;
	}
}

if (bestPrice < config.minLootValue()) return;   // LootNotifier only; ClueNotifier has no value gate
```

Then build and send:

```java
String itemName = bestComp.getName();
String valueLine = formatGp(bestPrice) + " gp";

if (config.showDropRarity())
{
	rarityLookupService.lookup(itemName, event.getName(),
		rarity -> webhookClient.send(config.webhookUrl(), buildCard(event.getName(), bestStack, itemName, valueLine, rarity)));
}
else
{
	webhookClient.send(config.webhookUrl(), buildCard(event.getName(), bestStack, itemName, valueLine, null));
}
```

```java
private RocketChatPayload buildCard(String source, ItemStack stack, String itemName, String valueLine, RarityLookupService.Rarity rarity)
{
	String text = valueLine;
	if (rarity != null)
	{
		text += "\n" + rarity.getRaw() + " (" + String.format("%.2f%%", rarity.getPercent()) + ")";
	}

	RocketChatPayload.Attachment.AttachmentBuilder attachment = RocketChatPayload.Attachment.builder()
		.title(stack.getQuantity() + "x " + itemName)
		.text(text)
		.color("#FFD700"); // "#8B4513" for ClueNotifier

	if (OsrsWiki.isLinkable(itemName))
	{
		attachment.titleLink(OsrsWiki.pageUrl(itemName));
		attachment.thumbUrl(OsrsWiki.iconUrl(itemName));
	}

	return RocketChatPayload.builder()
		.text("💰 Loot from " + source)   // "📜 " + tierName + " Clue Scroll completed" for ClueNotifier
		.attachments(Collections.singletonList(attachment.build()))
		.build();
}
```

`RarityLookupService` is injected alongside `ItemManager` in both notifiers.

---

## Files Changed

| File | Change |
|------|--------|
| `OsrsWiki.java` | New utility class |
| `RarityLookupService.java` | New — Bucket API query, parsing, caching |
| `RocketChatConnectorConfig.java` | New `showDropRarity` config item |
| `LootNotifier.java` | Single-highest-value-item card, optional rarity |
| `ClueNotifier.java` | Same restructure |

---

## Testing

- `OsrsWikiTest` (new): URL encoding for names with spaces, parentheses (e.g. "Amulet of fury (or)"), apostrophes (e.g. "Zamorak's greater blessing"); Coins returns `isLinkable() == false`.
- `RarityLookupServiceTest` (new, using the existing `mockwebserver` test dependency): parses a multi-row response and matches the correct source by stripping `#Variant`; falls back to `null` on non-numeric `Rarity`, no matching source, network failure, and malformed JSON; verifies the cache short-circuits a second lookup for the same key.
- `LootNotifierTest` / `ClueNotifierTest`: update existing assertions for the new single-card shape — highest-value stack is chosen when multiple items drop, `minLootValue` gates on the best item's price, rarity line appears only when `showDropRarity()` is true and the (mocked) lookup returns a `Rarity`.
