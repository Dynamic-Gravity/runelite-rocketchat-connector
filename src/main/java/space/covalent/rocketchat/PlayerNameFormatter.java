package space.covalent.rocketchat;

public final class PlayerNameFormatter
{
	private PlayerNameFormatter() {}

	/**
	 * Prefixes a player name with their account-type helm emoji shortcode, when emoji icons are
	 * enabled and the account type has one (NONE doesn't). Otherwise returns the name unchanged.
	 */
	public static String format(String name, IronManMode ironManMode, boolean useEmojiIcons)
	{
		if (!useEmojiIcons || ironManMode == null)
		{
			return name;
		}

		String helmItemName = ironManMode.helmItemName();
		if (helmItemName == null)
		{
			return name;
		}

		return ":" + ItemEmoji.shortcode(helmItemName) + ": " + name;
	}
}
