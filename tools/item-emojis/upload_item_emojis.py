#!/usr/bin/env python3
"""
Admin tool: mirror OSRS item icons into a Rocket.Chat server as custom emoji.

This is NOT part of the plugin build or the plugin's runtime. It's a one-time
(re-runnable) migration script a Rocket.Chat *admin* runs by hand, using an
admin account with the `manage-emoji` permission. The plugin itself never has
admin credentials and never talks to this endpoint.

Why this exists: OSRS item icon PNGs are trimmed tight to the sprite's
bounding box (e.g. "Black platelegs.png" is 12x29px), so Rocket.Chat's
message-attachment image fields either stretch or crop them. Rocket.Chat's
own emoji picker renders emoji contained within a fixed box instead of
stretched, so re-hosting icons as custom emoji sidesteps the distortion
without any image processing.

Usage:
    pip install requests
    python3 upload_item_emojis.py \\
        --rc-origin https://chat.example.com \\
        --admin-user <admin X-User-Id> \\
        --admin-token <admin X-Auth-Token> \\
        --dry-run          # preview first, then drop --dry-run to actually upload

Output: item-emoji-map.json, a {"Item name": "osrs_item_name", ...} map.
The plugin-side shortcode function MUST stay byte-for-byte in sync with
slugify() below - see tools/item-emojis/README.md.
"""

import argparse
import json
import re
import sys
import time
from pathlib import Path

import requests

MAPPING_URL = "https://prices.runescape.wiki/api/v1/osrs/mapping"
WIKI_ORIGIN = "https://oldschool.runescape.wiki"
USER_AGENT = "my-runelite-plugin item-emoji sync (contact: set-me-in-fork)"
SHORTCODE_PREFIX = "osrs_"


def slugify(item_name: str) -> str:
	"""Item display name -> Rocket.Chat emoji shortcode. Keep in sync with
	the plugin's Java equivalent - a mismatch means the plugin emits
	":osrs_foo:" text that doesn't resolve to any uploaded emoji."""
	slug = item_name.lower()
	slug = re.sub(r"[^a-z0-9]+", "_", slug).strip("_")
	return f"{SHORTCODE_PREFIX}{slug}"


# Items the plugin references by name that aren't GE-tradeable, so they never appear in
# MAPPING_URL's result - the account-type helms and coins (currency itself isn't a GE item).
# Keep in sync with IronManMode.java's helm names and ItemEmoji usages for "Coins".
EXTRA_ITEMS = [
	{"name": "Coins", "icon": "Coins.png"},
	{"name": "Ironman helm", "icon": "Ironman helm.png"},
	{"name": "Ultimate ironman helm", "icon": "Ultimate ironman helm.png"},
	{"name": "Hardcore ironman helm", "icon": "Hardcore ironman helm.png"},
	{"name": "Group ironman helm", "icon": "Group ironman helm.png"},
	{"name": "Hardcore group ironman helm", "icon": "Hardcore group ironman helm.png"},
]


def fetch_item_list():
	resp = requests.get(MAPPING_URL, headers={"User-Agent": USER_AGENT}, timeout=30)
	resp.raise_for_status()
	items = resp.json()  # [{"id": int, "name": str, "icon": "Item name.png", ...}, ...]
	return items + EXTRA_ITEMS


def download_icon(icon_filename: str, cache_dir: Path) -> Path:
	dest = cache_dir / icon_filename
	if dest.exists():
		return dest
	url = f"{WIKI_ORIGIN}/w/Special:FilePath/{icon_filename.replace(' ', '_')}"
	resp = requests.get(url, headers={"User-Agent": USER_AGENT}, timeout=30)
	resp.raise_for_status()
	dest.write_bytes(resp.content)
	return dest


