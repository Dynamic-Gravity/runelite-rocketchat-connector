package space.covalent.rocketchat;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("rocketchat-connector")
public interface RocketChatConnectorConfig extends Config
{
	@ConfigSection(
		name = "Webhook",
		description = "Rocket.Chat incoming webhook settings",
		position = 0
	)
	String webhookSection = "webhook";

	@ConfigItem(
		keyName = "webhookUrl",
		name = "Webhook URL",
		description = "Rocket.Chat incoming webhook URL",
		section = webhookSection,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default String webhookUrl()
	{
		return "";
	}

	@ConfigSection(
		name = "Death",
		description = "Notifications when you die",
		position = 1
	)
	String deathSection = "death";

	@ConfigItem(
		keyName = "notifyOnDeath",
		name = "Notify on death",
		description = "Send a Rocket.Chat message when you die",
		section = deathSection
	)
	default boolean notifyOnDeath()
	{
		return false;
	}

	@ConfigSection(
		name = "Levels",
		description = "Notifications when you gain a level",
		position = 2
	)
	String levelSection = "level";

	@ConfigItem(
		keyName = "notifyOnLevel",
		name = "Notify on level up",
		description = "Send a Rocket.Chat message when you gain a skill level",
		section = levelSection
	)
	default boolean notifyOnLevel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "minLevel",
		name = "Minimum level",
		description = "Only notify for levels at or above this value",
		section = levelSection
	)
	@Range(min = 1, max = 99)
	default int minLevel()
	{
		return 1;
	}

	@ConfigSection(
		name = "Loot",
		description = "Notifications when you receive loot",
		position = 3
	)
	String lootSection = "loot";

	@ConfigItem(
		keyName = "notifyOnLoot",
		name = "Notify on loot",
		description = "Send a Rocket.Chat message when you receive loot",
		section = lootSection
	)
	default boolean notifyOnLoot()
	{
		return false;
	}

	@ConfigItem(
		keyName = "minLootValue",
		name = "Minimum loot value",
		description = "Only notify if the highest-value item's price (GE) meets this threshold (gp)",
		section = lootSection
	)
	default int minLootValue()
	{
		return 100000;
	}

	@ConfigItem(
		keyName = "showDropRarity",
		name = "Show drop rarity",
		description = "Look up and display the item's drop rarity from the OSRS Wiki. Applies to both Loot and Clue Scroll notifications",
		section = lootSection,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean showDropRarity()
	{
		return false;
	}

	@ConfigSection(
		name = "Item Filters",
		description = "Override which item wins the Loot/Clue notification slot, regardless of value",
		position = 4
	)
	String itemFilterSection = "itemfilter";

	@ConfigItem(
		keyName = "itemWhitelist",
		name = "Item whitelist",
		description = "Comma-separated item names that always win the notification slot and bypass the minimum loot value",
		section = itemFilterSection
	)
	default String itemWhitelist()
	{
		return "";
	}

	@ConfigItem(
		keyName = "itemIgnorelist",
		name = "Item ignorelist",
		description = "Comma-separated item names that are never shown, even if they would otherwise be picked. Takes priority over the whitelist for the same item",
		section = itemFilterSection
	)
	default String itemIgnorelist()
	{
		return "";
	}

	@ConfigSection(
		name = "Clue Scrolls",
		description = "Notifications when you complete a clue scroll",
		position = 5
	)
	String clueSection = "clue";

	@ConfigItem(
		keyName = "notifyOnClue",
		name = "Notify on clue completion",
		description = "Send a Rocket.Chat message when you complete a clue scroll",
		section = clueSection
	)
	default boolean notifyOnClue()
	{
		return false;
	}

	@ConfigItem(
		keyName = "minClueTier",
		name = "Minimum clue tier",
		description = "Only notify for clues at or above this tier",
		section = clueSection
	)
	default ClueTier minClueTier()
	{
		return ClueTier.EASY;
	}

	@ConfigItem(
		keyName = "clueScreenshotEnabled",
		name = "Send reward screenshot",
		description = "Upload a screenshot of the clue reward screen to Rocket.Chat as a separate message",
		section = clueSection,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean clueScreenshotEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "rocketChatUserId",
		name = "Rocket.Chat user ID",
		description = "Personal access token user ID, used to upload the clue reward screenshot",
		section = clueSection
	)
	default String rocketChatUserId()
	{
		return "";
	}

	@ConfigItem(
		keyName = "rocketChatAuthToken",
		name = "Rocket.Chat auth token",
		description = "Personal access token auth token, used to upload the clue reward screenshot",
		section = clueSection,
		secret = true
	)
	default String rocketChatAuthToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "rocketChatRoomId",
		name = "Rocket.Chat room ID",
		description = "Target room/channel ID to upload the clue reward screenshot into",
		section = clueSection
	)
	default String rocketChatRoomId()
	{
		return "";
	}

