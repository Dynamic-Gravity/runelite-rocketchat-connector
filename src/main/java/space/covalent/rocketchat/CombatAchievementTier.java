package space.covalent.rocketchat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CombatAchievementTier
{
	EASY("Easy", 0),
	MEDIUM("Medium", 1),
	HARD("Hard", 2),
	ELITE("Elite", 3),
	MASTER("Master", 4),
	GRANDMASTER("Grandmaster", 5);

	private final String displayName;
	private final int rank;
}
