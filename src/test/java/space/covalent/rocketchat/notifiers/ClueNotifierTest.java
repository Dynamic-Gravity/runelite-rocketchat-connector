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

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ClueNotifierTest
{
	@Mock RocketChatConnectorConfig config;
	@Mock WebhookClient webhookClient;
	@Mock ItemManager itemManager;
	@Mock RarityLookupService rarityLookupService;

	@InjectMocks ClueNotifier notifier;

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
}
