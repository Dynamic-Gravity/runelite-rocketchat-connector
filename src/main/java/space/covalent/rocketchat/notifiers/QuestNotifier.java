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
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class QuestNotifier
{
	private static final Pattern QUEST_COMPLETE = Pattern.compile(
		"Congratulations, you've completed (.+)!");

	@Inject RocketChatNotifierConfig config;
	@Inject WebhookClient webhookClient;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.notifyOnQuest()) return;
		if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

		String msg = Text.removeTags(event.getMessage());
		Matcher m = QUEST_COMPLETE.matcher(msg);
		if (!m.find()) return;

		String questName = m.group(1);

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title(":trophy: Quest complete!")
					.text("You have completed **" + questName + "**.")
					.color("#1E8449")
					.build()
			))
			.build());
	}
}
