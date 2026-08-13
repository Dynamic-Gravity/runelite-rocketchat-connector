# Item Whitelist/Ignorelist for Loot & Clue Notifications — Design Spec

**Date:** 2026-08-12
**Status:** Approved, pending implementation

---

## Overview

`LootNotifier` and `ClueNotifier` currently pick a single item to show per drop event: the highest-value stack, gated by `minLootValue` for loot (clue has no value gate). This adds two user-editable lists that let the player override that automatic selection on a per-item-name basis:

- **Whitelist** — an item on this list always wins the notification slot over any non-whitelisted item, regardless of price, and bypasses `minLootValue` entirely (loot only — clue has no threshold to bypass).
- **Ignorelist** — an item on this list is never shown, even if it would otherwise be the natural pick. Selection falls back to the next-best non-ignored item in the same drop; if nothing survives, no notification fires for that event.
- **Conflict rule** — if the same item name is on both lists, the ignorelist wins. This falls out naturally from the implementation: an ignored item is filtered out before whitelist status is ever checked for it, so it can never win the slot.

---

## Scope

`LootNotifier` and `ClueNotifier` only — the two notifiers with item-based selection logic. Other notifiers (Pet, Collection Log, etc.) are plain on/off toggles with no selection to override.

---

## Config

New `@ConfigSection` **"Item Filters"**, inserted between "Loot" and "Clue Scrolls". `@ConfigSection` position is UI sort order only, not a config key — renumbering the sections after it (Clue Scrolls onward, each +1) is cosmetic and needs no migration.

```java
@ConfigSection(
	name = "Item Filters",
	description = "Override which item wins the Loot/Clue notification slot, regardless of value",
	position = 4
)
String itemFilterSection = "itemfilter";

@ConfigItem(
	keyName = "itemWhitelist",
	name = "Item whitelist",
	description = "Comma-separated item names that always win the notification slot and bypass the minimum loot value",
	section = itemFilterSection
)
default String itemWhitelist()
{
	return "";
}

@ConfigItem(
	keyName = "itemIgnorelist",
	name = "Item ignorelist",
	description = "Comma-separated item names that are never shown, even if they would otherwise be picked. Takes priority over the whitelist for the same item",
	section = itemFilterSection
)
default String itemIgnorelist()
{
	return "";
}
```

All existing sections from "Clue Scrolls" (currently position 4) through "Iron Man" (currently position 14) shift their `position` value by +1.

---

## New utility: `ItemFilter`

**File:** `src/main/java/space/covalent/rocketchat/ItemFilter.java`

```java
public final class ItemFilter
{
	private ItemFilter() {}

	public static boolean matches(String csv, String itemName)
	{
		if (csv == null || csv.isEmpty())
		{
			return false;
		}

		for (String entry : csv.split(","))
		{
			String trimmed = entry.trim();
			if (!trimmed.isEmpty() && trimmed.equalsIgnoreCase(itemName))
			{
				return true;
			}
		}

		return false;
	}
}
```

Matching is exact (not substring) and case-insensitive, comparing against the item's canonical `ItemComposition.getName()` — same precision level as the existing `"Coins"` comparison in `OsrsWiki.isLinkable()`.

---

## Selection loop changes

### `LootNotifier`

Replace the existing highest-price-wins loop with one that tracks whitelist status alongside price:

