package space.covalent.rocketchat.notifiers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import space.covalent.rocketchat.ClueTier;
import space.covalent.rocketchat.ItemEmoji;
import space.covalent.rocketchat.ItemFilter;
import space.covalent.rocketchat.OsrsWiki;
import space.covalent.rocketchat.PlayerNameFormatter;
import space.covalent.rocketchat.RarityLookupService;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class LootNotifier
{
	@Inject
	Client client;

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

		String whitelist = config.itemWhitelist();
		String ignorelist = config.itemIgnorelist();

		ItemStack bestStack = null;
		ItemComposition bestComp = null;
		long bestGePrice = -1;
		long bestHaPrice = -1;
		boolean bestWhitelisted = false;

		for (ItemStack stack : items)
		{
			ItemComposition comp = itemManager.getItemComposition(stack.getId());
			String itemName = comp.getName();

			if (ItemFilter.matches(ignorelist, itemName))
			{
				continue;
			}

			long gePrice = (long) itemManager.getItemPrice(stack.getId()) * stack.getQuantity();
			long haPrice = (long) comp.getHaPrice() * stack.getQuantity();

			boolean whitelisted = ItemFilter.matches(whitelist, itemName);

			boolean better;
			if (whitelisted != bestWhitelisted)
			{
				better = whitelisted;
			}
			else
			{
				better = gePrice > bestGePrice;
			}

			if (bestStack == null || better)
			{
				bestGePrice = gePrice;
				bestHaPrice = haPrice;
				bestStack = stack;
				bestComp = comp;
				bestWhitelisted = whitelisted;
			}
		}

		if (bestStack == null)
		{
			return;
		}

		if (!bestWhitelisted && bestGePrice < config.minLootValue())
		{
			return;
		}

		sendCard(event.getName(), bestStack, bestComp, bestGePrice, bestHaPrice);
	}

	/**
	 * Fires a synthetic loot drop through the real onLootReceived path, for the developer-mode
	 * debug panel. Respects all normal config gates (notifyOnLoot, minLootValue, filters, etc.).
	 */
	public void sendTestNotification()
	{
		LootReceived event = new LootReceived("Debug test", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(ItemID.ABYSSAL_WHIP, 1)), 1, null);
		onLootReceived(event);
	}

	private void sendCard(String source, ItemStack stack, ItemComposition comp, long gePrice, long haPrice)
	{
		String itemName = comp.getName();

		if (config.showDropRarity())
		{
			rarityLookupService.lookup(itemName, source,
				rarity -> webhookClient.send(config.webhookUrl(), buildPayload(source, stack, comp, gePrice, haPrice, rarity)));
		}
		else
		{
			webhookClient.send(config.webhookUrl(), buildPayload(source, stack, comp, gePrice, haPrice, null));
		}
	}

	private RocketChatPayload buildPayload(String source, ItemStack stack, ItemComposition comp, long gePrice, long haPrice, RarityLookupService.Rarity rarity)
	{
		String itemName = comp.getName();
		String playerName = client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null
			? client.getLocalPlayer().getName()
			: "Unknown";
		String headerName = PlayerNameFormatter.format("**" + playerName + "**", config.ironManMode(), config.useEmojiIcons());

		String itemLabel = stack.getQuantity() > 1 ? stack.getQuantity() + "x " + itemName : itemName;
		if (config.useEmojiIcons() && OsrsWiki.isLinkable(itemName))
		{
			itemLabel = ":" + ItemEmoji.shortcode(itemName) + ": " + itemLabel;
		}
		String itemText = OsrsWiki.isLinkable(itemName)
			? "[" + itemLabel + "](" + OsrsWiki.pageUrl(itemName) + ")"
			: itemLabel;

		String text = headerName + "\nJust got **" + itemText + "** from *" + source + "*";

		RocketChatPayload.Attachment.AttachmentBuilder attachment = RocketChatPayload.Attachment.builder()
			.color("#FFD700")
			.text(text)
			.fields(buildFields(gePrice, haPrice, rarity));

		// image_url, not thumb_url - OSRS item icon PNGs aren't padded to a square, and thumb_url
		// force-stretches non-square images. Skipped entirely in emoji-icon mode, where the icon
		// already appears inline above via the emoji shortcode.
		if (!config.useEmojiIcons() && OsrsWiki.isLinkable(itemName))
		{
			attachment.imageUrl(OsrsWiki.iconUrl(itemName));
		}

		return RocketChatPayload.builder()
			.text("💰 Loot from " + source)
			.attachments(Collections.singletonList(attachment.build()))
			.build();
	}

	private List<RocketChatPayload.Field> buildFields(long gePrice, long haPrice, RarityLookupService.Rarity rarity)
	{
		List<RocketChatPayload.Field> fields = new ArrayList<>();

		if (rarity != null)
		{
			fields.add(RocketChatPayload.Field.builder()
				.title("Rarity")
				.value("`# " + formatRarityLine(rarity) + "`")
				.short_(true)
				.build());
		}

		fields.add(RocketChatPayload.Field.builder()
			.title("HA Value")
			.value("`" + formatGp(haPrice) + " gp`")
			.short_(true)
			.build());

		fields.add(RocketChatPayload.Field.builder()
			.title("GE Value")
			.value("`" + formatGp(gePrice) + " gp`")
			.short_(true)
			.build());

		return fields;
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
