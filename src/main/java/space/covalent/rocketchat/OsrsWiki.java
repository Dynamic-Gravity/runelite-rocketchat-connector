package space.covalent.rocketchat;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public final class OsrsWiki
{
	private static final String BASE_URL = "https://oldschool.runescape.wiki/w/";

	private OsrsWiki() {}

	public static boolean isLinkable(String itemName)
	{
		return !"Coins".equals(itemName);
	}

	public static String pageUrl(String itemName)
	{
		return BASE_URL + slug(itemName);
	}

	public static String iconUrl(String itemName)
	{
		return BASE_URL + "Special:FilePath/" + slug(itemName + ".png");
	}

	private static String slug(String value)
	{
		String underscored = value.replace(' ', '_');
		try
		{
			return URLEncoder.encode(underscored, "UTF-8");
		}
		catch (UnsupportedEncodingException e)
		{
			return underscored;
		}
	}
}
