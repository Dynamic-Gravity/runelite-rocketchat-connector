# OSRS Wiki Item Cards + Drop Rarity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Loot and Clue Scroll notifications show a single compact card (icon + wiki-linked name + value + optional rarity) for the most valuable dropped item, instead of a multi-line list of every item.

**Architecture:** A pure `OsrsWiki` utility builds page/icon URLs directly from item names (no network call). A new `RarityLookupService` queries the OSRS Wiki's Bucket API asynchronously via OkHttp, parses the nested `drop_json` blob, matches by source name, and caches results. `LootNotifier`/`ClueNotifier` pick the highest-value stack, build one `RocketChatPayload.Attachment` for it, and — if the user opted in — defer sending until the rarity lookup's callback fires.

**Tech Stack:** Java 11, OkHttp (`enqueue`), Gson, Lombok, JUnit4 + Mockito + MockWebServer (all already in `build.gradle`).

## Global Constraints

- Java 11 compatible, no reflection, no external processes (AGENTS.md).
- Never block the client thread on network IO; all HTTP goes through OkHttp `enqueue()` (AGENTS.md).
- `@Inject OkHttpClient` / `@Inject Gson` only — never construct new instances in production code (AGENTS.md).
- Any config item that causes the plugin to contact a third-party server must default to `false` and carry the exact warning string: `"This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"` (AGENTS.md).
- Never rename an existing config key/group without a migration — this plan only *adds* a new key, so no migration is needed.
- `log.debug()` for diagnostics, never `log.info()` for per-event logging (AGENTS.md).

---

### Task 1: `OsrsWiki` utility class

**Files:**
- Create: `src/main/java/space/covalent/rocketchat/OsrsWiki.java`
- Test: `src/test/java/space/covalent/rocketchat/OsrsWikiTest.java`

**Interfaces:**
- Produces: `OsrsWiki.isLinkable(String itemName): boolean`, `OsrsWiki.pageUrl(String itemName): String`, `OsrsWiki.iconUrl(String itemName): String` — used by Task 3 and Task 4.

- [ ] **Step 1: Write the failing test**

```java
package space.covalent.rocketchat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OsrsWikiTest
{
	@Test
	public void testPageUrlForSimpleName()
	{
		assertEquals("https://oldschool.runescape.wiki/w/Abyssal_whip", OsrsWiki.pageUrl("Abyssal whip"));
	}

	@Test
	public void testPageUrlEncodesParentheses()
	{
		assertEquals("https://oldschool.runescape.wiki/w/Amulet_of_fury_%28or%29", OsrsWiki.pageUrl("Amulet of fury (or)"));
	}

	@Test
	public void testPageUrlEncodesApostrophe()
	{
		assertEquals("https://oldschool.runescape.wiki/w/Zamorak%27s_brew", OsrsWiki.pageUrl("Zamorak's brew"));
	}

	@Test
	public void testIconUrlAppendsPngExtension()
	{
		assertEquals("https://oldschool.runescape.wiki/w/Special:FilePath/Abyssal_whip.png", OsrsWiki.iconUrl("Abyssal whip"));
	}

	@Test
	public void testCoinsIsNotLinkable()
	{
		assertFalse(OsrsWiki.isLinkable("Coins"));
	}

	@Test
	public void testOtherItemsAreLinkable()
	{
		assertTrue(OsrsWiki.isLinkable("Abyssal whip"));
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests space.covalent.rocketchat.OsrsWikiTest`
Expected: FAIL — compile error, `OsrsWiki` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package space.covalent.rocketchat;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

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

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests space.covalent.rocketchat.OsrsWikiTest`
Expected: PASS, 6 tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/OsrsWiki.java src/test/java/space/covalent/rocketchat/OsrsWikiTest.java
git commit -m "feat: add OsrsWiki page/icon URL builder"
```

---

### Task 2: `RarityLookupService`

