# Item Whitelist/Ignorelist Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users override Loot/Clue notification item selection with a comma-separated whitelist (always wins the slot, bypasses value threshold) and ignorelist (never shown, falls back to next-best), with ignorelist taking priority when an item is on both.

**Architecture:** A pure `ItemFilter.matches(csv, itemName)` utility is shared by `LootNotifier` and `ClueNotifier`. Each notifier's existing highest-price-wins selection loop gains two checks per candidate: skip ignored items outright (before price is even computed), and let a whitelisted item always outrank a non-whitelisted one regardless of price. `LootNotifier` additionally skips its `minLootValue` gate when the winner got there via the whitelist.

**Tech Stack:** Java 11, JUnit4 + Mockito (existing test infra, no new dependencies).

## Global Constraints

- Java 11 compatible, no reflection (AGENTS.md).
- Never rename an existing config key/group — this plan only adds two new keys (AGENTS.md).
- `@ConfigSection` `position` values are UI sort order only, not config keys — renumbering them needs no migration.
- Matching is exact (not substring) and case-insensitive, comparing against `ItemComposition.getName()`.
- Conflict rule: an item on both the whitelist and ignorelist is treated purely as ignored — it is filtered out before whitelist status is ever checked for it, so it can never win the slot.

---

### Task 1: `ItemFilter` utility

**Files:**
- Create: `src/main/java/space/covalent/rocketchat/ItemFilter.java`
- Test: `src/test/java/space/covalent/rocketchat/ItemFilterTest.java`

**Interfaces:**
- Produces: `ItemFilter.matches(String csv, String itemName): boolean` — used by Task 2 and Task 3.

- [ ] **Step 1: Write the failing test**

```java
package space.covalent.rocketchat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ItemFilterTest
{
	@Test
	public void testExactMatch()
	{
		assertTrue(ItemFilter.matches("Abyssal whip", "Abyssal whip"));
	}

	@Test
	public void testCaseInsensitive()
	{
		assertTrue(ItemFilter.matches("abyssal WHIP", "Abyssal whip"));
	}

	@Test
	public void testMultiEntryCsv()
	{
		assertTrue(ItemFilter.matches("Coins, Abyssal whip, Rune arrow", "Abyssal whip"));
	}

	@Test
	public void testTrimsWhitespaceAroundEntries()
	{
		assertTrue(ItemFilter.matches("Coins ,  Abyssal whip  , Rune arrow", "Abyssal whip"));
	}

	@Test
	public void testNoMatch()
	{
		assertFalse(ItemFilter.matches("Coins, Rune arrow", "Abyssal whip"));
	}

	@Test
	public void testNullCsvReturnsFalse()
	{
		assertFalse(ItemFilter.matches(null, "Abyssal whip"));
	}

	@Test
	public void testEmptyCsvReturnsFalse()
	{
		assertFalse(ItemFilter.matches("", "Abyssal whip"));
	}

	@Test
	public void testSubstringDoesNotMatch()
	{
		assertFalse(ItemFilter.matches("whip", "Abyssal whip"));
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests space.covalent.rocketchat.ItemFilterTest`
Expected: FAIL — compile error, `ItemFilter` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package space.covalent.rocketchat;

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

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests space.covalent.rocketchat.ItemFilterTest`
Expected: PASS, 8 tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/ItemFilter.java src/test/java/space/covalent/rocketchat/ItemFilterTest.java
git commit -m "feat: add ItemFilter utility for whitelist/ignorelist matching"
```

---

### Task 2: `LootNotifier` whitelist/ignorelist + config

