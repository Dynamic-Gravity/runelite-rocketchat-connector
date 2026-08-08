# Rocket.Chat Notifier

A RuneLite plugin that sends game event notifications to a self-hosted [Rocket.Chat](https://rocket.chat) instance via incoming webhook. Equivalent to [Dink](https://github.com/pajlads/DinkPlugin) but targeting Rocket.Chat instead of Discord.

## Setup

1. Create an incoming webhook in Rocket.Chat: **Administration → Integrations → New → Incoming**
2. Copy the webhook URL
3. In RuneLite, open **Plugin Hub**, install **Rocket.Chat Notifier**
4. Open the plugin config panel, paste the webhook URL
5. Enable the notification types you want (all default to off)

## Feature Matrix

| Notification | Trigger | Config options |
|---|---|---|
| Death | Local player dies | Toggle |
| Level up | Skill level increases | Toggle, minimum level (1–99) |
| Loot | Loot received from NPC/pickpocket | Toggle, minimum GE value (gp) |
| Clue scrolls | Clue scroll reward received | Toggle, minimum tier (Beginner–Master) |
| Pet drop | "funny feeling" or "weird sneaking" chat message | Toggle |
| Quest complete | Quest completion chat message | Toggle |
| Slayer task | Slayer task completion chat message | Toggle |
| Boss kills | Kill count and personal best chat messages | Toggle, PB-only mode, kill count interval |
| Collection log | New collection log entry chat message | Toggle |
| Combat achievements | CA completion chat message | Toggle, minimum tier (Easy–Grandmaster) |
| Achievement diary | Diary completion chat message | Toggle, minimum tier (Easy–Elite) |
| Grand Exchange | Offer fully bought or sold | Toggle, minimum trade value (gp) |
| Custom chat pattern | Any chat message matching a user-defined regex | Toggle, regex pattern |

**Not implemented (planned):** Player kills, P2P trades, Group Ironman bank transactions, Leagues events, Barbarian Assault gambles, quest speedruns.

## Building

Requires JDK 11+. The Gradle wrapper handles everything else.

```bash
# Compile
./gradlew compileJava

# Run tests
./gradlew test

# Launch RuneLite in developer mode with the plugin loaded
./gradlew run
```

After `./gradlew run`, follow the [Jagex Accounts login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) to authenticate in the development client.

## Testing in-game

The plugin cannot be verified without running RuneLite. After launching with `./gradlew run`:

1. Configure a webhook URL in the plugin config panel
2. Enable one or more notification types
3. Trigger the corresponding event in-game (die somewhere safe, gain a level, etc.)
4. Confirm the message appears in your Rocket.Chat channel

## License

BSD 2-Clause
