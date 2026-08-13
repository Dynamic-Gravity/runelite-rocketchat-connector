package space.covalent.rocketchat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ItemFilterTest
{
	@Test
	public void testExactMatch()
	{
		assertTrue(ItemFilter.matches("Abyssal whip", "Abyssal whip"));
	}

	@Test
	public void testCaseInsensitive()
	{
		assertTrue(ItemFilter.matches("abyssal WHIP", "Abyssal whip"));
	}

	@Test
	public void testMultiEntryCsv()
	{
		assertTrue(ItemFilter.matches("Coins, Abyssal whip, Rune arrow", "Abyssal whip"));
	}

	@Test
	public void testTrimsWhitespaceAroundEntries()
	{
		assertTrue(ItemFilter.matches("Coins ,  Abyssal whip  , Rune arrow", "Abyssal whip"));
	}

	@Test
	public void testNoMatch()
	{
		assertFalse(ItemFilter.matches("Coins, Rune arrow", "Abyssal whip"));
	}

	@Test
	public void testNullCsvReturnsFalse()
	{
		assertFalse(ItemFilter.matches(null, "Abyssal whip"));
	}

	@Test
	public void testEmptyCsvReturnsFalse()
	{
		assertFalse(ItemFilter.matches("", "Abyssal whip"));
	}

	@Test
	public void testSubstringDoesNotMatch()
	{
		assertFalse(ItemFilter.matches("whip", "Abyssal whip"));
	}
}
