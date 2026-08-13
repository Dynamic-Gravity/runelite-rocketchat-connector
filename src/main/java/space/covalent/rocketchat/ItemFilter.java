package space.covalent.rocketchat;

public final class ItemFilter
{
	private ItemFilter() {}

	public static boolean matches(String csv, String itemName)
	{
		if (csv == null || csv.isEmpty())
		{
			return false;
		}

		for (String entry : csv.split(","))
		{
			String trimmed = entry.trim();
			if (!trimmed.isEmpty() && trimmed.equalsIgnoreCase(itemName))
			{
				return true;
			}
		}

		return false;
	}
}
