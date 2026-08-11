# Iron Man Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an `IronManMode` enum config item that suppresses GE notifications, reprices loot at high-alch value, and adds HCIM-specific death embellishment + HC-status-loss notification.

**Architecture:** A new `IronManMode` enum with `isIronman()` / `isHardcore()` helpers drives all behavior. Existing notifiers check these helpers; a new `HardcoreStatusNotifier` handles the HC status-loss message. All guards are null-safe so existing tests need no modification.

**Tech Stack:** Java 11, RuneLite plugin API, Mockito (tests), Lombok `@Value`/`@Builder` (payload)

## Global Constraints

- Java 11 source compatibility — no records, text blocks, or pattern matching
- No `Thread.sleep()`, no blocking on the client thread
- All HTTP via injected `OkHttpClient`, never on the client thread
- Use `net.runelite.api.gameval` constants where applicable; no magic numbers
- Do not add transitive dependencies from `runelite-client` (gson, guice, okhttp) to `build.gradle`
- No `META-INF/services/net.runelite.client.plugins.Plugin` file
- Tests run with `./gradlew test`; MockitoJUnitRunner strict mode — no unused stubs

---

## File Map

| File | Action |
|------|--------|
| `src/main/java/space/covalent/rocketchat/IronManMode.java` | Create — new enum |
| `src/test/java/space/covalent/rocketchat/IronManModeTest.java` | Create — enum unit tests |
| `src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java` | Modify — add Iron Man section + config item |
| `src/main/java/space/covalent/rocketchat/notifiers/GrandExchangeNotifier.java` | Modify — ironman guard |
| `src/test/java/space/covalent/rocketchat/notifiers/GrandExchangeNotifierTest.java` | Modify — add ironman suppression tests |
| `src/main/java/space/covalent/rocketchat/notifiers/LootNotifier.java` | Modify — high-alch price branch |
| `src/test/java/space/covalent/rocketchat/notifiers/LootNotifierTest.java` | Modify — add high-alch tests |
| `src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java` | Modify — high-alch price branch |
| `src/test/java/space/covalent/rocketchat/notifiers/ClueNotifierTest.java` | Create — new test file |
| `src/main/java/space/covalent/rocketchat/notifiers/DeathNotifier.java` | Modify — hardcore title/color branch |
| `src/test/java/space/covalent/rocketchat/notifiers/DeathNotifierTest.java` | Modify — add hardcore styling tests |
| `src/main/java/space/covalent/rocketchat/notifiers/HardcoreStatusNotifier.java` | Create — new notifier |
| `src/test/java/space/covalent/rocketchat/notifiers/HardcoreStatusNotifierTest.java` | Create — new test file |
| `src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java` | Modify — register/unregister new notifier |

---

### Task 1: `IronManMode` enum + config item

**Files:**
- Create: `src/main/java/space/covalent/rocketchat/IronManMode.java`
- Create: `src/test/java/space/covalent/rocketchat/IronManModeTest.java`
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java`

**Interfaces:**
- Produces: `IronManMode` enum with `isIronman()` and `isHardcore()` — all later tasks use these
- Produces: `config.ironManMode()` returning `IronManMode` — all later tasks call this

- [ ] **Step 1: Write the failing enum test**

Create `src/test/java/space/covalent/rocketchat/IronManModeTest.java`:

```java
package space.covalent.rocketchat;

import org.junit.Test;
import static org.junit.Assert.*;

public class IronManModeTest
{
    @Test
    public void testNoneIsNotIronman()
    {
        assertFalse(IronManMode.NONE.isIronman());
    }

    @Test
    public void testAllVariantsAreIronman()
    {
        assertTrue(IronManMode.IRONMAN.isIronman());
        assertTrue(IronManMode.ULTIMATE_IRONMAN.isIronman());
        assertTrue(IronManMode.HARDCORE_IRONMAN.isIronman());
        assertTrue(IronManMode.GROUP_IRONMAN.isIronman());
        assertTrue(IronManMode.HARDCORE_GROUP_IRONMAN.isIronman());
    }

