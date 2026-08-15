package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.PlayerNameFormatter;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class LevelNotifier
{
	private static final String SKILL_ICON_BASE = "https://oldschool.runescape.wiki/images/thumb/%s_icon.png/25px-%s_icon.png";

	@Inject
	Client client;

	@Inject
	RocketChatConnectorConfig config;

	@Inject
	WebhookClient webhookClient;

	private final Map<Skill, Integer> lastLevels = new EnumMap<>(Skill.class);

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (!config.notifyOnLevel())
		{
			return;
		}

		Skill skill = event.getSkill();
		int newLevel = event.getLevel();
		Integer lastLevel = lastLevels.put(skill, newLevel);

		if (lastLevel == null || newLevel <= lastLevel)
		{
			return;
		}

		if (newLevel < config.minLevel())
		{
			return;
		}

		String skillName = skill.getName();
		String iconUrl = String.format(SKILL_ICON_BASE, skillName, skillName);
		String playerName = client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null
			? client.getLocalPlayer().getName()
			: "Unknown";
		playerName = PlayerNameFormatter.format(playerName, config.ironManMode(), config.useEmojiIcons());

		RocketChatPayload payload = RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title("📈 Level " + newLevel + " " + skillName + "!")
					.text(playerName + " has reached level **" + newLevel + "** " + skillName + ".")
					.color("#00FF00")
					.thumbUrl(iconUrl)
					.build()
			))
			.build();

		webhookClient.send(config.webhookUrl(), payload);
	}

	/**
	 * Fires a synthetic level-up through the real onStatChanged path, for the developer-mode
	 * debug panel. onStatChanged only notifies when a level rises above the last-seen level for
	 * that skill, and lastLevels is already seeded from real gameplay by the time this runs (the
	 * client fires StatChanged for every skill on login) - so this reads the player's actual
	 * current Attack level, seeds with that exact value first (guaranteed not to fire, since
	 * equal doesn't count as a rise), then fires once with level+1.
	 */
	public void sendTestNotification()
	{
		int currentLevel = client.getRealSkillLevel(Skill.ATTACK);
		onStatChanged(new StatChanged(Skill.ATTACK, 0, currentLevel, currentLevel));
		onStatChanged(new StatChanged(Skill.ATTACK, 0, currentLevel + 1, currentLevel + 1));
	}
}
