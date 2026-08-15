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
public class BossNotifier
{
	private static final Pattern KILL_COUNT = Pattern.compile(
		"Your (.+) kill count is: ([\\d,]+)\\.");
	private static final Pattern FIGHT_DURATION = Pattern.compile(
		"Fight duration: ([\\d:]+)\\.(?:.* Personal best: ([\\d:]+))?");

	@Inject Client client;
	@Inject RocketChatConnectorConfig config;
	@Inject WebhookClient webhookClient;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.notifyOnBoss()) return;
		if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

		String msg = Text.removeTags(event.getMessage());
		String name = client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null
			? client.getLocalPlayer().getName()
			: "Unknown";
		// Emoji shortcodes never render in the attachment title field (same Rocket.Chat
		// limitation as item icons), so the "name | ..." line lives in text, not title.
		name = PlayerNameFormatter.format(name, config.ironManMode(), config.useEmojiIcons());

		Matcher kc = KILL_COUNT.matcher(msg);
		if (kc.find())
		{
			if (config.bossPersonalBestOnly()) return;
			String boss = kc.group(1);
			String count = kc.group(2);
			int countVal;
			try { countVal = Integer.parseInt(count.replace(",", "")); }
			catch (NumberFormatException e) { return; }

			int interval = config.bossKillCountInterval();
			if (interval == 0) return;
			if (interval > 1 && countVal % interval != 0) return;

			webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
				.attachments(Collections.singletonList(
					RocketChatPayload.Attachment.builder()
						.title(boss + " kill count: " + count)
						.text(name)
						.color("#C0392B")
						.build()
				))
				.build());
			return;
		}

		Matcher fd = FIGHT_DURATION.matcher(msg);
		if (fd.find())
		{
			String duration = fd.group(1);
			String pb = fd.group(2);
			boolean isNewPb = pb != null && pb.equals(duration);

			if (config.bossPersonalBestOnly() && !isNewPb) return;

			String title = isNewPb
				? "New personal best: " + duration
				: "Fight duration: " + duration;

			webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
				.attachments(Collections.singletonList(
					RocketChatPayload.Attachment.builder()
						.title(title)
						.text(name)
						.color(isNewPb ? "#F1C40F" : "#95A5A6")
						.build()
				))
				.build());
		}
	}

	/**
	 * Fires a synthetic boss kill-count message through the real onChatMessage path, for the
	 * developer-mode debug panel. Still subject to bossPersonalBestOnly / bossKillCountInterval.
	 */
	public void sendTestNotification()
	{
		onChatMessage(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "",
			"Your Zulrah kill count is: 42.", "", 0));
	}
}
