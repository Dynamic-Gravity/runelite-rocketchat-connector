package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.util.Text;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.DiaryTier;
import space.covalent.rocketchat.PlayerNameFormatter;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class DiaryNotifier
{
	private static final Pattern DIARY_COMPLETE = Pattern.compile(
		"Congratulations! You have completed all of the (.+) (Easy|Medium|Hard|Elite) Diary tasks\\.");

	@Inject Client client;
	@Inject RocketChatConnectorConfig config;
	@Inject WebhookClient webhookClient;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.notifyOnDiary()) return;
		if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

		String msg = Text.removeTags(event.getMessage());
		Matcher m = DIARY_COMPLETE.matcher(msg);
		if (!m.find()) return;

		String area = m.group(1);
		String tierName = m.group(2);

		DiaryTier tier;
		try { tier = DiaryTier.valueOf(tierName.toUpperCase()); }
		catch (IllegalArgumentException e) { return; }

		if (tier.getRank() < config.minDiaryTier().getRank()) return;

		String name = client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null
			? client.getLocalPlayer().getName()
			: "Unknown";
		name = PlayerNameFormatter.format(name, config.ironManMode(), config.useEmojiIcons());

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title("📋 " + area + " " + tierName + " Diary complete!")
					.text(name + " has completed all " + area + " " + tierName + " Diary tasks.")
					.color("#27AE60")
					.build()
			))
			.build());
	}

	/**
	 * Fires a synthetic diary completion message through the real onChatMessage path, for the
	 * developer-mode debug panel. Uses the highest tier so it clears minDiaryTier regardless of
	 * your configured minimum.
	 */
	public void sendTestNotification()
	{
		onChatMessage(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "",
			"Congratulations! You have completed all of the Ardougne Elite Diary tasks.", "", 0));
	}
}
