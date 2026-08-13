package space.covalent.rocketchat.notifiers;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import space.covalent.rocketchat.ClueTier;
import space.covalent.rocketchat.ItemFilter;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.OsrsWiki;
import space.covalent.rocketchat.RarityLookupService;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class LootNotifier
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
		if (!config.notifyOnLoot())
		{
			return;
		}

		// Skip clue scroll rewards — handled by ClueNotifier
		if (ClueTier.fromLootSource(event.getName()) != null)
		{
			return;
		}

		Collection<ItemStack> items = event.getItems();
		if (items.isEmpty())
		{
			return;
		}

		IronManMode ironManMode = config.ironManMode();
		String whitelist = config.itemWhitelist();
		String ignorelist = config.itemIgnorelist();

		ItemStack bestStack = null;
		ItemComposition bestComp = null;
		long bestPrice = -1;
		boolean bestWhitelisted = false;

		for (ItemStack stack : items)
		{
			ItemComposition comp = itemManager.getItemComposition(stack.getId());
			String itemName = comp.getName();

			if (ItemFilter.matches(ignorelist, itemName))
			{
				continue;
			}

			long price = (ironManMode != null && ironManMode.isIronman())
				? (long) comp.getHaPrice() * stack.getQuantity()
				: (long) itemManager.getItemPrice(stack.getId()) * stack.getQuantity();

			boolean whitelisted = ItemFilter.matches(whitelist, itemName);

			boolean better;
			if (whitelisted != bestWhitelisted)
			{
				better = whitelisted;
			}
			else
			{
				better = price > bestPrice;
			}

			if (bestStack == null || better)
			{
				bestPrice = price;
				bestStack = stack;
				bestComp = comp;
				bestWhitelisted = whitelisted;
			}
		}

		if (bestStack == null)
		{
			return;
		}

		if (!bestWhitelisted && bestPrice < config.minLootValue())
		{
			return;
		}

		sendCard(event.getName(), bestStack, bestComp, bestPrice);
	}

	private void sendCard(String source, ItemStack stack, ItemComposition comp, long price)
	{
		String itemName = comp.getName();
		String valueLine = formatGp(price) + " gp";

		if (config.showDropRarity())
		{
			rarityLookupService.lookup(itemName, source,
				rarity -> webhookClient.send(config.webhookUrl(), buildPayload(source, stack, itemName, valueLine, rarity)));
		}
		else
		{
			webhookClient.send(config.webhookUrl(), buildPayload(source, stack, itemName, valueLine, null));
		}
	}

	private RocketChatPayload buildPayload(String source, ItemStack stack, String itemName, String valueLine, RarityLookupService.Rarity rarity)
	{
		String text = valueLine;
		if (rarity != null)
		{
			text += "\n" + formatRarityLine(rarity);
		}

		RocketChatPayload.Attachment.AttachmentBuilder attachment = RocketChatPayload.Attachment.builder()
			.title(stack.getQuantity() + "x " + itemName)
			.text(text)
			.color("#FFD700");

		if (OsrsWiki.isLinkable(itemName))
		{
			attachment.titleLink(OsrsWiki.pageUrl(itemName));
			attachment.thumbUrl(OsrsWiki.iconUrl(itemName));
		}

		return RocketChatPayload.builder()
			.text("💰 Loot from " + source)
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

	private static String formatRarityLine(RarityLookupService.Rarity rarity)
	{
		String percentText = String.format(Locale.ROOT, "%.2f", rarity.getPercent());
		if ("0.00".equals(percentText))
		{
			return rarity.getRaw();
		}
		return rarity.getRaw() + " (" + percentText + "%)";
	}
}
