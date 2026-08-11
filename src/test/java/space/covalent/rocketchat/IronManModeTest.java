package space.covalent.rocketchat;

import org.junit.Test;
import static org.junit.Assert.*;

public class IronManModeTest
{
    @Test
    public void testNoneIsNotIronman()
    {
        assertFalse(IronManMode.NONE.isIronman());
    }

    @Test
    public void testAllVariantsAreIronman()
    {
        assertTrue(IronManMode.IRONMAN.isIronman());
        assertTrue(IronManMode.ULTIMATE_IRONMAN.isIronman());
        assertTrue(IronManMode.HARDCORE_IRONMAN.isIronman());
        assertTrue(IronManMode.GROUP_IRONMAN.isIronman());
        assertTrue(IronManMode.HARDCORE_GROUP_IRONMAN.isIronman());
    }

    @Test
    public void testOnlyHardcoreVariantsAreHardcore()
    {
        assertFalse(IronManMode.NONE.isHardcore());
        assertFalse(IronManMode.IRONMAN.isHardcore());
        assertFalse(IronManMode.ULTIMATE_IRONMAN.isHardcore());
        assertTrue(IronManMode.HARDCORE_IRONMAN.isHardcore());
        assertFalse(IronManMode.GROUP_IRONMAN.isHardcore());
        assertTrue(IronManMode.HARDCORE_GROUP_IRONMAN.isHardcore());
    }
}
