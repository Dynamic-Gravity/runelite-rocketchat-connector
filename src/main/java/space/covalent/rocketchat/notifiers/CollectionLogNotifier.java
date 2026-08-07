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
public class CollectionLogNotifier
{
	private static final Pattern NEW_ENTRY = Pattern.compile(
		"New item added to your collection log: (.+)\\.");

	@Inject RocketChatNotifierConfig config;
	@Inject WebhookClient webhookClient;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.notifyOnCollectionLog()) return;
		if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

		String msg = Text.removeTags(event.getMessage());
		Matcher m = NEW_ENTRY.matcher(msg);
		if (!m.find()) return;

		String itemName = m.group(1);

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title(":book: Collection log update")
					.text("New entry: **" + itemName + "**")
					.color("#2980B9")
					.build()
			))
			.build());
	}
}
