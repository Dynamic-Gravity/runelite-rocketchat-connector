package space.covalent.rocketchat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlayerNameFormatterTest
{
	@Test
	public void testNoPrefixWhenEmojiIconsDisabled()
	{
		assertEquals("Zezima", PlayerNameFormatter.format("Zezima", IronManMode.IRONMAN, false));
	}

	@Test
	public void testNoPrefixWhenAccountTypeIsNone()
	{
		assertEquals("Zezima", PlayerNameFormatter.format("Zezima", IronManMode.NONE, true));
	}

	@Test
	public void testNoPrefixWhenAccountTypeIsNull()
	{
		assertEquals("Zezima", PlayerNameFormatter.format("Zezima", null, true));
	}

	@Test
	public void testPrefixesHelmShortcodeWhenEnabled()
	{
		assertEquals(":osrs_ironman_helm: Zezima", PlayerNameFormatter.format("Zezima", IronManMode.IRONMAN, true));
	}

	@Test
	public void testUsesCorrectHelmPerAccountType()
	{
		assertEquals(":osrs_hardcore_ironman_helm: Zezima",
			PlayerNameFormatter.format("Zezima", IronManMode.HARDCORE_IRONMAN, true));
		assertEquals(":osrs_ultimate_ironman_helm: Zezima",
			PlayerNameFormatter.format("Zezima", IronManMode.ULTIMATE_IRONMAN, true));
	}
}