**Files:**
- Create: `src/main/java/space/covalent/rocketchat/RarityLookupService.java`
- Test: `src/test/java/space/covalent/rocketchat/RarityLookupServiceTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `RarityLookupService` is a `@Singleton` with package-private fields `OkHttpClient okHttpClient`, `Gson gson`, `String apiUrl` (settable directly in tests, same pattern as `WebhookClient`). Method `lookup(String itemName, String sourceName, Consumer<Rarity> callback): void`. Nested `RarityLookupService.Rarity` is a Lombok `@Value` with `String raw` and `double percent` (exposes `getRaw()`, `getPercent()`) — used by Task 3 and Task 4.

The wiki API shape below was verified live against `https://oldschool.runescape.wiki/api.php` during design — this is real, not assumed.

- [ ] **Step 1: Write the failing test**

```java
package space.covalent.rocketchat;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RarityLookupServiceTest
{
	private MockWebServer server;
	private RarityLookupService service;

	@Before
	public void setUp() throws Exception
	{
		server = new MockWebServer();
		server.start();

		service = new RarityLookupService();
		service.okHttpClient = new OkHttpClient();
		service.gson = new Gson();
		service.apiUrl = server.url("/api.php").toString();
	}

	@After
	public void tearDown() throws Exception
	{
		server.shutdown();
	}

	private RarityLookupService.Rarity awaitLookup(String itemName, String sourceName) throws InterruptedException
	{
		AtomicReference<RarityLookupService.Rarity> result = new AtomicReference<>();
		CountDownLatch latch = new CountDownLatch(1);
		service.lookup(itemName, sourceName, rarity ->
		{
			result.set(rarity);
			latch.countDown();
		});
		assertTrue("lookup callback did not fire", latch.await(5, TimeUnit.SECONDS));
		return result.get();
	}

	@Test
	public void testMatchesSourceIgnoringVariantSuffix() throws InterruptedException
	{
		server.enqueue(new MockResponse().setResponseCode(200).setBody(
			"{\"bucket\":["
				+ "{\"item_name\":\"Abyssal whip\",\"drop_json\":\"{\\\"Rarity\\\":\\\"1/512\\\",\\\"Dropped from\\\":\\\"Abyssal demon#Standard\\\"}\"},"
				+ "{\"item_name\":\"Abyssal whip\",\"drop_json\":\"{\\\"Rarity\\\":\\\"12/128\\\",\\\"Dropped from\\\":\\\"Unsired\\\"}\"}"
				+ "]}"));

		RarityLookupService.Rarity rarity = awaitLookup("Abyssal whip", "Abyssal demon");

		assertEquals("1/512", rarity.getRaw());
		assertEquals(100.0 / 512, rarity.getPercent(), 0.0001);
	}

	@Test
	public void testReturnsNullWhenNoSourceMatches() throws InterruptedException
	{
		server.enqueue(new MockResponse().setResponseCode(200).setBody(
			"{\"bucket\":[{\"item_name\":\"Abyssal whip\",\"drop_json\":\"{\\\"Rarity\\\":\\\"1/512\\\",\\\"Dropped from\\\":\\\"Abyssal demon\\\"}\"}]}"));

		RarityLookupService.Rarity rarity = awaitLookup("Abyssal whip", "Greater abyssal demon");

		assertNull(rarity);
	}

	@Test
	public void testReturnsNullForNonNumericRarity() throws InterruptedException
	{
		server.enqueue(new MockResponse().setResponseCode(200).setBody(
			"{\"bucket\":[{\"item_name\":\"Raw shrimps\",\"drop_json\":\"{\\\"Rarity\\\":\\\"Varies\\\",\\\"Dropped from\\\":\\\"Fishing Trawler\\\"}\"}]}"));

		RarityLookupService.Rarity rarity = awaitLookup("Raw shrimps", "Fishing Trawler");

		assertNull(rarity);
	}

	@Test
	public void testReturnsNullOnServerError() throws InterruptedException
	{
		server.enqueue(new MockResponse().setResponseCode(500));

		RarityLookupService.Rarity rarity = awaitLookup("Abyssal whip", "Abyssal demon");

		assertNull(rarity);
	}

	@Test
	public void testCachesSecondLookup() throws InterruptedException
	{
		server.enqueue(new MockResponse().setResponseCode(200).setBody(
			"{\"bucket\":[{\"item_name\":\"Abyssal whip\",\"drop_json\":\"{\\\"Rarity\\\":\\\"1/512\\\",\\\"Dropped from\\\":\\\"Abyssal demon\\\"}\"}]}"));

		awaitLookup("Abyssal whip", "Abyssal demon");
		awaitLookup("Abyssal whip", "Abyssal demon");

		assertEquals(1, server.getRequestCount());
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests space.covalent.rocketchat.RarityLookupServiceTest`
Expected: FAIL — compile error, `RarityLookupService` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package space.covalent.rocketchat;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
public class RarityLookupService
{
	private static final Pattern FRACTION = Pattern.compile("^(\\d+)/(\\d+)$");

