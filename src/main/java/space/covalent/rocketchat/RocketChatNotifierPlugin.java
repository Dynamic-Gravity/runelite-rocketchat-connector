package space.covalent.rocketchat;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import space.covalent.rocketchat.notifiers.ClueNotifier;
import space.covalent.rocketchat.notifiers.DeathNotifier;
import space.covalent.rocketchat.notifiers.LevelNotifier;
import space.covalent.rocketchat.notifiers.LootNotifier;

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

	@Inject
	private LootNotifier lootNotifier;

	@Inject
	private ClueNotifier clueNotifier;

	@Override
	protected void startUp()
	{
		log.debug("Rocket.Chat Notifier started");
		eventBus.register(deathNotifier);
		eventBus.register(levelNotifier);
		eventBus.register(lootNotifier);
		eventBus.register(clueNotifier);
	}

	@Override
	protected void shutDown()
	{
		log.debug("Rocket.Chat Notifier stopped");
		eventBus.unregister(deathNotifier);
		eventBus.unregister(levelNotifier);
		eventBus.unregister(lootNotifier);
		eventBus.unregister(clueNotifier);
	}

	@Provides
	RocketChatNotifierConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RocketChatNotifierConfig.class);
	}
}
