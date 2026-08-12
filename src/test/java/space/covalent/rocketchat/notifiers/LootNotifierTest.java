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
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.RarityLookupService;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LootNotifierTest
{
	@Mock
	RocketChatConnectorConfig config;

	@Mock
	WebhookClient webhookClient;

	@Mock
	ItemManager itemManager;

	@Mock
	RarityLookupService rarityLookupService;

	@InjectMocks
	LootNotifier notifier;

	@Test
	public void testSendsCardForHighestValueItem()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.showDropRarity()).thenReturn(false);
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

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Arrays.asList(new ItemStack(cheapId, 1), new ItemStack(expensiveId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("1x Abyssal whip", attachment.getTitle());
	}

	@Test
	public void testSkipsWhenBestValueBelowThreshold()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(100000);

		int itemId = 526;
		ItemComposition comp = mock(ItemComposition.class);
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(50);

		LootReceived event = new LootReceived("Goblin", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testSkipsEmptyLoot()
	{
		when(config.notifyOnLoot()).thenReturn(true);

		LootReceived event = new LootReceived("Some Boss", 0, LootRecordType.NPC,
			Collections.emptyList(), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testUsesHighAlchValueInIronmanMode()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.ironManMode()).thenReturn(IronManMode.IRONMAN);
		when(config.showDropRarity()).thenReturn(false);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(comp.getHaPrice()).thenReturn(120000);
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient).send(any(), any());
		verify(itemManager, never()).getItemPrice(itemId);
	}

	@Test
	public void testHighAlchValueAppliedToMinThreshold()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(200000);
		when(config.ironManMode()).thenReturn(IronManMode.IRONMAN);

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getHaPrice()).thenReturn(120000);
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testCardIncludesWikiLinkAndIcon()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.showDropRarity()).thenReturn(false);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertEquals("https://oldschool.runescape.wiki/w/Abyssal_whip", attachment.getTitleLink());
		assertEquals("https://oldschool.runescape.wiki/w/Special:FilePath/Abyssal_whip.png", attachment.getThumbUrl());
	}

	@Test
	public void testCoinsOmitsWikiLink()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.showDropRarity()).thenReturn(false);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int itemId = 995;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Coins");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(1000);

		LootReceived event = new LootReceived("Man", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1000)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment attachment = captor.getValue().getAttachments().get(0);
		assertNull(attachment.getTitleLink());
		assertNull(attachment.getThumbUrl());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testRarityLineAppendedWhenEnabledAndFound()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
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
			callback.accept(new RarityLookupService.Rarity("1/512", 0.1953125));
			return null;
		}).when(rarityLookupService).lookup(anyString(), anyString(), any());

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		String text = captor.getValue().getAttachments().get(0).getText();
		assertTrue(text.contains("1/512 (0.20%)"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testRarityLookupNoMatchStillSendsWithoutRarityLine()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
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

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		String text = captor.getValue().getAttachments().get(0).getText();
		assertEquals("2.0M gp", text);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testTinyRarityPercentageOmitsMisleadingZero()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.showDropRarity()).thenReturn(true);

		int itemId = 20997;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Twisted bow");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(1_000_000_000);

		doAnswer(invocation ->
		{
			Consumer<RarityLookupService.Rarity> callback = invocation.getArgument(2);
			callback.accept(new RarityLookupService.Rarity("1/16,256", 100.0 / 16256));
			return null;
		}).when(rarityLookupService).lookup(anyString(), anyString(), any());

		LootReceived event = new LootReceived("Chambers of Xeric", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		String text = captor.getValue().getAttachments().get(0).getText();
		assertTrue(text.contains("1/16,256"));
		assertTrue(!text.contains("0.00%"));
	}

	@Test
	public void testRarityLookupSkippedWhenDisabled()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(0);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.showDropRarity()).thenReturn(false);

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		verify(rarityLookupService, never()).lookup(any(), any(), any());
		verify(webhookClient).send(any(), any());
	}
}
