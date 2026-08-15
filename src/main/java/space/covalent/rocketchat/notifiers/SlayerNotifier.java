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
public class SlayerNotifier
{
	private static final Pattern TASK_COMPLETE = Pattern.compile(
		"You have completed your task! You killed (\\d[\\d,]*) (.+)\\.");

	@Inject Client client;
	@Inject RocketChatConnectorConfig config;
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
		String name = client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null
			? client.getLocalPlayer().getName()
			: "Unknown";
		name = PlayerNameFormatter.format(name, config.ironManMode(), config.useEmojiIcons());

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title("⚔️ Slayer task complete!")
					.text(name + " killed **" + count + "** " + monster + ".")
					.color("#E74C3C")
					.build()
			))
			.build());
	}

	/**
	 * Fires a synthetic slayer task completion message through the real onChatMessage path, for
	 * the developer-mode debug panel.
	 */
	public void sendTestNotification()
	{
		onChatMessage(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "",
			"You have completed your task! You killed 150 abyssal demons.", "", 0));
	}
}
