package space.covalent.rocketchat.notifiers;

import java.util.Collections;
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
public class PetNotifier
{
	@Inject Client client;
	@Inject RocketChatConnectorConfig config;
	@Inject WebhookClient webhookClient;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.notifyOnPet()) return;
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) return;

		String msg = Text.removeTags(event.getMessage());
		if (!msg.contains("funny feeling") && !msg.contains("weird sneaking into your backpack")) return;

		String name = client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null
			? client.getLocalPlayer().getName()
			: "Unknown";
		name = PlayerNameFormatter.format(name, config.ironManMode(), config.useEmojiIcons());

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title("🐕 " + name + " received a pet!")
					.text(msg)
					.color("#9B59B6")
					.build()
			))
			.build());
	}

	/**
	 * Fires a synthetic pet-received message through the real onChatMessage path, for the
	 * developer-mode debug panel.
	 */
	public void sendTestNotification()
	{
		onChatMessage(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "",
			"You have a funny feeling like you're being followed.", "", 0));
	}
}
