package space.covalent.rocketchat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OsrsWikiTest
{
	@Test
	public void testPageUrlForSimpleName()
	{
		assertEquals("https://oldschool.runescape.wiki/w/Abyssal_whip", OsrsWiki.pageUrl("Abyssal whip"));
	}

	@Test
	public void testPageUrlEncodesParentheses()
	{
		assertEquals("https://oldschool.runescape.wiki/w/Amulet_of_fury_%28or%29", OsrsWiki.pageUrl("Amulet of fury (or)"));
	}

	@Test
	public void testPageUrlEncodesApostrophe()
	{
		assertEquals("https://oldschool.runescape.wiki/w/Zamorak%27s_brew", OsrsWiki.pageUrl("Zamorak's brew"));
	}

	@Test
	public void testIconUrlAppendsPngExtension()
	{
		assertEquals("https://oldschool.runescape.wiki/w/Special:FilePath/Abyssal_whip.png", OsrsWiki.iconUrl("Abyssal whip"));
	}

	@Test
	public void testCoinsIsNotLinkable()
	{
		assertFalse(OsrsWiki.isLinkable("Coins"));
	}

	@Test
	public void testOtherItemsAreLinkable()
	{
		assertTrue(OsrsWiki.isLinkable("Abyssal whip"));
	}
}
