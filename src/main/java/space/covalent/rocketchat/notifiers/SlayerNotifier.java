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
public class SlayerNotifier
{
	private static final Pattern TASK_COMPLETE = Pattern.compile(
		"You have completed your task! You killed (\\d[\\d,]*) (.+)\\.");

	@Inject RocketChatNotifierConfig config;
	@Inject WebhookClient webhookClient;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.notifyOnSlayer()) return;
		if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

		String msg = Text.removeTags(event.getMessage());
		Matcher m = TASK_COMPLETE.matcher(msg);
		if (!m.find()) return;

		String count = m.group(1);
		String monster = m.group(2);

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title("⚔️ Slayer task complete!")
					.text("Killed **" + count + "** " + monster + ".")
					.color("#E74C3C")
					.build()
			))
			.build());
	}
}
