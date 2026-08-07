package space.covalent.rocketchat;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import space.covalent.rocketchat.notifiers.BossNotifier;
import space.covalent.rocketchat.notifiers.ChatPatternNotifier;
import space.covalent.rocketchat.notifiers.ClueNotifier;
import space.covalent.rocketchat.notifiers.CollectionLogNotifier;
import space.covalent.rocketchat.notifiers.CombatAchievementNotifier;
import space.covalent.rocketchat.notifiers.DeathNotifier;
import space.covalent.rocketchat.notifiers.DiaryNotifier;
import space.covalent.rocketchat.notifiers.GrandExchangeNotifier;
import space.covalent.rocketchat.notifiers.LevelNotifier;
import space.covalent.rocketchat.notifiers.LootNotifier;
import space.covalent.rocketchat.notifiers.PetNotifier;
import space.covalent.rocketchat.notifiers.QuestNotifier;
import space.covalent.rocketchat.notifiers.SlayerNotifier;

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

	@Inject
	private PetNotifier petNotifier;

	@Inject
	private QuestNotifier questNotifier;

	@Inject
	private SlayerNotifier slayerNotifier;

	@Inject
	private BossNotifier bossNotifier;

	@Inject
	private CollectionLogNotifier collectionLogNotifier;

	@Inject
	private CombatAchievementNotifier combatAchievementNotifier;

	@Inject
	private DiaryNotifier diaryNotifier;

	@Inject
	private ChatPatternNotifier chatPatternNotifier;

	@Inject
	private GrandExchangeNotifier grandExchangeNotifier;

	@Override
	protected void startUp()
	{
		log.debug("Rocket.Chat Notifier started");
		eventBus.register(deathNotifier);
		eventBus.register(levelNotifier);
		eventBus.register(lootNotifier);
		eventBus.register(clueNotifier);
		eventBus.register(petNotifier);
		eventBus.register(questNotifier);
		eventBus.register(slayerNotifier);
		eventBus.register(bossNotifier);
		eventBus.register(collectionLogNotifier);
		eventBus.register(combatAchievementNotifier);
		eventBus.register(diaryNotifier);
		eventBus.register(chatPatternNotifier);
		eventBus.register(grandExchangeNotifier);
	}

	@Override
	protected void shutDown()
	{
		log.debug("Rocket.Chat Notifier stopped");
		eventBus.unregister(deathNotifier);
		eventBus.unregister(levelNotifier);
		eventBus.unregister(lootNotifier);
		eventBus.unregister(clueNotifier);
		eventBus.unregister(petNotifier);
		eventBus.unregister(questNotifier);
		eventBus.unregister(slayerNotifier);
		eventBus.unregister(bossNotifier);
		eventBus.unregister(collectionLogNotifier);
		eventBus.unregister(combatAchievementNotifier);
		eventBus.unregister(diaryNotifier);
		eventBus.unregister(chatPatternNotifier);
		eventBus.unregister(grandExchangeNotifier);
	}

	@Provides
	RocketChatNotifierConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RocketChatNotifierConfig.class);
	}
}
