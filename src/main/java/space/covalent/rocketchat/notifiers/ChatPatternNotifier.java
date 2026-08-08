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

	private String lastRawPattern = null;
	private Pattern cachedPattern = null;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.notifyOnChatPattern()) return;

		String rawPattern = config.chatPattern();
		if (rawPattern == null || rawPattern.isEmpty()) return;

		if (!rawPattern.equals(lastRawPattern))
		{
			try
			{
				cachedPattern = Pattern.compile(rawPattern, Pattern.CASE_INSENSITIVE);
				lastRawPattern = rawPattern;
			}
			catch (PatternSyntaxException e)
			{
				log.debug("Invalid chat pattern regex: {}", rawPattern);
				cachedPattern = null;
				lastRawPattern = rawPattern;
				return;
			}
		}

		if (cachedPattern == null) return;

		String msg = Text.removeTags(event.getMessage());
		if (!cachedPattern.matcher(msg).find()) return;

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title("🔔 Pattern match")
					.text(msg)
					.color("#3498DB")
					.build()
			))
			.build());
	}
}
