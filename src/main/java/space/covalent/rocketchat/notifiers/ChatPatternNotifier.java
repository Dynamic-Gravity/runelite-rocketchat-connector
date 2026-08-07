package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Slf4j
@Singleton
public class ChatPatternNotifier
{
	@Inject RocketChatNotifierConfig config;
	@Inject WebhookClient webhookClient;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.notifyOnChatPattern()) return;

		String rawPattern = config.chatPattern();
		if (rawPattern == null || rawPattern.isEmpty()) return;

		String msg = Text.removeTags(event.getMessage());

		Pattern pattern;
		try
		{
			pattern = Pattern.compile(rawPattern, Pattern.CASE_INSENSITIVE);
		}
		catch (PatternSyntaxException e)
		{
			log.debug("Invalid chat pattern regex: {}", rawPattern);
			return;
		}

		if (!pattern.matcher(msg).find()) return;

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title(":bell: Pattern match")
					.text(msg)
					.color("#3498DB")
					.build()
			))
			.build());
	}
}
