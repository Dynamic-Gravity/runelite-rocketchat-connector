package space.covalent.rocketchat;

import java.util.Locale;

public final class ItemEmoji
{
	private static final String SHORTCODE_PREFIX = "osrs_";

	private ItemEmoji() {}

	/**
	 * Must stay in sync with slugify() in tools/item-emojis/upload_item_emojis.py -
	 * a mismatch means this emits ":osrs_x:" text that doesn't resolve to any
	 * emoji the sync script uploaded.
	 */
	public static String shortcode(String itemName)
	{
		String slug = itemName.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "_")
			.replaceAll("^_+|_+$", "");
		return SHORTCODE_PREFIX + slug;
	}
}
