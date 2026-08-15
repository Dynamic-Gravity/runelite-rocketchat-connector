# Item icon emoji sync

Admin-run, one-time (re-runnable) tool. Mirrors every tradeable OSRS item
icon into a Rocket.Chat server's custom emoji list, so notification cards
can reference `:osrs_item_name:` instead of a `thumb_url`/`image_url`
attachment field.

**Not part of the plugin.** The plugin ships to arbitrary users pointing at
arbitrary Rocket.Chat servers with only a webhook URL (and optionally
room-scoped upload credentials for the clue-screenshot feature) - it never
holds admin credentials and never should. This script requires a Rocket.Chat
*admin* account with the `manage-emoji` permission, so it's something a
server admin runs by hand against their own instance, not something the
plugin can trigger.

## Why

OSRS item icon PNGs are trimmed tight to the sprite's bounding box - e.g.
`Black platelegs.png` is 12x29px, `Abyssal whip.png` is 26x29px. Rocket.Chat
message-attachment image fields either force-stretch these into a square
(`thumb_url`) or render them inline at native size, which looks fine but
breaks the compact-card layout (`image_url`, what the plugin currently
uses). Rocket.Chat's own emoji picker renders emoji *contained* within a
fixed box rather than stretched, so re-hosting icons as custom emoji fixes
the distortion with no image processing needed.

## Usage

```
pip install requests
python3 upload_item_emojis.py \
    --rc-origin https://chat.example.com \
    --admin-user <admin X-User-Id> \
    --admin-token <admin X-Auth-Token> \
    --dry-run
```

Review the dry-run output, then drop `--dry-run` to actually upload. Re-run
periodically (e.g. after OSRS updates add items) - already-uploaded emoji
are skipped.

Produces `item-emoji-map.json`: `{"Black platelegs": "osrs_black_platelegs", ...}`.

## Known limits

- Source list is `prices.runescape.wiki`'s GE mapping (~4650 tradeable
  items), plus `EXTRA_ITEMS` in the script - Coins and the five account-type
  helms (Ironman/Ultimate/Hardcore/Group/Hardcore Group), none of which are
  GE-tradeable so the mapping endpoint never returns them. Other untradeable
  quest/event items still aren't covered - acceptable for loot/clue reward
  cards, which are GE-tradeable items in practice.
- A handful of distinct item ids share an identical display name (e.g.
  multiple `Abyssal dagger(p)` entries), which collide on the same
  shortcode. The script claims the first and silently skips the rest for
  the remainder of that run - counted separately as `duplicate` in the
  summary line, not `failed`.
- One full run is ~4000 downloads + uploads at the default 0.3s delay,
  roughly 30-40 minutes. That's deliberate - be polite to the wiki and to
  the Rocket.Chat server, this only needs to run occasionally.
- `--admin-token`/`--admin-user` are admin-level Rocket.Chat credentials,
  more sensitive than the room-scoped upload token the plugin already asks
  for. Treat them accordingly (don't commit them, prefer a short-lived
  personal access token scoped to `manage-emoji` if your server supports it).
- `slugify()` in this script is the source of truth for the shortcode
  naming convention. If a plugin-side "use emoji icons" feature is ever
  built, its Java slug function must match this one exactly, or the plugin
  will emit `:osrs_foo:` text that doesn't resolve to any uploaded emoji.
