package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class GrandExchangeNotifier
{
	@Inject
	RocketChatConnectorConfig config;

	@Inject
	WebhookClient webhookClient;

	@Inject
	ItemManager itemManager;

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		if (!config.notifyOnGrandExchange())
		{
			return;
		}

		IronManMode ironManMode = config.ironManMode();
		if (ironManMode != null && ironManMode.isIronman())
		{
			return;
		}

		GrandExchangeOffer offer = event.getOffer();
		GrandExchangeOfferState state = offer.getState();

		if (state != GrandExchangeOfferState.BOUGHT && state != GrandExchangeOfferState.SOLD)
		{
			return;
		}

		long totalValue = (long) offer.getTotalQuantity() * offer.getPrice();
		if (totalValue < config.minGrandExchangeValue())
		{
			return;
		}

		ItemComposition comp = itemManager.getItemComposition(offer.getItemId());
		String itemName = comp.getName();
		String action = state == GrandExchangeOfferState.BOUGHT ? "Bought" : "Sold";
		String color = state == GrandExchangeOfferState.BOUGHT ? "#27AE60" : "#E74C3C";

		String body = "**Quantity:** " + offer.getTotalQuantity()
			+ "\n**Price each:** " + offer.getPrice() + " gp"
			+ "\n**Total:** " + formatGp(totalValue) + " gp";

		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title("🛒 " + action + " " + itemName)
					.text(body)
					.color(color)
					.build()
			))
			.build());
	}

	private static String formatGp(long value)
	{
		if (value >= 1_000_000)
		{
			return String.format("%.1fM", value / 1_000_000.0);
		}
		if (value >= 1_000)
		{
			return String.format("%.1fK", value / 1_000.0);
		}
		return String.valueOf(value);
	}
}
