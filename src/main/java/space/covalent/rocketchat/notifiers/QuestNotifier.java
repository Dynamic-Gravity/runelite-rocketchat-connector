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
public class QuestNotifier
{
	private static final Pattern QUEST_COMPLETE = Pattern.compile(
		"Congratulations, you've completed (?!.*combat achievement)(.+)!");

	@Inject Client client;
	@Inject RocketChatConnectorConfig config;
	@Inject WebhookClient webhookClient;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.notifyOnQuest()) return;
		if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

		String msg = Text.removeTags(event.getMessage());
		Matcher m = QUEST_COMPLETE.matcher(msg);
		if (!m.find()) return;

		String questName = m.group(1);
		String name = client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null
			? client.getLocalPlayer().getName()
			: "Unknown";
		name = PlayerNameFormatter.format(name, config.ironManMode(), config.useEmojiIcons());

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title("🏆 Quest complete!")
					.text(name + " has completed **" + questName + "**.")
					.color("#1E8449")
					.build()
			))
			.build());
	}

	/**
	 * Fires a synthetic quest completion message through the real onChatMessage path, for the
	 * developer-mode debug panel.
	 */
	public void sendTestNotification()
	{
		onChatMessage(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "",
			"Congratulations, you've completed Dragon Slayer II!", "", 0));
	}
}