def existing_emoji_names(rc_origin, admin_user, admin_token) -> set:
	resp = requests.get(
		f"{rc_origin}/api/v1/emoji-custom.all",
		headers={"X-Auth-Token": admin_token, "X-User-Id": admin_user},
		params={"count": 0},  # 0 = no page limit; endpoint paginates by default (~50/page)
		timeout=30,
	)
	resp.raise_for_status()
	return {e["name"] for e in resp.json()["emojis"]}


def upload_emoji(rc_origin, admin_user, admin_token, shortcode, icon_path: Path) -> bool:
	with icon_path.open("rb") as f:
		resp = requests.post(
			f"{rc_origin}/api/v1/emoji-custom.create",
			headers={"X-Auth-Token": admin_token, "X-User-Id": admin_user},
			data={"name": shortcode, "aliases": ""},
			files={"emoji": (icon_path.name, f, "image/png")},
			timeout=30,
		)
	if not resp.ok:
		print(f" - FAILED: {resp.status_code} {resp.text[:200]}", file=sys.stderr)
		return False
	return True


def main():
	parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
	parser.add_argument("--rc-origin", required=True, help="e.g. https://chat.example.com")
	parser.add_argument("--admin-user", required=True, help="X-User-Id of an admin with manage-emoji permission")
	parser.add_argument("--admin-token", required=True, help="X-Auth-Token for that admin user")
	parser.add_argument("--cache-dir", default="./icon-cache", help="local download cache")
	parser.add_argument("--delay", type=float, default=0.3, help="seconds between wiki/RC requests")
	parser.add_argument("--dry-run", action="store_true", help="print planned uploads, touch nothing")
	parser.add_argument("--out", default="item-emoji-map.json", help="name -> shortcode map written on completion")
	args = parser.parse_args()

	cache_dir = Path(args.cache_dir)
	cache_dir.mkdir(parents=True, exist_ok=True)

	print("Fetching tradeable item list...")
	items = fetch_item_list()
	print(f"{len(items)} items")

	print("Fetching existing Rocket.Chat emoji names...")
	existing = existing_emoji_names(args.rc_origin, args.admin_user, args.admin_token)

	name_to_shortcode = {}
	claimed_shortcodes = set(existing)  # pre-existing on the server, plus ones we claim below
	uploaded = duplicate = failed = 0
	total = len(items)

	for i, item in enumerate(items, 1):
		name = item["name"]
		icon = item["icon"]
		shortcode = slugify(name)
		name_to_shortcode[name] = shortcode

		print(f"[{i}/{total}] {name} -> :{shortcode}:", end="", flush=True)

		if shortcode in claimed_shortcodes:
			# Either already on the server, or another item earlier in this same run mapped to
			# the identical shortcode (the wiki's item list has distinct item ids sharing an
			# identical display name, e.g. multiple "Abyssal dagger(p)" entries) - re-attempting
			# would just fail with Custom_Emoji_Error_Name_Or_Alias_Already_In_Use.
			print(" - already claimed, skipping")
			duplicate += 1
			continue

		try:
			icon_path = download_icon(icon, cache_dir)
		except Exception as e:
			print(f" - download failed: {e}", file=sys.stderr)
			failed += 1
			continue
		time.sleep(args.delay)

		if args.dry_run:
			print(" - would upload")
			uploaded += 1
			claimed_shortcodes.add(shortcode)
			continue

		ok = upload_emoji(args.rc_origin, args.admin_user, args.admin_token, shortcode, icon_path)
		if ok:
			print(" - uploaded")
			uploaded += 1
			claimed_shortcodes.add(shortcode)
		else:
			# upload_emoji already printed the failure detail to stderr
			failed += 1
		time.sleep(args.delay)

	Path(args.out).write_text(json.dumps(name_to_shortcode, indent=2, sort_keys=True))
	print(f"done. uploaded={uploaded} duplicate/already-uploaded={duplicate} failed={failed}")
	print(f"name->shortcode map written to {args.out}")


if __name__ == "__main__":
	main()
