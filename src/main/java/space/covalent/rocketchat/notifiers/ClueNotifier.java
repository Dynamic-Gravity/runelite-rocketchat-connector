package space.covalent.rocketchat.notifiers;

import java.util.Collection;
import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import space.covalent.rocketchat.ClueTier;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.OsrsWiki;
import space.covalent.rocketchat.RarityLookupService;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class ClueNotifier
{
	@Inject
	RocketChatConnectorConfig config;

	@Inject
	WebhookClient webhookClient;

	@Inject
	ItemManager itemManager;

	@Inject
	RarityLookupService rarityLookupService;

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
		if (items.isEmpty())
		{
			return;
		}

		IronManMode ironManMode = config.ironManMode();
		ItemStack bestStack = null;
		ItemComposition bestComp = null;
		long bestPrice = -1;

		for (ItemStack stack : items)
		{
			ItemComposition comp = itemManager.getItemComposition(stack.getId());
			long price = (ironManMode != null && ironManMode.isIronman())
				? (long) comp.getHaPrice() * stack.getQuantity()
				: (long) itemManager.getItemPrice(stack.getId()) * stack.getQuantity();
			if (price > bestPrice)
			{
				bestPrice = price;
				bestStack = stack;
				bestComp = comp;
			}
		}

		String tierName = tier.name().charAt(0) + tier.name().substring(1).toLowerCase();
		sendCard(tierName, event.getName(), bestStack, bestComp, bestPrice);
	}

	private void sendCard(String tierName, String source, ItemStack stack, ItemComposition comp, long price)
	{
		String itemName = comp.getName();
		String valueLine = price > 0 ? formatGp(price) + " gp" : null;

		if (config.showDropRarity())
		{
			rarityLookupService.lookup(itemName, source,
				rarity -> webhookClient.send(config.webhookUrl(), buildPayload(tierName, stack, itemName, valueLine, rarity)));
		}
		else
		{
			webhookClient.send(config.webhookUrl(), buildPayload(tierName, stack, itemName, valueLine, null));
		}
	}

	private RocketChatPayload buildPayload(String tierName, ItemStack stack, String itemName, String valueLine, RarityLookupService.Rarity rarity)
	{
		StringBuilder text = new StringBuilder();
		if (valueLine != null)
		{
			text.append(valueLine);
		}
		if (rarity != null)
		{
			if (text.length() > 0)
			{
				text.append("\n");
			}
			text.append(rarity.getRaw()).append(" (").append(String.format("%.2f%%", rarity.getPercent())).append(")");
		}

		RocketChatPayload.Attachment.AttachmentBuilder attachment = RocketChatPayload.Attachment.builder()
			.title(stack.getQuantity() + "x " + itemName)
			.text(text.toString())
			.color("#8B4513");

		if (OsrsWiki.isLinkable(itemName))
		{
			attachment.titleLink(OsrsWiki.pageUrl(itemName));
			attachment.thumbUrl(OsrsWiki.iconUrl(itemName));
		}

		return RocketChatPayload.builder()
			.text("📜 " + tierName + " Clue Scroll completed")
			.attachments(Collections.singletonList(attachment.build()))
			.build();
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
