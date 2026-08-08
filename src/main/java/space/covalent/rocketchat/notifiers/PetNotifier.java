package space.covalent.rocketchat.notifiers;

import java.util.Collections;
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
public class PetNotifier
{
	@Inject RocketChatNotifierConfig config;
	@Inject WebhookClient webhookClient;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.notifyOnPet()) return;
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) return;

		String msg = Text.removeTags(event.getMessage());
		if (!msg.contains("funny feeling") && !msg.contains("weird sneaking into your backpack")) return;

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title("🐕 You received a pet!")
					.text(msg)
					.color("#9B59B6")
					.build()
			))
			.build());
	}
}
