package space.covalent.rocketchat.notifiers;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import space.covalent.rocketchat.ClueTier;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.RarityLookupService;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.DrawManager;
import okhttp3.OkHttpClient;
import org.junit.Before;
import space.covalent.rocketchat.RocketChatFileUploadClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.atLeastOnce;

@RunWith(MockitoJUnitRunner.class)
public class ClueNotifierTest
{
	@Mock RocketChatConnectorConfig config;
	@Mock WebhookClient webhookClient;
	@Mock ItemManager itemManager;
	@Mock RarityLookupService rarityLookupService;
	@Mock Client client;
	@Mock DrawManager drawManager;
	@Mock RocketChatFileUploadClient fileUploadClient;

	@InjectMocks ClueNotifier notifier;

	@Before
	public void setUp()
	{
		notifier.okHttpClient = new OkHttpClient();
		// Default canvas size matches the 10x10 frames used by most screenshot tests, so
		// captureAndCrop's scale factor is 1.0 unless a test overrides these explicitly.
		lenient().when(client.getCanvasWidth()).thenReturn(10);
		lenient().when(client.getCanvasHeight()).thenReturn(10);
	}

	@Test
	public void testUsesHighAlchValueInIronmanMode()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.ironManMode()).thenReturn(IronManMode.IRONMAN);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(comp.getHaPrice()).thenReturn(120000);
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient).send(any(), any());
		verify(itemManager, never()).getItemPrice(itemId);
	}

	@Test
	public void testUsesGePriceWhenNotIronman()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int itemId = 4151;
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient).send(any(), any());
		verify(itemManager).getItemPrice(itemId);
	}

	@Test
	public void testSendsCardForHighestValueItem()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int cheapId = 526;
		ItemComposition cheapComp = mock(ItemComposition.class);
		when(itemManager.getItemComposition(cheapId)).thenReturn(cheapComp);
		when(itemManager.getItemPrice(cheapId)).thenReturn(50);

		int expensiveId = 4151;
		ItemComposition expensiveComp = mock(ItemComposition.class);
		when(expensiveComp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(expensiveId)).thenReturn(expensiveComp);
		when(itemManager.getItemPrice(expensiveId)).thenReturn(2000000);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Arrays.asList(new ItemStack(cheapId, 1), new ItemStack(expensiveId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("1x Abyssal whip", attachment.getTitle());
	}

	@Test
	public void testCardIncludesWikiLinkAndIcon()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("https://oldschool.runescape.wiki/w/Abyssal_whip", attachment.getTitleLink());
		assertEquals("https://oldschool.runescape.wiki/w/Special:FilePath/Abyssal_whip.png", attachment.getThumbUrl());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testRarityLineAppendedWhenEnabledAndFound()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.showDropRarity()).thenReturn(true);

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		doAnswer(invocation ->
		{
			Consumer<RarityLookupService.Rarity> callback = invocation.getArgument(2);
			callback.accept(new RarityLookupService.Rarity("12/128", 9.375));
			return null;
		}).when(rarityLookupService).lookup(anyString(), anyString(), any());

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		String text = captor.getValue().getAttachments().get(0).getText();
		assertTrue(text.contains("12/128 (9.38%)"));
	}

	@Test
	public void testRarityLookupUsesCasketSourceNameNotEventName()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.showDropRarity()).thenReturn(true);

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Wooden shield (g)");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(rarityLookupService).lookup(eq("Wooden shield (g)"), eq("Reward casket (easy)"), any());
	}

	@Test
	public void testRarityLookupSkippedWhenDisabled()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.showDropRarity()).thenReturn(false);

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(rarityLookupService, never()).lookup(any(), any(), any());
		verify(webhookClient).send(any(), any());
	}

	@Test
	public void testCoinsOmitsWikiLink()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int itemId = 995;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Coins");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(1000);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1000)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertNull(attachment.getTitleLink());
		assertNull(attachment.getThumbUrl());
	}

	@Test
	public void testZeroPriceOmitsValueLine()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int itemId = 314;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Clue scroll (easy)");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(0);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		String text = captor.getValue().getAttachments().get(0).getText();
		assertEquals("", text);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testRarityLookupNoMatchStillSendsWithoutRarityLine()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.showDropRarity()).thenReturn(true);

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		doAnswer(invocation ->
		{
			Consumer<RarityLookupService.Rarity> callback = invocation.getArgument(2);
			callback.accept(null);
			return null;
		}).when(rarityLookupService).lookup(anyString(), anyString(), any());

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		String text = captor.getValue().getAttachments().get(0).getText();
		assertEquals("2.0M gp", text);
	}

	@Test
	public void testWhitelistedItemWinsOverHigherValueItem()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.itemWhitelist()).thenReturn("Rune arrow");

		int expensiveId = 4151;
		ItemComposition expensiveComp = mock(ItemComposition.class);
		when(expensiveComp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(expensiveId)).thenReturn(expensiveComp);
		when(itemManager.getItemPrice(expensiveId)).thenReturn(2000000);

		int whitelistedId = 892;
		ItemComposition whitelistedComp = mock(ItemComposition.class);
		when(whitelistedComp.getName()).thenReturn("Rune arrow");
		when(itemManager.getItemComposition(whitelistedId)).thenReturn(whitelistedComp);
		when(itemManager.getItemPrice(whitelistedId)).thenReturn(100);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Arrays.asList(new ItemStack(expensiveId, 1), new ItemStack(whitelistedId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("1x Rune arrow", attachment.getTitle());
	}

	@Test
	public void testIgnoredItemFallsBackToNextBest()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.itemIgnorelist()).thenReturn("Abyssal whip");

		int expensiveId = 4151;
		ItemComposition expensiveComp = mock(ItemComposition.class);
		when(expensiveComp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(expensiveId)).thenReturn(expensiveComp);
		lenient().when(itemManager.getItemPrice(expensiveId)).thenReturn(2000000);

		int cheapId = 526;
		ItemComposition cheapComp = mock(ItemComposition.class);
		when(cheapComp.getName()).thenReturn("Bones");
		when(itemManager.getItemComposition(cheapId)).thenReturn(cheapComp);
		when(itemManager.getItemPrice(cheapId)).thenReturn(50);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Arrays.asList(new ItemStack(expensiveId, 1), new ItemStack(cheapId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("1x Bones", attachment.getTitle());
		verify(itemManager, never()).getItemPrice(expensiveId);
	}

	@Test
	public void testAllItemsIgnoredSendsNothing()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.itemIgnorelist()).thenReturn("Bones");

		int itemId = 526;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Bones");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testItemOnBothListsIsTreatedAsIgnored()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.itemWhitelist()).thenReturn("Bones");
		when(config.itemIgnorelist()).thenReturn("Bones");

		int ignoredId = 526;
		ItemComposition ignoredComp = mock(ItemComposition.class);
		when(ignoredComp.getName()).thenReturn("Bones");
		when(itemManager.getItemComposition(ignoredId)).thenReturn(ignoredComp);
		lenient().when(itemManager.getItemPrice(ignoredId)).thenReturn(5000000);

		int otherId = 4151;
		ItemComposition otherComp = mock(ItemComposition.class);
		when(otherComp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(otherId)).thenReturn(otherComp);
		when(itemManager.getItemPrice(otherId)).thenReturn(2000000);

		LootReceived event = new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Arrays.asList(new ItemStack(ignoredId, 1), new ItemStack(otherId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("1x Abyssal whip", attachment.getTitle());
	}

	private void loadRewardScreen(Rectangle widgetBounds)
	{
		Widget rewardWidget = mock(Widget.class);
		when(rewardWidget.getBounds()).thenReturn(widgetBounds);
		when(client.getWidget(InterfaceID.TrailRewardscreen.UNIVERSE)).thenReturn(rewardWidget);

		WidgetLoaded widgetLoaded = new WidgetLoaded();
		widgetLoaded.setGroupId(InterfaceID.TrailRewardscreen.UNIVERSE >>> 16);
		notifier.onWidgetLoaded(widgetLoaded);
	}

	@SuppressWarnings("unchecked")
	private void deliverFrame(BufferedImage frame)
	{
		ArgumentCaptor<Consumer<Image>> captor = ArgumentCaptor.forClass(Consumer.class);
		verify(drawManager, atLeastOnce()).requestNextFrameListener(captor.capture());
		captor.getValue().accept(frame);
	}

	private LootReceived clueEasyLootEvent(int itemId)
	{
		return new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
	}

	@Test
	public void testIgnoresWidgetLoadedForOtherInterfaces()
	{
		WidgetLoaded widgetLoaded = new WidgetLoaded();
		widgetLoaded.setGroupId(999);
		notifier.onWidgetLoaded(widgetLoaded);

		verify(drawManager, never()).requestNextFrameListener(any());
	}

	@Test
	public void testUploadsScreenshotWhenCaptureAndConfigSucceed()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.clueScreenshotEnabled()).thenReturn(true);
		when(config.rocketChatRoomId()).thenReturn("room1");
		when(config.rocketChatUserId()).thenReturn("user1");
		when(config.rocketChatAuthToken()).thenReturn("token1");
		when(fileUploadClient.upload(any(), any(), any(), any(), any()))
			.thenReturn(CompletableFuture.completedFuture(null));

		loadRewardScreen(new Rectangle(0, 0, 10, 10));
		deliverFrame(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		notifier.onLootReceived(clueEasyLootEvent(itemId));

		verify(fileUploadClient, timeout(2000))
			.upload(eq("http://example.com"), eq("room1"), eq("user1"), eq("token1"), any());
		// item card was sent synchronously before the async screenshot chain runs - no race here
		verify(webhookClient, times(1)).send(any(), any());
	}

	@Test
	public void testNoUploadAttemptedWhenScreenshotDisabled()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.clueScreenshotEnabled()).thenReturn(false);

		// clueScreenshotEnabled() gate short-circuits onWidgetLoaded before it ever touches
		// client.getWidget(...) or drawManager - so no frame is requested at all when disabled.
		WidgetLoaded widgetLoaded = new WidgetLoaded();
		widgetLoaded.setGroupId(InterfaceID.TrailRewardscreen.UNIVERSE >>> 16);
		notifier.onWidgetLoaded(widgetLoaded);
		verify(drawManager, never()).requestNextFrameListener(any());

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		notifier.onLootReceived(clueEasyLootEvent(itemId));

		verify(fileUploadClient, never()).upload(any(), any(), any(), any(), any());
		verify(webhookClient, times(1)).send(any(), any());
	}

	@Test
	public void testUploadSkippedSilentlyWhenCredentialsIncomplete()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.clueScreenshotEnabled()).thenReturn(true);
		when(config.rocketChatRoomId()).thenReturn("");
		// hasUploadConfig() short-circuits on the empty roomId, so these two are never read
		lenient().when(config.rocketChatUserId()).thenReturn("user1");
		lenient().when(config.rocketChatAuthToken()).thenReturn("token1");

		loadRewardScreen(new Rectangle(0, 0, 10, 10));
		deliverFrame(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		notifier.onLootReceived(clueEasyLootEvent(itemId));

		verify(fileUploadClient, never()).upload(any(), any(), any(), any(), any());
		// exactly the one item-card message - no whimsical fallback either
		verify(webhookClient, timeout(500).times(1)).send(any(), any());
	}

	@Test
	public void testSneakingSuspicionMessageWhenRewardWidgetMissing()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.clueScreenshotEnabled()).thenReturn(true);
		// captureError short-circuits handleScreenshot before these are ever read
		lenient().when(config.rocketChatRoomId()).thenReturn("room1");
		lenient().when(config.rocketChatUserId()).thenReturn("user1");
		lenient().when(config.rocketChatAuthToken()).thenReturn("token1");
		// client.getWidget(...) not stubbed -> returns null, simulating the reward widget having closed already

		WidgetLoaded widgetLoaded = new WidgetLoaded();
		widgetLoaded.setGroupId(InterfaceID.TrailRewardscreen.UNIVERSE >>> 16);
		notifier.onWidgetLoaded(widgetLoaded);
		deliverFrame(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		notifier.onLootReceived(clueEasyLootEvent(itemId));

		verify(fileUploadClient, never()).upload(any(), any(), any(), any(), any());
		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient, timeout(2000).times(2)).send(any(), captor.capture());
		assertTrue(captor.getAllValues().stream()
			.anyMatch(p -> p.getText() != null && p.getText().contains("sneaking suspicion")));
	}

	@Test
	public void testSneakingSuspicionMessageWhenUploadFails()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.clueScreenshotEnabled()).thenReturn(true);
		when(config.rocketChatRoomId()).thenReturn("room1");
		when(config.rocketChatUserId()).thenReturn("user1");
		when(config.rocketChatAuthToken()).thenReturn("token1");

		CompletableFuture<Void> failedUpload = new CompletableFuture<>();
		failedUpload.completeExceptionally(new java.io.IOException("Upload rejected: 401"));
		when(fileUploadClient.upload(any(), any(), any(), any(), any())).thenReturn(failedUpload);

		loadRewardScreen(new Rectangle(0, 0, 10, 10));
		deliverFrame(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		notifier.onLootReceived(clueEasyLootEvent(itemId));

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient, timeout(2000).times(2)).send(any(), captor.capture());
		assertTrue(captor.getAllValues().stream()
			.anyMatch(p -> p.getText() != null && p.getText().contains("sneaking suspicion")));
	}

	@Test
	public void testSecondLootEventDoesNotReuseFirstCluesScreenshot()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.clueScreenshotEnabled()).thenReturn(true);
		when(config.rocketChatRoomId()).thenReturn("room1");
		when(config.rocketChatUserId()).thenReturn("user1");
		when(config.rocketChatAuthToken()).thenReturn("token1");
		when(fileUploadClient.upload(any(), any(), any(), any(), any()))
			.thenReturn(CompletableFuture.completedFuture(null));

		// Only one WidgetLoaded/capture happens, before either LootReceived - simulating the
		// unordered-events scenario the reviewer flagged as not actually guaranteed by RuneLite.
		loadRewardScreen(new Rectangle(0, 0, 10, 10));
		deliverFrame(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

		int firstItemId = 4151;
		ItemComposition firstComp = mock(ItemComposition.class);
		when(firstComp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(firstItemId)).thenReturn(firstComp);
		when(itemManager.getItemPrice(firstItemId)).thenReturn(2000000);

		int secondItemId = 995;
		ItemComposition secondComp = mock(ItemComposition.class);
		when(secondComp.getName()).thenReturn("Coins");
		when(itemManager.getItemComposition(secondItemId)).thenReturn(secondComp);
		when(itemManager.getItemPrice(secondItemId)).thenReturn(1000);

		notifier.onLootReceived(clueEasyLootEvent(firstItemId));
		notifier.onLootReceived(clueEasyLootEvent(secondItemId));

		// pendingScreenshot must be cleared after the first read, so the second clue's loot
		// event does not pick up and upload the first clue's (now stale) screenshot.
		verify(fileUploadClient, timeout(2000).times(1)).upload(any(), any(), any(), any(), any());
		verify(webhookClient, times(2)).send(any(), any());
	}

	@Test
	public void testCaptureScalesWidgetBoundsToDeliveredFrameSize() throws Exception
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.clueScreenshotEnabled()).thenReturn(true);
		when(config.rocketChatRoomId()).thenReturn("room1");
		when(config.rocketChatUserId()).thenReturn("user1");
		when(config.rocketChatAuthToken()).thenReturn("token1");
		when(fileUploadClient.upload(any(), any(), any(), any(), any()))
			.thenReturn(CompletableFuture.completedFuture(null));

		// Canvas (unstretched) is 50x50 but the delivered frame is 100x100 - a 2x stretched-mode
		// or HiDPI frame. The widget bounds are in unstretched canvas coordinates and must be
		// scaled by 2x before cropping.
		when(client.getCanvasWidth()).thenReturn(50);
		when(client.getCanvasHeight()).thenReturn(50);

		loadRewardScreen(new Rectangle(10, 10, 20, 20));
		deliverFrame(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB));

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		notifier.onLootReceived(clueEasyLootEvent(itemId));

		ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
		verify(fileUploadClient, timeout(2000)).upload(any(), any(), any(), any(), bytesCaptor.capture());

		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytesCaptor.getValue()));
		assertEquals(40, decoded.getWidth());
		assertEquals(40, decoded.getHeight());
	}

	@Test
	public void testNoFallbackMessageWhenCredentialsIncompleteAndCaptureFails()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.clueScreenshotEnabled()).thenReturn(true);
		when(config.rocketChatRoomId()).thenReturn("");
		// hasUploadConfig() short-circuits on the empty roomId, so these two are never read
		lenient().when(config.rocketChatUserId()).thenReturn("user1");
		lenient().when(config.rocketChatAuthToken()).thenReturn("token1");
		// client.getWidget(...) not stubbed -> returns null, so capture fails - same setup as
		// testSneakingSuspicionMessageWhenRewardWidgetMissing, but now combined with incomplete
		// credentials. The config-gap check must win: no fallback message should be sent.

		WidgetLoaded widgetLoaded = new WidgetLoaded();
		widgetLoaded.setGroupId(InterfaceID.TrailRewardscreen.UNIVERSE >>> 16);
		notifier.onWidgetLoaded(widgetLoaded);
		deliverFrame(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		notifier.onLootReceived(clueEasyLootEvent(itemId));

		verify(fileUploadClient, never()).upload(any(), any(), any(), any(), any());
		// exactly the one item-card message - no whimsical fallback despite the failed capture
		verify(webhookClient, timeout(500).times(1)).send(any(), any());
	}
}