```java
IronManMode ironManMode = config.ironManMode();
String whitelist = config.itemWhitelist();
String ignorelist = config.itemIgnorelist();

ItemStack bestStack = null;
ItemComposition bestComp = null;
long bestPrice = -1;
boolean bestWhitelisted = false;

for (ItemStack stack : items)
{
	ItemComposition comp = itemManager.getItemComposition(stack.getId());
	String itemName = comp.getName();

	if (ItemFilter.matches(ignorelist, itemName))
	{
		continue;
	}

	long price = (ironManMode != null && ironManMode.isIronman())
		? (long) comp.getHaPrice() * stack.getQuantity()
		: (long) itemManager.getItemPrice(stack.getId()) * stack.getQuantity();

	boolean whitelisted = ItemFilter.matches(whitelist, itemName);

	boolean better;
	if (whitelisted != bestWhitelisted)
	{
		better = whitelisted; // a whitelisted candidate always beats a non-whitelisted one
	}
	else
	{
		better = price > bestPrice; // same whitelist tier: highest price wins, as before
	}

	if (bestStack == null || better)
	{
		bestPrice = price;
		bestStack = stack;
		bestComp = comp;
		bestWhitelisted = whitelisted;
	}
}

if (bestStack == null)
{
	return; // every dropped item was ignored
}

if (!bestWhitelisted && bestPrice < config.minLootValue())
{
	return;
}

sendCard(event.getName(), bestStack, bestComp, bestPrice);
```

### `ClueNotifier`

Same loop (including the `bestStack == null` early return), but with no `minLootValue`-style gate after selection — matches its existing "always send on tier completion" behavior. Whitelist still overrides selection; ignorelist still filters and falls back.

---

## Files Changed

| File | Change |
|------|--------|
| `ItemFilter.java` | New utility |
| `RocketChatConnectorConfig.java` | New "Item Filters" section + 2 config items; existing section positions shift +1 from Clue Scrolls onward |
| `LootNotifier.java` | Selection loop rewritten to honor whitelist/ignorelist |
| `ClueNotifier.java` | Same rewrite, no value-gate bypass needed (none exists) |
| `README.md` | Feature matrix rows for Loot/Clue updated; new section documenting whitelist/ignorelist behavior including the ignorelist-beats-whitelist precedence rule |

---

## README updates

**Feature Matrix** — the `Loot` and `Clue scrolls` rows currently only describe the pre-wiki-card behavior (`Toggle, minimum GE value (gp)` / `Toggle, minimum tier (Beginner–Master)`) and never picked up the wiki-link/icon/rarity feature that shipped earlier. Since this task touches the same rows, bring them fully up to date rather than only appending the new whitelist/ignorelist mention. Replace both rows entirely:

```markdown
| Loot | Loot received from NPC/pickpocket | Toggle, minimum GE value (gp), wiki-linked item card with icon, optional drop rarity, item whitelist/ignorelist |
| Clue scrolls | Clue scroll reward received | Toggle, minimum tier (Beginner–Master), wiki-linked item card with icon, optional drop rarity, item whitelist/ignorelist |
```

**New section**, after Feature Matrix:

```markdown
## Item Filters

The Loot and Clue Scroll notifiers pick one item to show per drop — normally the highest-value item. Two config fields let you override that:

- **Item whitelist** — comma-separated item names (e.g. `Zulrah's scales, Coins`) that always win the notification slot, regardless of price, and bypass the minimum loot value.
- **Item ignorelist** — comma-separated item names that are never shown, even if they'd otherwise be the natural pick. If a drop's other items still qualify, one of those is shown instead; if nothing survives, no notification fires for that event.
- **If an item is on both lists, the ignorelist wins** — it's filtered out before whitelist status is ever checked, so it can never be shown.

Matching is exact and case-insensitive — item names must match their in-game name exactly (e.g. `Abyssal whip`, not `whip`).
```

---

## Testing

- `ItemFilterTest` (new): exact match, case-insensitivity, empty/null CSV returns false, whitespace around entries is trimmed, multi-entry CSV.
- `LootNotifierTest`: whitelisted lower-value item wins over a higher-value non-whitelisted item; whitelisted item bypasses `minLootValue`; ignored item is skipped and the next-best non-ignored item is shown instead; all items ignored → no notification; item on both lists is treated as ignored (never selected).
- `ClueNotifierTest`: same whitelist/ignorelist cases adapted to clue's no-threshold behavior (whitelist still wins selection; no bypass-of-threshold case since none exists).
