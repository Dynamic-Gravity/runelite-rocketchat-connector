package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.util.Text;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.CombatAchievementTier;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class CombatAchievementNotifier
{
	private static final Pattern CA_COMPLETE = Pattern.compile(
		"Congratulations, you've completed (?:a|an) (Easy|Medium|Hard|Elite|Master|Grandmaster) combat achievement: (.+)\\.");

	@Inject RocketChatNotifierConfig config;
	@Inject WebhookClient webhookClient;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.notifyOnCombatAchievement()) return;
		if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

		String msg = Text.removeTags(event.getMessage());
		Matcher m = CA_COMPLETE.matcher(msg);
		if (!m.find()) return;

		String tierName = m.group(1);
		String taskName = m.group(2);

		CombatAchievementTier tier;
		try { tier = CombatAchievementTier.valueOf(tierName.toUpperCase()); }
		catch (IllegalArgumentException e) { return; }

		if (tier.getRank() < config.minCombatAchievementTier().getRank()) return;

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title("🏅 Combat Achievement: " + taskName)
					.text("Tier: **" + tierName + "**")
					.color("#E67E22")
					.build()
			))
			.build());
	}
}
