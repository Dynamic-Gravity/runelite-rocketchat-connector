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

    /**
     * OSRS Wiki display name of this account type's helm item, for emoji-icon lookup via
     * ItemEmoji.shortcode(). Null for NONE, which has no helm.
     */
    public String helmItemName()
    {
        switch (this)
        {
            case IRONMAN:
                return "Ironman helm";
            case ULTIMATE_IRONMAN:
                return "Ultimate ironman helm";
            case HARDCORE_IRONMAN:
                return "Hardcore ironman helm";
            case GROUP_IRONMAN:
                return "Group ironman helm";
            case HARDCORE_GROUP_IRONMAN:
                return "Hardcore group ironman helm";
            default:
                return null;
        }
    }
}
