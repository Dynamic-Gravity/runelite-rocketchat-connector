package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class DeathNotifier
{
	@Inject
	Client client;

	@Inject
	RocketChatNotifierConfig config;

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

		RocketChatPayload payload = RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title(":skull: " + name + " has died")
					.text("**Player:** " + name + "\n**Combat level:** " + combatLevel + "\n**Location:** " + locationStr)
					.color("#FF0000")
					.build()
			))
			.build();

		webhookClient.send(config.webhookUrl(), payload);
	}
}
