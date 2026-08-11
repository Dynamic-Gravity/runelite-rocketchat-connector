package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class HardcoreStatusNotifier
{
	private static final Pattern HC_LOST = Pattern.compile(
		"You have lost your Hardcore (?:Group )?Ironman status\\.");

	@Inject
	Client client;

	@Inject
	RocketChatNotifierConfig config;

	@Inject
	WebhookClient webhookClient;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		IronManMode ironManMode = config.ironManMode();
		if (ironManMode == null || !ironManMode.isHardcore())
		{
			return;
		}
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String msg = Text.removeTags(event.getMessage());
		Matcher m = HC_LOST.matcher(msg);
		if (!m.find())
		{
			return;
		}

		String name = client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null
			? client.getLocalPlayer().getName()
			: "Unknown";

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title("☠️ Hardcore status lost: " + name)
					.text(msg)
					.color("#000000")
					.build()
			))
			.build());
	}
}
