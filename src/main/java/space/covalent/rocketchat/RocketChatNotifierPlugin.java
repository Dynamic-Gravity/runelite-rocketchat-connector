package space.covalent.rocketchat;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import space.covalent.rocketchat.notifiers.DeathNotifier;
import space.covalent.rocketchat.notifiers.LevelNotifier;

@Slf4j
@PluginDescriptor(
	name = "Rocket.Chat Notifier",
	description = "Send game event notifications to a Rocket.Chat channel via webhook",
	tags = {"notification", "webhook", "rocketchat"}
)
public class RocketChatNotifierPlugin extends Plugin
{
	@Inject
	private RocketChatNotifierConfig config;

	@Inject
	private EventBus eventBus;

	@Inject
	private DeathNotifier deathNotifier;

	@Inject
	private LevelNotifier levelNotifier;

	@Override
	protected void startUp()
	{
		log.debug("Rocket.Chat Notifier started");
		eventBus.register(deathNotifier);
		eventBus.register(levelNotifier);
	}

	@Override
	protected void shutDown()
	{
		log.debug("Rocket.Chat Notifier stopped");
		eventBus.unregister(deathNotifier);
		eventBus.unregister(levelNotifier);
	}

	@Provides
	RocketChatNotifierConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RocketChatNotifierConfig.class);
	}
}
