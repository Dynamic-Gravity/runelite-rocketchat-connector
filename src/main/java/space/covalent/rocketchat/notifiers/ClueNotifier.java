package space.covalent.rocketchat.notifiers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import space.covalent.rocketchat.ClueTier;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class ClueNotifier
{
	@Inject
	RocketChatNotifierConfig config;

	@Inject
	WebhookClient webhookClient;

	@Inject
	ItemManager itemManager;

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!config.notifyOnClue())
		{
			return;
		}

		ClueTier tier = ClueTier.fromLootSource(event.getName());
		if (tier == null)
		{
			return;
		}

		if (tier.getRank() < config.minClueTier().getRank())
		{
			return;
		}

		Collection<ItemStack> items = event.getItems();
		List<String> itemLines = new ArrayList<>();
		long totalValue = 0;

		for (ItemStack stack : items)
		{
			long price = (long) itemManager.getItemPrice(stack.getId()) * stack.getQuantity();
			totalValue += price;
			ItemComposition comp = itemManager.getItemComposition(stack.getId());
			itemLines.add(stack.getQuantity() + "x **" + comp.getName() + "**");
		}

		String body = String.join("\n", itemLines);
		if (totalValue > 0)
		{
			body += "\n\n**Total:** " + formatGp(totalValue) + " gp";
		}

		String tierName = tier.name().charAt(0) + tier.name().substring(1).toLowerCase();

		RocketChatPayload payload = RocketChatPayload.builder()
			.attachments(Collections.singletonList(
				RocketChatPayload.Attachment.builder()
					.title("📜 " + tierName + " Clue Scroll completed")
					.text(body)
					.color("#8B4513")
					.build()
			))
			.build();

		webhookClient.send(config.webhookUrl(), payload);
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
