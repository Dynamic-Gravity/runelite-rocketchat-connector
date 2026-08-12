package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class DeathNotifier
{
	@Inject
	Client client;

	@Inject
	RocketChatConnectorConfig config;

	@Inject
	WebhookClient webhookClient;

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (!config.notifyOnDeath())
		{
			return;
		}
		Player local = client.getLocalPlayer();
		if (event.getActor() != local)
		{
			return;
		}

		String name = local.getName() != null ? local.getName() : "Unknown";
		int combatLevel = local.getCombatLevel();
		WorldPoint location = local.getWorldLocation();
		String locationStr = location != null
			? location.getX() + ", " + location.getY() + " (plane " + location.getPlane() + ")"
			: "Unknown";

		IronManMode ironManMode = config.ironManMode();
		boolean hardcore = ironManMode != null && ironManMode.isHardcore();
		String title = hardcore ? "☠️ HARDCORE DEATH: " + name : "💀 " + name + " has died";
		String color = hardcore ? "#7B0000" : "#FF0000";

		RocketChatPayload payload = RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title(title)
					.text("**Player:** " + name + "\n**Combat level:** " + combatLevel + "\n**Location:** " + locationStr)
					.color(color)
					.build()
			))
			.build();

		webhookClient.send(config.webhookUrl(), payload);
	}
}