**Files:**
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatConnectorConfig.java`
- Modify: `src/main/java/space/covalent/rocketchat/notifiers/LootNotifier.java`
- Modify: `src/test/java/space/covalent/rocketchat/notifiers/LootNotifierTest.java`

**Interfaces:**
- Consumes: `ItemFilter.matches(String, String): boolean` (Task 1).
- Produces: `RocketChatConnectorConfig.itemWhitelist(): String` (default `""`), `RocketChatConnectorConfig.itemIgnorelist(): String` (default `""`) — also consumed by Task 3.

- [ ] **Step 1: Add the config section and items**

In `src/main/java/space/covalent/rocketchat/RocketChatConnectorConfig.java`, insert a new section immediately after the `showDropRarity()` method and before the existing `@ConfigSection(name = "Clue Scrolls", ...)` block:

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

Then renumber every `@ConfigSection`'s `position` from "Clue Scrolls" onward by +1, so the new section sits between "Loot" (position 3) and "Clue Scrolls":

| Section | Old position | New position |
|---|---|---|
| Clue Scrolls | 4 | 5 |
| Pets | 5 | 6 |
| Quests | 6 | 7 |
| Slayer | 7 | 8 |
| Boss Kills | 8 | 9 |
| Collection Log | 9 | 10 |
| Combat Achievements | 10 | 11 |
| Achievement Diaries | 11 | 12 |
| Custom Pattern | 12 | 13 |
| Grand Exchange | 13 | 14 |
| Iron Man | 14 | 15 |

- [ ] **Step 2: Write the failing tests**

Add these tests to `src/test/java/space/covalent/rocketchat/notifiers/LootNotifierTest.java`, inside the `LootNotifierTest` class (e.g. after `testRarityLookupSkippedWhenDisabled`, before the closing `}`):

```java
	@Test
	public void testWhitelistedItemWinsOverHigherValueItem()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.showDropRarity()).thenReturn(false);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.itemWhitelist()).thenReturn("Rune arrow");

		int expensiveId = 4151;
		ItemComposition expensiveComp = mock(ItemComposition.class);
		when(expensiveComp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(expensiveId)).thenReturn(expensiveComp);
		when(itemManager.getItemPrice(expensiveId)).thenReturn(2000000);

		int whitelistedId = 892;
		ItemComposition whitelistedComp = mock(ItemComposition.class);
		when(whitelistedComp.getName()).thenReturn("Rune arrow");
		when(itemManager.getItemComposition(whitelistedId)).thenReturn(whitelistedComp);
		when(itemManager.getItemPrice(whitelistedId)).thenReturn(100);

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Arrays.asList(new ItemStack(expensiveId, 1), new ItemStack(whitelistedId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("1x Rune arrow", attachment.getTitle());
	}

	@Test
	public void testWhitelistedItemBypassesMinLootValue()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(100000);
		when(config.showDropRarity()).thenReturn(false);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.itemWhitelist()).thenReturn("Rune arrow");

		int itemId = 892;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Rune arrow");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(100);

		LootReceived event = new LootReceived("Goblin", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient).send(any(), any());
	}

	@Test
	public void testIgnoredItemFallsBackToNextBest()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.showDropRarity()).thenReturn(false);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.itemIgnorelist()).thenReturn("Abyssal whip");

		int expensiveId = 4151;
		ItemComposition expensiveComp = mock(ItemComposition.class);
		when(expensiveComp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(expensiveId)).thenReturn(expensiveComp);

		int cheapId = 526;
		ItemComposition cheapComp = mock(ItemComposition.class);
		when(cheapComp.getName()).thenReturn("Bones");
		when(itemManager.getItemComposition(cheapId)).thenReturn(cheapComp);
		when(itemManager.getItemPrice(cheapId)).thenReturn(50);

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Arrays.asList(new ItemStack(expensiveId, 1), new ItemStack(cheapId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("1x Bones", attachment.getTitle());
	}

	@Test
	public void testAllItemsIgnoredSendsNothing()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.itemIgnorelist()).thenReturn("Bones");

		int itemId = 526;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Bones");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);

		LootReceived event = new LootReceived("Goblin", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testItemOnBothListsIsTreatedAsIgnored()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.showDropRarity()).thenReturn(false);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.itemWhitelist()).thenReturn("Bones");
		when(config.itemIgnorelist()).thenReturn("Bones");

		int ignoredId = 526;
		ItemComposition ignoredComp = mock(ItemComposition.class);
		when(ignoredComp.getName()).thenReturn("Bones");
		when(itemManager.getItemComposition(ignoredId)).thenReturn(ignoredComp);

		int otherId = 4151;
		ItemComposition otherComp = mock(ItemComposition.class);
		when(otherComp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(otherId)).thenReturn(otherComp);
		when(itemManager.getItemPrice(otherId)).thenReturn(2000000);

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Arrays.asList(new ItemStack(ignoredId, 1), new ItemStack(otherId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("1x Abyssal whip", attachment.getTitle());
	}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests space.covalent.rocketchat.notifiers.LootNotifierTest`
Expected: FAIL — compile errors (`config.itemWhitelist()`/`config.itemIgnorelist()` don't exist yet), and the new assertions don't match the current always-highest-price behavior.

- [ ] **Step 4: Rewrite `LootNotifier`'s selection loop**

In `src/main/java/space/covalent/rocketchat/notifiers/LootNotifier.java`, add this import alongside the existing ones:

```java
import space.covalent.rocketchat.ItemFilter;
```

Replace the entire `onLootReceived` method body from `IronManMode ironManMode = config.ironManMode();` through `sendCard(event.getName(), bestStack, bestComp, bestPrice);` with:

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
				better = whitelisted;
			}
			else
			{
				better = price > bestPrice;
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
			return;
		}

		if (!bestWhitelisted && bestPrice < config.minLootValue())
		{
			return;
		}

		sendCard(event.getName(), bestStack, bestComp, bestPrice);
```

The rest of the file (`sendCard`, `buildPayload`, `formatGp`, `formatRarityLine`) is unchanged.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests space.covalent.rocketchat.notifiers.LootNotifierTest`
Expected: PASS, 16 tests green (11 existing + 5 new).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/RocketChatConnectorConfig.java src/main/java/space/covalent/rocketchat/notifiers/LootNotifier.java src/test/java/space/covalent/rocketchat/notifiers/LootNotifierTest.java
git commit -m "feat: LootNotifier honors item whitelist/ignorelist"
```

---

### Task 3: `ClueNotifier` whitelist/ignorelist

**Files:**
- Modify: `src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java`
- Modify: `src/test/java/space/covalent/rocketchat/notifiers/ClueNotifierTest.java`

**Interfaces:**
- Consumes: `ItemFilter.matches(String, String): boolean` (Task 1), `RocketChatConnectorConfig.itemWhitelist()`/`itemIgnorelist()` (Task 2).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Write the failing tests**

Add these tests to `src/test/java/space/covalent/rocketchat/notifiers/ClueNotifierTest.java`, inside the `ClueNotifierTest` class (e.g. after `testRarityLookupNoMatchStillSendsWithoutRarityLine`, before the closing `}`):

```java
	@Test
	public void testWhitelistedItemWinsOverHigherValueItem()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.itemWhitelist()).thenReturn("Rune arrow");

		int expensiveId = 4151;
		ItemComposition expensiveComp = mock(ItemComposition.class);
		when(expensiveComp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(expensiveId)).thenReturn(expensiveComp);
		when(itemManager.getItemPrice(expensiveId)).thenReturn(2000000);

		int whitelistedId = 892;
		ItemComposition whitelistedComp = mock(ItemComposition.class);
		when(whitelistedComp.getName()).thenReturn("Rune arrow");
		when(itemManager.getItemComposition(whitelistedId)).thenReturn(whitelistedComp);
		when(itemManager.getItemPrice(whitelistedId)).thenReturn(100);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Arrays.asList(new ItemStack(expensiveId, 1), new ItemStack(whitelistedId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("1x Rune arrow", attachment.getTitle());
	}

	@Test
	public void testIgnoredItemFallsBackToNextBest()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.itemIgnorelist()).thenReturn("Abyssal whip");

		int expensiveId = 4151;
		ItemComposition expensiveComp = mock(ItemComposition.class);
		when(expensiveComp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(expensiveId)).thenReturn(expensiveComp);

		int cheapId = 526;
		ItemComposition cheapComp = mock(ItemComposition.class);
		when(cheapComp.getName()).thenReturn("Bones");
		when(itemManager.getItemComposition(cheapId)).thenReturn(cheapComp);
		when(itemManager.getItemPrice(cheapId)).thenReturn(50);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Arrays.asList(new ItemStack(expensiveId, 1), new ItemStack(cheapId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("1x Bones", attachment.getTitle());
	}

	@Test
	public void testAllItemsIgnoredSendsNothing()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.itemIgnorelist()).thenReturn("Bones");

		int itemId = 526;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Bones");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testItemOnBothListsIsTreatedAsIgnored()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.itemWhitelist()).thenReturn("Bones");
		when(config.itemIgnorelist()).thenReturn("Bones");

		int ignoredId = 526;
		ItemComposition ignoredComp = mock(ItemComposition.class);
		when(ignoredComp.getName()).thenReturn("Bones");
		when(itemManager.getItemComposition(ignoredId)).thenReturn(ignoredComp);

		int otherId = 4151;
		ItemComposition otherComp = mock(ItemComposition.class);
		when(otherComp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(otherId)).thenReturn(otherComp);
		when(itemManager.getItemPrice(otherId)).thenReturn(2000000);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Arrays.asList(new ItemStack(ignoredId, 1), new ItemStack(otherId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("1x Abyssal whip", attachment.getTitle());
	}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests space.covalent.rocketchat.notifiers.ClueNotifierTest`
Expected: FAIL — new assertions don't match the current always-highest-price behavior.

- [ ] **Step 3: Rewrite `ClueNotifier`'s selection loop**

In `src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java`, add this import alongside the existing ones:

```java
import space.covalent.rocketchat.ItemFilter;
```

Replace the entire `onLootReceived` method body from `IronManMode ironManMode = config.ironManMode();` through `sendCard(tierName, wikiSource, bestStack, bestComp, bestPrice);` with:

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
				better = whitelisted;
			}
			else
			{
				better = price > bestPrice;
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
			return;
		}

		String tierName = tier.name().charAt(0) + tier.name().substring(1).toLowerCase();
		String wikiSource = "Reward casket (" + tier.name().toLowerCase() + ")";
		sendCard(tierName, wikiSource, bestStack, bestComp, bestPrice);