	// Pet
	@ConfigSection(name = "Pets", description = "Pet drop notifications", position = 6)
	String petSection = "pet";

	@ConfigItem(keyName = "notifyOnPet", name = "Notify on pet", description = "Send a message when you receive a pet", section = petSection)
	default boolean notifyOnPet() { return false; }

	// Quest
	@ConfigSection(name = "Quests", description = "Quest completion notifications", position = 7)
	String questSection = "quest";

	@ConfigItem(keyName = "notifyOnQuest", name = "Notify on quest", description = "Send a message when you complete a quest", section = questSection)
	default boolean notifyOnQuest() { return false; }

	// Slayer
	@ConfigSection(name = "Slayer", description = "Slayer task completion notifications", position = 8)
	String slayerSection = "slayer";

	@ConfigItem(keyName = "notifyOnSlayer", name = "Notify on slayer task", description = "Send a message when you complete a slayer task", section = slayerSection)
	default boolean notifyOnSlayer() { return false; }

	// Boss
	@ConfigSection(name = "Boss Kills", description = "Boss kill count notifications", position = 9)
	String bossSection = "boss";

	@ConfigItem(keyName = "notifyOnBoss", name = "Notify on boss kill", description = "Send a message on boss kill count milestones", section = bossSection)
	default boolean notifyOnBoss() { return false; }

	@ConfigItem(keyName = "bossPersonalBestOnly", name = "Personal best only", description = "Only notify when a personal best time is set", section = bossSection)
	default boolean bossPersonalBestOnly() { return false; }

	@ConfigItem(keyName = "bossKillCountInterval", name = "Kill count interval", description = "Notify every N kills (0 = only on personal best)", section = bossSection)
	default int bossKillCountInterval() { return 1; }

	// Collection log
	@ConfigSection(name = "Collection Log", description = "Collection log new-entry notifications", position = 10)
	String collectionLogSection = "collectionlog";

	@ConfigItem(keyName = "notifyOnCollectionLog", name = "Notify on collection log", description = "Send a message when a new item is added to your collection log", section = collectionLogSection)
	default boolean notifyOnCollectionLog() { return false; }

	// Combat achievements
	@ConfigSection(name = "Combat Achievements", description = "Combat achievement notifications", position = 11)
	String combatAchievementSection = "combatachievement";

	@ConfigItem(keyName = "notifyOnCombatAchievement", name = "Notify on CA", description = "Send a message when you complete a combat achievement", section = combatAchievementSection)
	default boolean notifyOnCombatAchievement() { return false; }

	@ConfigItem(keyName = "minCombatAchievementTier", name = "Minimum tier", description = "Only notify for this tier or above", section = combatAchievementSection)
	default CombatAchievementTier minCombatAchievementTier() { return CombatAchievementTier.EASY; }

	// Achievement diaries
	@ConfigSection(name = "Achievement Diaries", description = "Diary completion notifications", position = 12)
	String diarySection = "diary";

	@ConfigItem(keyName = "notifyOnDiary", name = "Notify on diary", description = "Send a message when you complete an achievement diary", section = diarySection)
	default boolean notifyOnDiary() { return false; }

	@ConfigItem(keyName = "minDiaryTier", name = "Minimum tier", description = "Only notify for this tier or above", section = diarySection)
	default DiaryTier minDiaryTier() { return DiaryTier.EASY; }

	// Custom Pattern
	@ConfigSection(name = "Custom Pattern", description = "Notify on custom chat messages", position = 13)
	String chatPatternSection = "chatpattern";

	@ConfigItem(keyName = "notifyOnChatPattern", name = "Notify on pattern match",
		description = "Send a message when a chat message matches the custom pattern",
		section = chatPatternSection)
	default boolean notifyOnChatPattern() { return false; }

	@ConfigItem(keyName = "chatPattern", name = "Pattern (regex)",
		description = "Java regex to match against chat messages",
		section = chatPatternSection)
	default String chatPattern() { return ""; }

	// Grand Exchange
	@ConfigSection(name = "Grand Exchange", description = "Grand Exchange trade notifications", position = 14)
	String grandExchangeSection = "grandexchange";

	@ConfigItem(keyName = "notifyOnGrandExchange", name = "Notify on GE trade",
		description = "Send a message when a Grand Exchange offer completes",
		section = grandExchangeSection)
	default boolean notifyOnGrandExchange() { return false; }

	@ConfigItem(keyName = "minGrandExchangeValue", name = "Minimum trade value",
		description = "Only notify if the completed trade value meets this threshold (gp)",
		section = grandExchangeSection)
	default int minGrandExchangeValue() { return 0; }

	// Iron Man
	@ConfigSection(name = "Iron Man", description = "Iron Man account type settings", position = 15)
	String ironManSection = "ironman";

	@ConfigItem(keyName = "ironManMode", name = "Account type",
		description = "Enables iron man behaviours appropriate for your account type",
		section = ironManSection)
	default IronManMode ironManMode() { return IronManMode.NONE; }
}