	@Inject
	OkHttpClient okHttpClient;

	@Inject
	Gson gson;

	String apiUrl = "https://oldschool.runescape.wiki/api.php";

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
		HttpUrl url = HttpUrl.parse(apiUrl).newBuilder()
			.addQueryParameter("action", "bucket")
			.addQueryParameter("format", "json")
			.addQueryParameter("query", query)
			.build();

		okHttpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Rarity lookup failed", e);
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

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests space.covalent.rocketchat.RarityLookupServiceTest`
Expected: PASS, 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/RarityLookupService.java src/test/java/space/covalent/rocketchat/RarityLookupServiceTest.java
git commit -m "feat: add RarityLookupService for OSRS Wiki drop-rate queries"
```

---

### Task 3: `LootNotifier` single-item card + rarity toggle

**Files:**
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatConnectorConfig.java`
- Modify: `src/main/java/space/covalent/rocketchat/notifiers/LootNotifier.java`
- Modify: `src/test/java/space/covalent/rocketchat/notifiers/LootNotifierTest.java`

**Interfaces:**
- Consumes: `OsrsWiki.isLinkable/pageUrl/iconUrl` (Task 1), `RarityLookupService` + `RarityLookupService.Rarity` (Task 2), `RocketChatConnectorConfig.showDropRarity()` (added in this task).
- Produces: `RocketChatConnectorConfig.showDropRarity(): boolean` (default `false`) — also consumed by Task 4.

- [ ] **Step 1: Add the config item**

In `src/main/java/space/covalent/rocketchat/RocketChatConnectorConfig.java`, immediately after the existing `minLootValue()` method (inside the `lootSection`), add:

```java
	@ConfigItem(
		keyName = "showDropRarity",
		name = "Show drop rarity",
		description = "Look up and display the item's drop rarity from the OSRS Wiki",
		section = lootSection,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean showDropRarity()
	{
		return false;
	}
```

- [ ] **Step 2: Write the failing tests**

Replace the full contents of `src/test/java/space/covalent/rocketchat/notifiers/LootNotifierTest.java` with:

```java
package space.covalent.rocketchat.notifiers;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.RarityLookupService;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LootNotifierTest
{
	@Mock
	RocketChatConnectorConfig config;

	@Mock
	WebhookClient webhookClient;

	@Mock
	ItemManager itemManager;

	@Mock
	RarityLookupService rarityLookupService;

	@InjectMocks
	LootNotifier notifier;

	@Test
	public void testSendsCardForHighestValueItem()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int cheapId = 526;
		ItemComposition cheapComp = mock(ItemComposition.class);
		when(cheapComp.getName()).thenReturn("Bones");
		when(itemManager.getItemComposition(cheapId)).thenReturn(cheapComp);
		when(itemManager.getItemPrice(cheapId)).thenReturn(50);

		int expensiveId = 4151;
		ItemComposition expensiveComp = mock(ItemComposition.class);
		when(expensiveComp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(expensiveId)).thenReturn(expensiveComp);
		when(itemManager.getItemPrice(expensiveId)).thenReturn(2000000);

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Arrays.asList(new ItemStack(cheapId, 1), new ItemStack(expensiveId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("1x Abyssal whip", attachment.getTitle());
	}

	@Test
	public void testSkipsWhenBestValueBelowThreshold()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(100000);

		int itemId = 526;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Bones");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(50);

		LootReceived event = new LootReceived("Goblin", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testSkipsEmptyLoot()
	{
		when(config.notifyOnLoot()).thenReturn(true);

		LootReceived event = new LootReceived("Some Boss", 0, LootRecordType.NPC,
			Collections.emptyList(), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testUsesHighAlchValueInIronmanMode()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.ironManMode()).thenReturn(IronManMode.IRONMAN);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(comp.getHaPrice()).thenReturn(120000);
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient).send(any(), any());
		verify(itemManager, never()).getItemPrice(itemId);
	}

	@Test
	public void testHighAlchValueAppliedToMinThreshold()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(200000);
		when(config.ironManMode()).thenReturn(IronManMode.IRONMAN);

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(comp.getHaPrice()).thenReturn(120000);
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testCardIncludesWikiLinkAndIcon()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("https://oldschool.runescape.wiki/w/Abyssal_whip", attachment.getTitleLink());
		assertEquals("https://oldschool.runescape.wiki/w/Special:FilePath/Abyssal_whip.png", attachment.getThumbUrl());
	}

	@Test
	public void testCoinsOmitsWikiLink()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int itemId = 995;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Coins");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(1000);

		LootReceived event = new LootReceived("Man", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1000)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertNull(attachment.getTitleLink());
		assertNull(attachment.getThumbUrl());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testRarityLineAppendedWhenEnabledAndFound()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.showDropRarity()).thenReturn(true);

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		doAnswer(invocation ->
		{
			Consumer<RarityLookupService.Rarity> callback = invocation.getArgument(2);
			callback.accept(new RarityLookupService.Rarity("1/512", 0.1953125));
			return null;
		}).when(rarityLookupService).lookup(anyString(), anyString(), any());

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		String text = captor.getValue().getAttachments().get(0).getText();
		assertTrue(text.contains("1/512 (0.20%)"));
	}

