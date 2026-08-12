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
import space.covalent.rocketchat.notifiers.HardcoreStatusNotifier;
import space.covalent.rocketchat.notifiers.LevelNotifier;
import space.covalent.rocketchat.notifiers.LootNotifier;
import space.covalent.rocketchat.notifiers.PetNotifier;
import space.covalent.rocketchat.notifiers.QuestNotifier;
import space.covalent.rocketchat.notifiers.SlayerNotifier;

@Slf4j
@PluginDescriptor(
	name = "Rocket.Chat Connector",
	description = "Send game event notifications to a Rocket.Chat channel via webhook",
	tags = {"notification", "webhook", "rocketchat"}
)
public class RocketChatConnectorPlugin extends Plugin
{
	private static final String OLD_CONFIG_GROUP = "rocketchat-notifier";
	private static final String NEW_CONFIG_GROUP = "rocketchat-connector";

	private static final String[] CONFIG_KEYS = {
		"webhookUrl", "notifyOnDeath", "notifyOnLevel", "minLevel", "notifyOnLoot", "minLootValue",
		"notifyOnClue", "minClueTier", "notifyOnPet", "notifyOnQuest", "notifyOnSlayer", "notifyOnBoss",
		"bossPersonalBestOnly", "bossKillCountInterval", "notifyOnCollectionLog", "notifyOnCombatAchievement",
		"minCombatAchievementTier", "notifyOnDiary", "minDiaryTier", "notifyOnChatPattern", "chatPattern",
		"notifyOnGrandExchange", "minGrandExchangeValue", "ironManMode"
	};

	@Inject
	private RocketChatConnectorConfig config;

	@Inject
	private ConfigManager configManager;

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

	@Inject
	private HardcoreStatusNotifier hardcoreStatusNotifier;

	@Override
	protected void startUp()
	{
		migrateConfig();
		log.debug("Rocket.Chat Connector started");
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
		eventBus.register(hardcoreStatusNotifier);
	}

	@Override
	protected void shutDown()
	{
		log.debug("Rocket.Chat Connector stopped");
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
		eventBus.unregister(hardcoreStatusNotifier);
	}

	@Provides
	RocketChatConnectorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RocketChatConnectorConfig.class);
	}

	private void migrateConfig()
	{
		for (String key : CONFIG_KEYS)
		{
			String oldValue = configManager.getConfiguration(OLD_CONFIG_GROUP, key);
			if (oldValue == null)
			{
				continue;
			}

			if (configManager.getConfiguration(NEW_CONFIG_GROUP, key) == null)
			{
				configManager.setConfiguration(NEW_CONFIG_GROUP, key, oldValue);
			}

			configManager.unsetConfiguration(OLD_CONFIG_GROUP, key);
		}
	}
}
