package space.covalent.rocketchat.notifiers;

import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.DrawManager;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import space.covalent.rocketchat.ClueTier;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.ItemFilter;
import space.covalent.rocketchat.OsrsWiki;
import space.covalent.rocketchat.RarityLookupService;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatFileUploadClient;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Slf4j
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

	@Inject
	Client client;

	@Inject
	DrawManager drawManager;

	@Inject
	OkHttpClient okHttpClient;

	@Inject
	RocketChatFileUploadClient fileUploadClient;

	private static final int REWARD_SCREEN_GROUP = InterfaceID.TrailRewardscreen.UNIVERSE >>> 16;

	private volatile CompletableFuture<byte[]> pendingScreenshot;

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (!config.clueScreenshotEnabled())
		{
			return;
		}

		if (event.getGroupId() != REWARD_SCREEN_GROUP)
		{
			return;
		}

		CompletableFuture<byte[]> future = new CompletableFuture<>();
		future.orTimeout(5, TimeUnit.SECONDS);
		pendingScreenshot = future;

		drawManager.requestNextFrameListener(image -> captureAndCrop(image, future));
	}

	private void captureAndCrop(Image image, CompletableFuture<byte[]> future)
	{
		Widget rewardWidget = client.getWidget(InterfaceID.TrailRewardscreen.UNIVERSE);
		if (rewardWidget == null)
		{
			future.completeExceptionally(new IllegalStateException("Reward widget not present"));
			return;
		}

		Rectangle bounds = rewardWidget.getBounds();
		BufferedImage cropped;
		try
		{
			BufferedImage frame = (BufferedImage) image;
			double scaleX = (double) frame.getWidth() / client.getCanvasWidth();
			double scaleY = (double) frame.getHeight() / client.getCanvasHeight();
			int x = (int) (bounds.x * scaleX);
			int y = (int) (bounds.y * scaleY);
			int width = (int) (bounds.width * scaleX);
			int height = (int) (bounds.height * scaleY);
			cropped = frame.getSubimage(x, y, width, height);
		}
		catch (RuntimeException e)
		{
			future.completeExceptionally(e);
			return;
		}

		okHttpClient.dispatcher().executorService().execute(() -> encodeToPng(cropped, future));
	}

	private void encodeToPng(BufferedImage image, CompletableFuture<byte[]> future)
	{
		try
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			future.complete(out.toByteArray());
		}
		catch (IOException e)
		{
			future.completeExceptionally(e);
		}
	}

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

		String tierName = tier.name().charAt(0) + tier.name().substring(1).toLowerCase();
		String wikiSource = "Reward casket (" + tier.name().toLowerCase() + ")";
		sendCard(tierName, wikiSource, bestStack, bestComp, bestPrice);

		if (config.clueScreenshotEnabled())
		{
			CompletableFuture<byte[]> screenshot = pendingScreenshot;
			pendingScreenshot = null;
			if (screenshot != null)
			{
				screenshot.whenComplete(this::handleScreenshot);
			}
		}
	}

	private void sendCard(String tierName, String wikiSource, ItemStack stack, ItemComposition comp, long price)
	{
		String itemName = comp.getName();
		String valueLine = price > 0 ? formatGp(price) + " gp" : null;

		if (config.showDropRarity())
		{
			rarityLookupService.lookup(itemName, wikiSource,
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
			text.append(formatRarityLine(rarity));
		}

		RocketChatPayload.Attachment.AttachmentBuilder attachment = RocketChatPayload.Attachment.builder()
			.title(stack.getQuantity() + "x " + itemName)
			.text(text.toString())
			.color("#8B4513");

		if (OsrsWiki.isLinkable(itemName))
		{
			attachment.titleLink(OsrsWiki.pageUrl(itemName));
			attachment.imageUrl(OsrsWiki.iconUrl(itemName));
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

	private static String formatRarityLine(RarityLookupService.Rarity rarity)
	{
		String percentText = String.format(Locale.ROOT, "%.2f", rarity.getPercent());
		if ("0.00".equals(percentText))
		{
			return rarity.getRaw();
		}
		return rarity.getRaw() + " (" + percentText + "%)";
	}

	private void handleScreenshot(byte[] bytes, Throwable captureError)
	{
		if (!hasUploadConfig())
		{
			log.debug("Clue screenshot enabled but Rocket.Chat upload credentials are incomplete");
			return;
		}

		String origin = serverOrigin(config.webhookUrl());
		if (origin == null)
		{
			log.debug("Could not determine Rocket.Chat server origin from webhook URL");
			return;
		}

		if (captureError != null)
		{
			sendSneakingSuspicion();
			return;
		}

		fileUploadClient.upload(origin, config.rocketChatRoomId(), config.rocketChatUserId(), config.rocketChatAuthToken(), bytes)
			.whenComplete((v, uploadError) ->
			{
				if (uploadError != null)
				{
					sendSneakingSuspicion();
				}
			});
	}

	private boolean hasUploadConfig()
	{
		return !config.rocketChatRoomId().isEmpty()
			&& !config.rocketChatUserId().isEmpty()
			&& !config.rocketChatAuthToken().isEmpty();
	}

	private void sendSneakingSuspicion()
	{
		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.text("🕵️ You have a sneaking suspicion this reward should've come with a screenshot...")
			.build());
	}

	private static String serverOrigin(String webhookUrl)
	{
		HttpUrl url = HttpUrl.parse(webhookUrl);
		if (url == null)
		{
			return null;
		}
		boolean defaultPort = url.port() == HttpUrl.defaultPort(url.scheme());
		return url.scheme() + "://" + url.host() + (defaultPort ? "" : ":" + url.port());
	}
}
