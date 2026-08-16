# Changelog

All notable changes to this project are documented here. Format is based on
[Keep a Changelog](https://keepachangelog.com/). Entries are generated
automatically from `feat:`/`fix:` commit subjects by the release job in
[.gitlab-ci.yml](.gitlab-ci.yml) — commits that don't follow the
`type(scope): subject` convention aren't picked up, so a few pre-convention
or malformed-prefix commits in early history are missing from the sections
below. `chore`/`docs`/`refactor`/etc. commits are intentionally excluded as
non-user-facing.

## [1.6.0] - 2026-08-15

### Added
- emoji item icons, notifier redesign, and debug test panel

### Fixed
- don't squish icons that are narrow

## [1.5.0] - 2026-08-14

### Added
- upload clue reward screenshot to Rocket.Chat

## [1.4.0] - 2026-08-13

### Added
- add commit-msg hook to catch conventional-commit typos

## [1.3.1] - 2026-08-13

### Fixed
- trigger release for LevelNotifier player-name fix (previous commit message had a formatting typo)

## [1.3.0] - 2026-08-13

_No conventionally-tagged feat/fix commits for this release._

## [1.2.0] - 2026-08-12

_No conventionally-tagged feat/fix commits for this release._

## [1.1.0] - 2026-08-12

### Added
- add RocketChatPayload model and WebhookClient
- add death notifier
- add level-up notifier
- add loot and clue scroll notifiers
- add pet, quest, slayer, boss, collection log, CA, and diary notifiers
- add custom chat pattern notifier
- add Grand Exchange notifier
- add IronManMode enum and config item
- suppress GE notifications for iron man accounts
- use high-alch value for loot pricing in iron man mode
- use high-alch value for clue scroll pricing in iron man mode
- embellish death notification for hardcore iron man mode
- add HardcoreStatusNotifier for HC status-loss alert
- add OsrsWiki page/icon URL builder
- add RarityLookupService for OSRS Wiki drop-rate queries
- LootNotifier shows one wiki-linked card for the highest-value item
- ClueNotifier shows one wiki-linked card for the highest-value reward

### Fixed
- show unicode emojis directly instead of interpolated emojis
- edge-case bugs and expand test coverage
- avoid logging webhook URL in error message
- cache negative rarity lookups and isolate per-row JSON parsing
- prevent caching of transient errors in rarity lookups
- parse comma-grouped wiki rarities and correct clue rarity source
- locale-safe rarity formatting, drop dead OsrsWiki replace, test gaps