```

The rest of the file (`sendCard`, `buildPayload`, `formatGp`, `formatRarityLine`) is unchanged. Note `ClueNotifier` has no `minLootValue`-style gate to bypass — clue notifications always fire once tier and selection succeed, same as before this change.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests space.covalent.rocketchat.notifiers.ClueNotifierTest`
Expected: PASS, 14 tests green (10 existing + 4 new).

- [ ] **Step 5: Run the full test suite**

Run: `./gradlew test`
Expected: PASS, all tests across the project green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java src/test/java/space/covalent/rocketchat/notifiers/ClueNotifierTest.java
git commit -m "feat: ClueNotifier honors item whitelist/ignorelist"
```

---

### Task 4: README documentation

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: nothing (documentation only).
- Produces: nothing consumed by other tasks — this is the last task.

- [ ] **Step 1: Update the Feature Matrix rows**

In `README.md`, the current `Loot` and `Clue scrolls` table rows read:

```markdown
| Loot | Loot received from NPC/pickpocket | Toggle, minimum GE value (gp) |
| Clue scrolls | Clue scroll reward received | Toggle, minimum tier (Beginner–Master) |
```

Replace both rows with (these also pick up the wiki-card/icon/rarity feature that shipped earlier and was never reflected in the README):

```markdown
| Loot | Loot received from NPC/pickpocket | Toggle, minimum GE value (gp), wiki-linked item card with icon, optional drop rarity, item whitelist/ignorelist |
| Clue scrolls | Clue scroll reward received | Toggle, minimum tier (Beginner–Master), wiki-linked item card with icon, optional drop rarity, item whitelist/ignorelist |
```

- [ ] **Step 2: Add a new "Item Filters" section**

Immediately after the `## Feature Matrix` table (after the `**Not implemented (planned):**` line, before `## Building`), add:

```markdown
## Item Filters

The Loot and Clue Scroll notifiers pick one item to show per drop — normally the highest-value item. Two config fields let you override that:

- **Item whitelist** — comma-separated item names (e.g. `Zulrah's scales, Coins`) that always win the notification slot, regardless of price, and bypass the minimum loot value.
- **Item ignorelist** — comma-separated item names that are never shown, even if they'd otherwise be the natural pick. If a drop's other items still qualify, one of those is shown instead; if nothing survives, no notification fires for that event.
- **If an item is on both lists, the ignorelist wins** — it's filtered out before whitelist status is ever checked, so it can never be shown.

Matching is exact and case-insensitive — item names must match their in-game name exactly (e.g. `Abyssal whip`, not `whip`).
```

- [ ] **Step 3: Verify the README renders sensibly**

Read the file back and confirm the new section sits between the Feature Matrix and the Building section, and that both table rows are valid Markdown table syntax (same column count as the header).

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: document item whitelist/ignorelist and update feature matrix"
```
