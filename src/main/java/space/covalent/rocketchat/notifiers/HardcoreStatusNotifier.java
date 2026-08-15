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
import space.covalent.rocketchat.PlayerNameFormatter;
import space.covalent.rocketchat.RocketChatConnectorConfig;
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
	RocketChatConnectorConfig config;

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
		handleMessage(event);
	}

	private void handleMessage(ChatMessage event)
	{
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
		// Emoji shortcodes never render in the attachment title field (same Rocket.Chat
		// limitation as item icons), so the name lives in text, not title.
		name = PlayerNameFormatter.format(name, config.ironManMode(), config.useEmojiIcons());

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title("☠️ Hardcore status lost!")
					.text(name + "\n" + msg)
					.color("#000000")
					.build()
			))
			.build());
	}

	/**
	 * Fires a synthetic hardcore-status-lost message through the real payload-building path, for
	 * the developer-mode debug panel. Bypasses the hardcore-account gate (there's no separate
	 * notifyOnX toggle here - Account type itself is the gate) so the message can be previewed
	 * regardless of your configured account type.
	 */
	public void sendTestNotification()
	{
		handleMessage(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "",
			"You have lost your Hardcore Ironman status.", "", 0));
	}
}