	@Test
	public void testRarityLookupSkippedWhenDisabled()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.showDropRarity()).thenReturn(false);

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(rarityLookupService, never()).lookup(any(), any(), any());
		verify(webhookClient).send(any(), any());
	}
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests space.covalent.rocketchat.notifiers.LootNotifierTest`
Expected: FAIL — compile errors (no `RarityLookupService` field on `LootNotifier`, old multi-item behavior doesn't match new assertions).

- [ ] **Step 4: Rewrite `LootNotifier`**

Replace the full contents of `src/main/java/space/covalent/rocketchat/notifiers/LootNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.Collection;
import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import space.covalent.rocketchat.ClueTier;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.OsrsWiki;
import space.covalent.rocketchat.RarityLookupService;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class LootNotifier
{
	@Inject
	RocketChatConnectorConfig config;

	@Inject
	WebhookClient webhookClient;

	@Inject
	ItemManager itemManager;

	@Inject
	RarityLookupService rarityLookupService;

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!config.notifyOnLoot())
		{
			return;
		}

		// Skip clue scroll rewards — handled by ClueNotifier
		if (ClueTier.fromLootSource(event.getName()) != null)
		{
			return;
		}

		Collection<ItemStack> items = event.getItems();
		if (items.isEmpty())
		{
			return;
		}

		IronManMode ironManMode = config.ironManMode();
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

		if (bestPrice < config.minLootValue())
		{
			return;
		}

		sendCard(event.getName(), bestStack, bestComp, bestPrice);
	}

	private void sendCard(String source, ItemStack stack, ItemComposition comp, long price)
	{
		String itemName = comp.getName();
		String valueLine = formatGp(price) + " gp";

		if (config.showDropRarity())
		{
			rarityLookupService.lookup(itemName, source,
				rarity -> webhookClient.send(config.webhookUrl(), buildPayload(source, stack, itemName, valueLine, rarity)));
		}
		else
		{
			webhookClient.send(config.webhookUrl(), buildPayload(source, stack, itemName, valueLine, null));
		}
	}

	private RocketChatPayload buildPayload(String source, ItemStack stack, String itemName, String valueLine, RarityLookupService.Rarity rarity)
	{
		String text = valueLine;
		if (rarity != null)
		{
			text += "\n" + rarity.getRaw() + " (" + String.format("%.2f%%", rarity.getPercent()) + ")";
		}

		RocketChatPayload.Attachment.AttachmentBuilder attachment = RocketChatPayload.Attachment.builder()
			.title(stack.getQuantity() + "x " + itemName)
			.text(text)
			.color("#FFD700");

		if (OsrsWiki.isLinkable(itemName))
		{
			attachment.titleLink(OsrsWiki.pageUrl(itemName));
			attachment.thumbUrl(OsrsWiki.iconUrl(itemName));
		}

		return RocketChatPayload.builder()
			.text("💰 Loot from " + source)
			.attachments(Collections.singletonList(attachment.build()))
			.build();
	}

	private static String formatGp(long value)
	{
		if (value >= 1_000_000)
		{
			return String.format("%.1fM", value / 1_000_000.0);
		}
		if (value >= 1_000)
		{
			return String.format("%.1fK", value / 1_000.0);
		}
		return String.valueOf(value);
	}
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests space.covalent.rocketchat.notifiers.LootNotifierTest`
Expected: PASS, 9 tests green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/RocketChatConnectorConfig.java src/main/java/space/covalent/rocketchat/notifiers/LootNotifier.java src/test/java/space/covalent/rocketchat/notifiers/LootNotifierTest.java
git commit -m "feat: LootNotifier shows one wiki-linked card for the highest-value item"
```

