package space.covalent.rocketchat;

public enum IronManMode
{
    NONE,
    IRONMAN,
    ULTIMATE_IRONMAN,
    HARDCORE_IRONMAN,
    GROUP_IRONMAN,
    HARDCORE_GROUP_IRONMAN;

    public boolean isIronman()
    {
        return this != NONE;
    }

    public boolean isHardcore()
    {
        return this == HARDCORE_IRONMAN || this == HARDCORE_GROUP_IRONMAN;
    }
}
