package space.covalent.rocketchat;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("rocketchat-notifier")
public interface RocketChatNotifierConfig extends Config
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
}
