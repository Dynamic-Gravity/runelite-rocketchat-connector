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
import space.covalent.rocketchat.PlayerNameFormatter;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class CollectionLogNotifier
{
	private static final Pattern NEW_ENTRY = Pattern.compile(
		"New item added to your collection log: (.+)\\.");

	@Inject Client client;
	@Inject RocketChatConnectorConfig config;
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
		String name = client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null
			? client.getLocalPlayer().getName()
			: "Unknown";
		name = PlayerNameFormatter.format(name, config.ironManMode(), config.useEmojiIcons());

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title("📖 Collection log update")
					.text(name + " - new entry: **" + itemName + "**")
					.color("#2980B9")
					.build()
			))
			.build());
	}

	/**
	 * Fires a synthetic collection log message through the real onChatMessage path, for the
	 * developer-mode debug panel.
	 */
	public void sendTestNotification()
	{
		onChatMessage(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "",
			"New item added to your collection log: Twisted bow.", "", 0));
	}
}
