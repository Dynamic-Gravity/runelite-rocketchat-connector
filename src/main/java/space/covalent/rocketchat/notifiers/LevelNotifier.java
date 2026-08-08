package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class LevelNotifier
{
	private static final String SKILL_ICON_BASE = "https://oldschool.runescape.wiki/images/thumb/%s_icon.png/25px-%s_icon.png";

	@Inject
	RocketChatNotifierConfig config;

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

		RocketChatPayload payload = RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title("📈 Level " + newLevel + " " + skillName + "!")
					.text("You have reached level **" + newLevel + "** " + skillName + ".")
					.color("#00FF00")
					.thumbUrl(iconUrl)
					.build()
			))
			.build();

		webhookClient.send(config.webhookUrl(), payload);
	}
}
