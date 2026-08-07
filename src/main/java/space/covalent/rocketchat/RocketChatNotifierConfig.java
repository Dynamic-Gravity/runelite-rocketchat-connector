package space.covalent.rocketchat;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

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
}