    @Test
    public void testOnlyHardcoreVariantsAreHardcore()
    {
        assertFalse(IronManMode.NONE.isHardcore());
        assertFalse(IronManMode.IRONMAN.isHardcore());
        assertFalse(IronManMode.ULTIMATE_IRONMAN.isHardcore());
        assertTrue(IronManMode.HARDCORE_IRONMAN.isHardcore());
        assertFalse(IronManMode.GROUP_IRONMAN.isHardcore());
        assertTrue(IronManMode.HARDCORE_GROUP_IRONMAN.isHardcore());
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```bash
./gradlew test 2>&1 | grep -E "error|FAILED|BUILD"
```

Expected: compile error — `IronManMode` does not exist.

- [ ] **Step 3: Create the enum**

Create `src/main/java/space/covalent/rocketchat/IronManMode.java`:

```java
package space.covalent.rocketchat;

public enum IronManMode
{
    NONE,
    IRONMAN,
    ULTIMATE_IRONMAN,
    HARDCORE_IRONMAN,
    GROUP_IRONMAN,
    HARDCORE_GROUP_IRONMAN;

    public boolean isIronman()
    {
        return this != NONE;
    }

    public boolean isHardcore()
    {
        return this == HARDCORE_IRONMAN || this == HARDCORE_GROUP_IRONMAN;
    }
}
```

- [ ] **Step 4: Add config section and item to `RocketChatNotifierConfig`**

Open `src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java`.

Append after the last existing `@ConfigItem` (the `minGrandExchangeValue` block):

```java
    // Iron Man
    @ConfigSection(name = "Iron Man", description = "Iron Man account type settings", position = 14)
    String ironManSection = "ironman";

    @ConfigItem(keyName = "ironManMode", name = "Account type",
        description = "Enables iron man behaviours appropriate for your account type",
        section = ironManSection)
    default IronManMode ironManMode() { return IronManMode.NONE; }
```

`IronManMode` is in the same package — no import needed.

- [ ] **Step 5: Run tests — expect all pass**

```bash
./gradlew test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/IronManMode.java \
        src/test/java/space/covalent/rocketchat/IronManModeTest.java \
        src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java
git commit -m "feat: add IronManMode enum and config item"
```

---

### Task 2: GrandExchangeNotifier — suppress when ironman

**Files:**
- Modify: `src/main/java/space/covalent/rocketchat/notifiers/GrandExchangeNotifier.java`
- Modify: `src/test/java/space/covalent/rocketchat/notifiers/GrandExchangeNotifierTest.java`

**Interfaces:**
- Consumes: `IronManMode` enum and `config.ironManMode()` from Task 1

- [ ] **Step 1: Write failing tests**

Add these three tests to `GrandExchangeNotifierTest.java` (append before the closing `}`):

```java
    @Test
    public void testSuppressedWhenIronman()
    {
        when(config.notifyOnGrandExchange()).thenReturn(true);
        when(config.ironManMode()).thenReturn(IronManMode.IRONMAN);

        GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
        when(offer.getState()).thenReturn(GrandExchangeOfferState.BOUGHT);

        GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
        event.setOffer(offer);
        notifier.onGrandExchangeOfferChanged(event);

        verify(webhookClient, never()).send(any(), any());
    }

    @Test
    public void testSuppressedWhenHardcoreIronman()
    {
        when(config.notifyOnGrandExchange()).thenReturn(true);
        when(config.ironManMode()).thenReturn(IronManMode.HARDCORE_IRONMAN);

        GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
        when(offer.getState()).thenReturn(GrandExchangeOfferState.BOUGHT);

        GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
        event.setOffer(offer);
        notifier.onGrandExchangeOfferChanged(event);

        verify(webhookClient, never()).send(any(), any());
    }

    @Test
    public void testNotSuppressedWhenNone()
    {
        when(config.notifyOnGrandExchange()).thenReturn(true);
        when(config.ironManMode()).thenReturn(IronManMode.NONE);
        when(config.minGrandExchangeValue()).thenReturn(0);
        when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

        ItemComposition comp = mock(ItemComposition.class);
        when(comp.getName()).thenReturn("Coal");
        when(itemManager.getItemComposition(453)).thenReturn(comp);

        GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
        when(offer.getState()).thenReturn(GrandExchangeOfferState.BOUGHT);
        when(offer.getItemId()).thenReturn(453);
        when(offer.getTotalQuantity()).thenReturn(100);
        when(offer.getPrice()).thenReturn(200);

        GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
        event.setOffer(offer);
        notifier.onGrandExchangeOfferChanged(event);

        verify(webhookClient).send(any(), any());
    }
```

Add `import space.covalent.rocketchat.IronManMode;` to the imports in the test file.

- [ ] **Step 2: Run tests — expect the three new tests to fail**

```bash
./gradlew test 2>&1 | grep -E "FAILED|BUILD"
```

Expected: `testSuppressedWhenIronman` and `testSuppressedWhenHardcoreIronman` FAIL (webhook is called when it shouldn't be).

- [ ] **Step 3: Add guard to GrandExchangeNotifier**

Open `src/main/java/space/covalent/rocketchat/notifiers/GrandExchangeNotifier.java`.

Add `import space.covalent.rocketchat.IronManMode;` to the imports.

In `onGrandExchangeOfferChanged`, add the guard **after** the `notifyOnGrandExchange()` check and **before** the offer state check:

```java
    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
    {
        if (!config.notifyOnGrandExchange())
        {
            return;
        }

        IronManMode ironManMode = config.ironManMode();
        if (ironManMode != null && ironManMode.isIronman())
        {
            return;
        }

        GrandExchangeOffer offer = event.getOffer();
        // ... rest of method unchanged
```

The null check ensures existing tests that don't stub `ironManMode()` (which returns null from Mockito) continue to pass without modification.

- [ ] **Step 4: Run tests — expect all pass**

```bash
./gradlew test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/notifiers/GrandExchangeNotifier.java \
        src/test/java/space/covalent/rocketchat/notifiers/GrandExchangeNotifierTest.java
git commit -m "feat(ironman): suppress GE notifications for iron man accounts"
```

---

### Task 3: LootNotifier — high-alch pricing

**Files:**
- Modify: `src/main/java/space/covalent/rocketchat/notifiers/LootNotifier.java`
- Modify: `src/test/java/space/covalent/rocketchat/notifiers/LootNotifierTest.java`

**Interfaces:**
- Consumes: `IronManMode.isIronman()` from Task 1
- Consumes: `ItemComposition.getHaPrice()` from RuneLite API — returns the high-alch GP value (0 for un-alchable items)

- [ ] **Step 1: Write failing tests**

Add to `LootNotifierTest.java` (before closing `}`). Also add `import space.covalent.rocketchat.IronManMode;` to imports.

```java
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
```

- [ ] **Step 2: Run tests — expect new tests to fail**

```bash
./gradlew test 2>&1 | grep -E "FAILED|BUILD"
```

Expected: `testUsesHighAlchValueInIronmanMode` fails — `getItemPrice` is called when it should not be.

- [ ] **Step 3: Refactor LootNotifier item loop**

Open `src/main/java/space/covalent/rocketchat/notifiers/LootNotifier.java`.

Add `import space.covalent.rocketchat.IronManMode;` to imports.

Replace the existing for-loop (which calls `getItemPrice` then `getItemComposition` separately) with:

```java
        IronManMode ironManMode = config.ironManMode();
        for (ItemStack stack : items)
        {
            ItemComposition comp = itemManager.getItemComposition(stack.getId());
            long price = (ironManMode != null && ironManMode.isIronman())
                ? (long) comp.getHaPrice() * stack.getQuantity()
                : (long) itemManager.getItemPrice(stack.getId()) * stack.getQuantity();
            totalValue += price;
            itemLines.add(stack.getQuantity() + "x **" + comp.getName() + "** (" + formatGp(price) + " gp)");
        }
```

`getItemComposition` is now called once per stack (used for both price in ironman mode and name). The existing non-ironman path still calls `getItemPrice` as before.

- [ ] **Step 4: Run tests — expect all pass**

```bash
./gradlew test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/notifiers/LootNotifier.java \
        src/test/java/space/covalent/rocketchat/notifiers/LootNotifierTest.java
git commit -m "feat(ironman): use high-alch value for loot pricing in iron man mode"
```

---

### Task 4: ClueNotifier — high-alch pricing

**Files:**
- Modify: `src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java`
- Create: `src/test/java/space/covalent/rocketchat/notifiers/ClueNotifierTest.java`

**Interfaces:**
- Consumes: `IronManMode.isIronman()` from Task 1
- Consumes: `ClueTier.fromLootSource()` — existing static method mapping loot source name to tier

- [ ] **Step 1: Write failing tests**

Create `src/test/java/space/covalent/rocketchat/notifiers/ClueNotifierTest.java`:

```java
package space.covalent.rocketchat.notifiers;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import space.covalent.rocketchat.ClueTier;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.WebhookClient;

import java.util.Collections;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ClueNotifierTest
{
    @Mock RocketChatNotifierConfig config;
    @Mock WebhookClient webhookClient;
    @Mock ItemManager itemManager;

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
}
```

- [ ] **Step 2: Run tests — expect new tests to fail**

```bash
./gradlew test 2>&1 | grep -E "FAILED|BUILD"
```

Expected: `testUsesHighAlchValueInIronmanMode` fails.

- [ ] **Step 3: Refactor ClueNotifier item loop**

Open `src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java`.

Add `import space.covalent.rocketchat.IronManMode;` to imports.

Replace the existing for-loop with:

```java
        IronManMode ironManMode = config.ironManMode();
        for (ItemStack stack : items)
        {
            ItemComposition comp = itemManager.getItemComposition(stack.getId());
            long price = (ironManMode != null && ironManMode.isIronman())
                ? (long) comp.getHaPrice() * stack.getQuantity()
                : (long) itemManager.getItemPrice(stack.getId()) * stack.getQuantity();
            totalValue += price;
            itemLines.add(stack.getQuantity() + "x **" + comp.getName() + "**");
        }
```

- [ ] **Step 4: Run tests — expect all pass**

```bash
./gradlew test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java \
        src/test/java/space/covalent/rocketchat/notifiers/ClueNotifierTest.java
git commit -m "feat(ironman): use high-alch value for clue scroll pricing in iron man mode"
```

---

### Task 5: DeathNotifier — hardcore embellishment

**Files:**
- Modify: `src/main/java/space/covalent/rocketchat/notifiers/DeathNotifier.java`
- Modify: `src/test/java/space/covalent/rocketchat/notifiers/DeathNotifierTest.java`

**Interfaces:**
- Consumes: `IronManMode.isHardcore()` from Task 1

- [ ] **Step 1: Write failing tests**

Add to `DeathNotifierTest.java` (before closing `}`).

Add these imports to the test file:
```java
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import space.covalent.rocketchat.IronManMode;
```

```java
    @Test
    public void testHardcoreDeathHasAlarmingTitleAndColor()
    {
        when(config.notifyOnDeath()).thenReturn(true);
        when(config.ironManMode()).thenReturn(IronManMode.HARDCORE_IRONMAN);
        when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
        Player localPlayer = mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(localPlayer);
        when(localPlayer.getName()).thenReturn("Zezima");
        when(localPlayer.getCombatLevel()).thenReturn(126);

        ActorDeath event = new ActorDeath(localPlayer);
        notifier.onActorDeath(event);

        ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
        verify(webhookClient).send(any(), captor.capture());
        RocketChatPayload.Attachment att = captor.getValue().getAttachments().get(0);
        assertTrue(att.getTitle().contains("HARDCORE DEATH"));
        assertEquals("#7B0000", att.getColor());
    }

    @Test
    public void testNonHardcoreDeathHasNormalTitleAndColor()
    {
        when(config.notifyOnDeath()).thenReturn(true);
        when(config.ironManMode()).thenReturn(IronManMode.IRONMAN);
        when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
        Player localPlayer = mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(localPlayer);
        when(localPlayer.getName()).thenReturn("Zezima");
        when(localPlayer.getCombatLevel()).thenReturn(126);

        ActorDeath event = new ActorDeath(localPlayer);
        notifier.onActorDeath(event);

        ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
        verify(webhookClient).send(any(), captor.capture());
        RocketChatPayload.Attachment att = captor.getValue().getAttachments().get(0);
        assertFalse(att.getTitle().contains("HARDCORE"));
        assertEquals("#FF0000", att.getColor());
    }
```

- [ ] **Step 2: Run tests — expect new tests to fail**

```bash
./gradlew test 2>&1 | grep -E "FAILED|BUILD"
```

Expected: both new tests fail — wrong title/color.

- [ ] **Step 3: Add hardcore branch to DeathNotifier**

Open `src/main/java/space/covalent/rocketchat/notifiers/DeathNotifier.java`.

Add `import space.covalent.rocketchat.IronManMode;` to imports.

In `onActorDeath`, replace the hardcoded title and color in the payload with:

```java
        IronManMode ironManMode = config.ironManMode();
        boolean hardcore = ironManMode != null && ironManMode.isHardcore();
        String title = hardcore ? "☠️ HARDCORE DEATH: " + name : "💀 " + name + " has died";
        String color = hardcore ? "#7B0000" : "#FF0000";

        RocketChatPayload payload = RocketChatPayload.builder()
            .attachments(Collections.singletonList(
                RocketChatPayload.Attachment.builder()
                    .title(title)
                    .text("**Player:** " + name + "\n**Combat level:** " + combatLevel + "\n**Location:** " + locationStr)
                    .color(color)
                    .build()
            ))
            .build();
```

- [ ] **Step 4: Run tests — expect all pass**

```bash
./gradlew test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/notifiers/DeathNotifier.java \
        src/test/java/space/covalent/rocketchat/notifiers/DeathNotifierTest.java
git commit -m "feat(ironman): alarming death notification for hardcore iron man accounts"
```

---

### Task 6: HardcoreStatusNotifier + plugin registration

**Files:**
- Create: `src/main/java/space/covalent/rocketchat/notifiers/HardcoreStatusNotifier.java`
- Create: `src/test/java/space/covalent/rocketchat/notifiers/HardcoreStatusNotifierTest.java`
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java`

**Interfaces:**
- Consumes: `IronManMode.isHardcore()` from Task 1
- Consumes: `Client.getLocalPlayer().getName()` — may be null; guard with null check

- [ ] **Step 1: Write failing tests**

Create `src/test/java/space/covalent/rocketchat/notifiers/HardcoreStatusNotifierTest.java`:

```java
package space.covalent.rocketchat.notifiers;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.WebhookClient;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class HardcoreStatusNotifierTest
{
    @Mock Client client;
    @Mock RocketChatNotifierConfig config;
    @Mock WebhookClient webhookClient;

    @InjectMocks HardcoreStatusNotifier notifier;

    private static ChatMessage gameMessage(String text)
    {
        ChatMessage msg = new ChatMessage();
        msg.setType(ChatMessageType.GAMEMESSAGE);
        msg.setMessage(text);
        return msg;
    }

    @Test
    public void testFiresOnHcimStatusLost()
    {
        when(config.ironManMode()).thenReturn(IronManMode.HARDCORE_IRONMAN);
        when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Zezima");
        when(client.getLocalPlayer()).thenReturn(player);

        notifier.onChatMessage(gameMessage("You have lost your Hardcore Ironman status."));

        verify(webhookClient).send(any(), any());
    }

    @Test
    public void testFiresOnHcgimStatusLost()
    {
        when(config.ironManMode()).thenReturn(IronManMode.HARDCORE_GROUP_IRONMAN);
        when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Zezima");
        when(client.getLocalPlayer()).thenReturn(player);

        notifier.onChatMessage(gameMessage("You have lost your Hardcore Group Ironman status."));

        verify(webhookClient).send(any(), any());
    }

    @Test
    public void testSkipsWhenNotHardcore()
    {
        when(config.ironManMode()).thenReturn(IronManMode.IRONMAN);

        notifier.onChatMessage(gameMessage("You have lost your Hardcore Ironman status."));

        verify(webhookClient, never()).send(any(), any());
    }

    @Test
    public void testSkipsNonMatchingMessage()
    {
        when(config.ironManMode()).thenReturn(IronManMode.HARDCORE_IRONMAN);

        notifier.onChatMessage(gameMessage("You have completed a hard task."));

        verify(webhookClient, never()).send(any(), any());
    }

    @Test
    public void testSkipsNonGameMessageType()
    {
        when(config.ironManMode()).thenReturn(IronManMode.HARDCORE_IRONMAN);

        ChatMessage msg = new ChatMessage();
        msg.setType(ChatMessageType.PUBLICCHAT);
        msg.setMessage("You have lost your Hardcore Ironman status.");
        notifier.onChatMessage(msg);

        verify(webhookClient, never()).send(any(), any());
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
./gradlew test 2>&1 | grep -E "error|FAILED|BUILD"
```

Expected: compile error — `HardcoreStatusNotifier` does not exist.

- [ ] **Step 3: Create HardcoreStatusNotifier**

Create `src/main/java/space/covalent/rocketchat/notifiers/HardcoreStatusNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class HardcoreStatusNotifier
{
    private static final Pattern HC_LOST = Pattern.compile(
        "You have lost your Hardcore (?:Group )?Ironman status\\.");

    @Inject Client client;
    @Inject RocketChatNotifierConfig config;
    @Inject WebhookClient webhookClient;

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        IronManMode ironManMode = config.ironManMode();
        if (ironManMode == null || !ironManMode.isHardcore()) return;
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String msg = Text.removeTags(event.getMessage());
        Matcher m = HC_LOST.matcher(msg);
        if (!m.find()) return;

        String name = client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null
            ? client.getLocalPlayer().getName()
            : "Unknown";

        webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
            .attachments(Collections.singletonList(
                RocketChatPayload.Attachment.builder()
                    .title("☠️ Hardcore status lost: " + name)
                    .text(msg)
                    .color("#000000")
                    .build()
            ))
            .build());
    }
}
```

- [ ] **Step 4: Run tests — expect HardcoreStatusNotifier tests pass, all others still pass**

```bash
./gradlew test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Register in plugin**

Open `src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java`.

Add import:
```java
import space.covalent.rocketchat.notifiers.HardcoreStatusNotifier;
```

Add field (alongside the other `@Inject` notifier fields):
```java
    @Inject
    private HardcoreStatusNotifier hardcoreStatusNotifier;
```

In `startUp()`, add:
```java
        eventBus.register(hardcoreStatusNotifier);
```

In `shutDown()`, add:
```java
        eventBus.unregister(hardcoreStatusNotifier);
```

- [ ] **Step 6: Run tests — expect all pass**

```bash
./gradlew test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/notifiers/HardcoreStatusNotifier.java \
        src/test/java/space/covalent/rocketchat/notifiers/HardcoreStatusNotifierTest.java \
        src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java
git commit -m "feat(ironman): add HardcoreStatusNotifier for HC status-loss alert"
```
