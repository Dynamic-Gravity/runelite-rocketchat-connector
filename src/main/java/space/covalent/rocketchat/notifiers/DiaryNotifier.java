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
import space.covalent.rocketchat.DiaryTier;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class DiaryNotifier
{
	private static final Pattern DIARY_COMPLETE = Pattern.compile(
		"Congratulations! You have completed all of the (.+) (Easy|Medium|Hard|Elite) Diary tasks\\.");

	@Inject RocketChatNotifierConfig config;
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

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title(":clipboard: " + area + " " + tierName + " Diary complete!")
					.color("#27AE60")
					.build()
			))
			.build());
	}
}
