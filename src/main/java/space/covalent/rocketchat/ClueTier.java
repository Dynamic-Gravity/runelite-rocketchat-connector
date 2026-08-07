package space.covalent.rocketchat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClueTier
{
	BEGINNER("Clue Scroll (Beginner)", 0),
	EASY("Clue Scroll (Easy)", 1),
	MEDIUM("Clue Scroll (Medium)", 2),
	HARD("Clue Scroll (Hard)", 3),
	ELITE("Clue Scroll (Elite)", 4),
	MASTER("Clue Scroll (Master)", 5);

	private final String lootSourceName;
	private final int rank;

	public static ClueTier fromLootSource(String name)
	{
		for (ClueTier tier : values())
		{
			if (tier.lootSourceName.equalsIgnoreCase(name))
			{
				return tier;
			}
		}
		return null;
	}
}
