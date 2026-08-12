# OSRS Wiki Links on Drop Items — Design Spec

**Date:** 2026-08-12
**Status:** Approved, pending implementation

---

## Overview

Item names in Loot and Clue Scroll notifications become clickable links to their OSRS Wiki page. No new config item — links ride along with the existing `notifyOnLoot`/`notifyOnClue` toggles. No network lookup: the URL is built directly from the item's canonical `ItemComposition` name, which already matches the wiki's page-title convention.

---

## Scope

`LootNotifier` and `ClueNotifier` only. Both already resolve dropped items via `ItemComposition`, giving a canonical name to link from. Other notifiers (Pet, Collection Log) reference item names only as raw chat-message substrings, not structured `ItemComposition` lookups, and are out of scope.

---

## New class: `OsrsWiki`

**File:** `src/main/java/space/covalent/rocketchat/OsrsWiki.java`

```java
public final class OsrsWiki
{
	private static final String BASE_URL = "https://oldschool.runescape.wiki/w/";

	private OsrsWiki() {}

	public static String itemLink(String itemName)
	{
		if ("Coins".equals(itemName))
		{
			return "**" + itemName + "**";
		}
		return "**[" + itemName + "](" + url(itemName) + ")**";
	}

	static String url(String itemName)
	{
		String slug = itemName.replace(' ', '_');
		try
		{
			return BASE_URL + URLEncoder.encode(slug, "UTF-8").replace("+", "%20");
		}
		catch (UnsupportedEncodingException e)
		{
			return BASE_URL + slug;
		}
	}
}
```

- `itemLink()` is a drop-in replacement for the existing `"**" + comp.getName() + "**"` pattern — same bold formatting, now wrapped in a markdown link. Rocket.Chat already renders markdown links (confirmed by the existing `**bold**` usage in these same messages).
- `url()` is package-private and separately testable: spaces → underscores, then `URLEncoder` percent-encodes the rest (parens, apostrophes, ampersands, etc.), with the encoder's `+`-for-space behavior neutralized since spaces were already converted to underscores beforehand.
- **Coins exception:** skipped — its wiki page is currency trivia, not useful in a drop context. Every other item is linked unconditionally, no value threshold.

---

## Changes to Existing Notifiers

### LootNotifier

```java
itemLines.add(stack.getQuantity() + "x " + OsrsWiki.itemLink(comp.getName()) + " (" + formatGp(price) + " gp)");
```

### ClueNotifier

```java
itemLines.add(stack.getQuantity() + "x " + OsrsWiki.itemLink(comp.getName()));
```

---

## Files Changed

| File | Change |
|------|--------|
| `OsrsWiki.java` | New utility class |
| `LootNotifier.java` | Item line uses `OsrsWiki.itemLink()` |
| `ClueNotifier.java` | Item line uses `OsrsWiki.itemLink()` |

---

## Testing

- `OsrsWikiTest` (new): URL encoding for names with spaces, parentheses (e.g. "Amulet of fury (or)"), apostrophes (e.g. "Zamorak's greater blessing"); Coins returns plain bold text with no link.
- `LootNotifierTest` / `ClueNotifierTest`: existing `text.contains("Abyssal whip")`-style assertions keep passing since the item name remains substring-present inside the markdown link; no new assertions strictly required but may add one confirming `[Abyssal whip](https://oldschool.runescape.wiki/w/Abyssal_whip)` shape for a representative case.
