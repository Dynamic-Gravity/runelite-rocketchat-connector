package space.covalent.rocketchat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ItemEmojiTest
{
	@Test
	public void testShortcodeLowercasesAndUnderscoresSpaces()
	{
		assertEquals("osrs_black_platelegs", ItemEmoji.shortcode("Black platelegs"));
	}

	@Test
	public void testShortcodeCollapsesPunctuationRuns()
	{
		assertEquals("osrs_amulet_of_glory_4", ItemEmoji.shortcode("Amulet of glory(4)"));
	}

	@Test
	public void testShortcodeHandlesApostrophe()
	{
		assertEquals("osrs_zamorak_s_brew", ItemEmoji.shortcode("Zamorak's brew"));
	}
}