---

### Task 4: `ClueNotifier` single-item card + rarity

**Files:**
- Modify: `src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java`
- Modify: `src/test/java/space/covalent/rocketchat/notifiers/ClueNotifierTest.java`

**Interfaces:**
- Consumes: `OsrsWiki.isLinkable/pageUrl/iconUrl` (Task 1), `RarityLookupService` + `RarityLookupService.Rarity` (Task 2), `RocketChatConnectorConfig.showDropRarity()` (Task 3).
- Produces: nothing consumed by later tasks — this is the last task.

- [ ] **Step 1: Write the failing tests**

Replace the full contents of `src/test/java/space/covalent/rocketchat/notifiers/ClueNotifierTest.java`:

```java
package space.covalent.rocketchat.notifiers;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import space.covalent.rocketchat.ClueTier;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.RarityLookupService;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ClueNotifierTest
{
	@Mock RocketChatConnectorConfig config;
	@Mock WebhookClient webhookClient;
	@Mock ItemManager itemManager;
	@Mock RarityLookupService rarityLookupService;

	@InjectMocks ClueNotifier notifier;

	@Test
	public void testUsesHighAlchValueInIronmanMode()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.ironManMode()).thenReturn(IronManMode.IRONMAN);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(comp.getHaPrice()).thenReturn(120000);
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient).send(any(), any());
		verify(itemManager, never()).getItemPrice(itemId);
	}

	@Test
	public void testUsesGePriceWhenNotIronman()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int itemId = 4151;
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient).send(any(), any());
		verify(itemManager).getItemPrice(itemId);
	}

	@Test
	public void testSendsCardForHighestValueItem()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int cheapId = 526;
		ItemComposition cheapComp = mock(ItemComposition.class);
		when(cheapComp.getName()).thenReturn("Bones");
		when(itemManager.getItemComposition(cheapId)).thenReturn(cheapComp);
		when(itemManager.getItemPrice(cheapId)).thenReturn(50);

		int expensiveId = 4151;
		ItemComposition expensiveComp = mock(ItemComposition.class);
		when(expensiveComp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(expensiveId)).thenReturn(expensiveComp);
		when(itemManager.getItemPrice(expensiveId)).thenReturn(2000000);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Arrays.asList(new ItemStack(cheapId, 1), new ItemStack(expensiveId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("1x Abyssal whip", attachment.getTitle());
	}

	@Test
	public void testCardIncludesWikiLinkAndIcon()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("https://oldschool.runescape.wiki/w/Abyssal_whip", attachment.getTitleLink());
		assertEquals("https://oldschool.runescape.wiki/w/Special:FilePath/Abyssal_whip.png", attachment.getThumbUrl());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testRarityLineAppendedWhenEnabledAndFound()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.showDropRarity()).thenReturn(true);

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		doAnswer(invocation ->
		{
			Consumer<RarityLookupService.Rarity> callback = invocation.getArgument(2);
			callback.accept(new RarityLookupService.Rarity("12/128", 9.375));
			return null;
		}).when(rarityLookupService).lookup(anyString(), anyString(), any());

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		String text = captor.getValue().getAttachments().get(0).getText();
		assertTrue(text.contains("12/128 (9.38%)"));
	}
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests space.covalent.rocketchat.notifiers.ClueNotifierTest`
Expected: FAIL — compile errors (no `RarityLookupService` field on `ClueNotifier`, old multi-item text format doesn't match new assertions).

- [ ] **Step 3: Rewrite `ClueNotifier`**

Replace the full contents of `src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.Collection;
import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import space.covalent.rocketchat.ClueTier;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.OsrsWiki;
import space.covalent.rocketchat.RarityLookupService;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class ClueNotifier
{
	@Inject
	RocketChatConnectorConfig config;

	@Inject
	WebhookClient webhookClient;

	@Inject
	ItemManager itemManager;

	@Inject
	RarityLookupService rarityLookupService;

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!config.notifyOnClue())
		{
			return;
		}

		ClueTier tier = ClueTier.fromLootSource(event.getName());
		if (tier == null)
		{
			return;
		}

		if (tier.getRank() < config.minClueTier().getRank())
		{
			return;
		}

		Collection<ItemStack> items = event.getItems();
		if (items.isEmpty())
		{
			return;
		}

		IronManMode ironManMode = config.ironManMode();
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

		String tierName = tier.name().charAt(0) + tier.name().substring(1).toLowerCase();
		sendCard(tierName, event.getName(), bestStack, bestComp, bestPrice);
	}

	private void sendCard(String tierName, String source, ItemStack stack, ItemComposition comp, long price)
	{
		String itemName = comp.getName();
		String valueLine = price > 0 ? formatGp(price) + " gp" : null;

		if (config.showDropRarity())
		{
			rarityLookupService.lookup(itemName, source,
				rarity -> webhookClient.send(config.webhookUrl(), buildPayload(tierName, stack, itemName, valueLine, rarity)));
		}
		else
		{
			webhookClient.send(config.webhookUrl(), buildPayload(tierName, stack, itemName, valueLine, null));
		}
	}

	private RocketChatPayload buildPayload(String tierName, ItemStack stack, String itemName, String valueLine, RarityLookupService.Rarity rarity)
	{
		StringBuilder text = new StringBuilder();
		if (valueLine != null)
		{
			text.append(valueLine);
		}
		if (rarity != null)
		{
			if (text.length() > 0)
			{
				text.append("\n");
			}
			text.append(rarity.getRaw()).append(" (").append(String.format("%.2f%%", rarity.getPercent())).append(")");
		}

		RocketChatPayload.Attachment.AttachmentBuilder attachment = RocketChatPayload.Attachment.builder()
			.title(stack.getQuantity() + "x " + itemName)
			.text(text.toString())
			.color("#8B4513");

		if (OsrsWiki.isLinkable(itemName))
		{
			attachment.titleLink(OsrsWiki.pageUrl(itemName));
			attachment.thumbUrl(OsrsWiki.iconUrl(itemName));
		}

		return RocketChatPayload.builder()
			.text("📜 " + tierName + " Clue Scroll completed")
			.attachments(Collections.singletonList(attachment.build()))
			.build();
	}

	private static String formatGp(long value)
	{
		if (value >= 1_000_000)
		{
			return String.format("%.1fM", value / 1_000_000.0);
		}
		if (value >= 1_000)
		{
			return String.format("%.1fK", value / 1_000.0);
		}
		return String.valueOf(value);
	}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests space.covalent.rocketchat.notifiers.ClueNotifierTest`
Expected: PASS, 5 tests green.

- [ ] **Step 5: Run the full test suite**

Run: `./gradlew test`
Expected: PASS, all tests across the project green (confirms no other notifier or test was broken by the `RocketChatConnectorConfig` change).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java src/test/java/space/covalent/rocketchat/notifiers/ClueNotifierTest.java
git commit -m "feat: ClueNotifier shows one wiki-linked card for the highest-value reward"
```
