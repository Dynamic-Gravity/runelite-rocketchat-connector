# Iron Man Mode — Design Spec

**Date:** 2026-08-10
**Status:** Approved, pending implementation

---

## Overview

Add Iron Man mode to the Rocket.Chat Notifier plugin. A single enum config item selects the player's account type. Depending on the type, the plugin suppresses irrelevant notifications, reprices loot in high-alch gold instead of GE price, and adds HCIM-specific urgency to death events.

---

## Config

New section "Iron Man" at position 14 (after Grand Exchange).

### New enum: `IronManMode`

```
NONE                    — default, no behaviour change
IRONMAN
ULTIMATE_IRONMAN
HARDCORE_IRONMAN
GROUP_IRONMAN
HARDCORE_GROUP_IRONMAN
```

Two helper methods on the enum:

- `isIronman()` — true for all non-NONE values
- `isHardcore()` — true for HARDCORE_IRONMAN and HARDCORE_GROUP_IRONMAN

### New config item

```java
@ConfigItem(
    keyName = "ironManMode",
    name = "Account type",
    description = "Enables iron man behaviours appropriate for your account type",
    section = ironManSection
)
default IronManMode ironManMode() { return IronManMode.NONE; }
```

No `warning` field — no third-party server involved.

---

## Behaviour Matrix

| Account type           | Suppress GE | High-alch values | Embellished death | HC loss alert |
|------------------------|-------------|------------------|-------------------|---------------|
| NONE                   |             |                  |                   |               |
| IRONMAN                | ✓           | ✓                |                   |               |
| ULTIMATE_IRONMAN       | ✓           | ✓                |                   |               |
| HARDCORE_IRONMAN       | ✓           | ✓                | ✓                 | ✓             |
| GROUP_IRONMAN          | ✓           | ✓                |                   |               |
| HARDCORE_GROUP_IRONMAN | ✓           | ✓                | ✓                 | ✓             |

---

## Changes to Existing Notifiers

### GrandExchangeNotifier

Early return at top of `onGrandExchangeOfferChanged`:

```java
if (config.ironManMode().isIronman()) return;
```

### LootNotifier + ClueNotifier

Replace price lookup per item stack:

```java
long price = config.ironManMode().isIronman()
    ? (long) itemManager.getItemComposition(stack.getId()).getHaPrice() * stack.getQuantity()
    : (long) itemManager.getItemPrice(stack.getId()) * stack.getQuantity();
```

`ItemComposition.getHaPrice()` returns 0 for un-alchable items — these appear in the notification body with "0 gp" but are still listed by name. The `minLootValue` threshold applies against the high-alch total; users running iron man mode may want to lower it accordingly.

### DeathNotifier

When `isHardcore()`, swap to alarming title and colour:

```java
boolean hardcore = config.ironManMode().isHardcore();
String title = hardcore
    ? "☠️ HARDCORE DEATH: " + name
    : "💀 " + name + " has died";
String color = hardcore ? "#7B0000" : "#FF0000";
```

---

## New Notifier: `HardcoreStatusNotifier`

**File:** `src/main/java/space/covalent/rocketchat/notifiers/HardcoreStatusNotifier.java`

Listens for the in-game message fired when HC status is lost. Pattern covers both HCIM and HCGIM variants:

```java
private static final Pattern HC_LOST = Pattern.compile(
    "You have lost your Hardcore (?:Group )?Ironman status\\.");
```

Guards:
- `config.ironManMode().isHardcore()`
- `event.getType() == ChatMessageType.GAMEMESSAGE`

Notification:
- Title: `"☠️ Hardcore status lost: " + playerName`
- Text: raw game message
- Colour: `"#000000"`

Injects: `Client`, `RocketChatNotifierConfig`, `WebhookClient`.

Registered and unregistered in `RocketChatNotifierPlugin.startUp()` / `shutDown()` alongside all existing notifiers.

---

## Files Changed

| File | Change |
|------|--------|
| `IronManMode.java` | New enum |
| `RocketChatNotifierConfig.java` | New section + config item |
| `GrandExchangeNotifier.java` | Guard on `isIronman()` |
| `LootNotifier.java` | High-alch price branch |
| `ClueNotifier.java` | High-alch price branch |
| `DeathNotifier.java` | Hardcore title/colour branch |
| `HardcoreStatusNotifier.java` | New notifier |
| `RocketChatNotifierPlugin.java` | Register/unregister new notifier |

---

## Testing

- `GrandExchangeNotifierTest`: verify suppression for all `isIronman()` types, fires for NONE
- `LootNotifierTest`: verify high-alch value used for IRONMAN, GE price for NONE, un-alchable item shows 0
- `ClueNotifierTest`: same as loot
- `DeathNotifierTest`: verify title/colour for HARDCORE vs non-hardcore
- `HardcoreStatusNotifierTest`: HCIM fires on pattern match, non-hardcore does not, GAMEMESSAGE filter
