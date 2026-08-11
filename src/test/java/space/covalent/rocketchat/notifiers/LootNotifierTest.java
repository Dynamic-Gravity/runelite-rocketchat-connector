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
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LootNotifierTest
{
	@Mock
	RocketChatNotifierConfig config;

	@Mock
	WebhookClient webhookClient;

	@Mock
	ItemManager itemManager;

	@InjectMocks
	LootNotifier notifier;

	@Test
	public void testSendsNotificationWhenValueMeetsThreshold()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(100000);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		int itemId = 4151; // Abyssal whip
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);

		LootReceived event = new LootReceived("Abyssal Sire", 0, LootRecordType.NPC,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
		notifier.onLootReceived(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		String text = captor.getValue().getAttachments().get(0).getText();
		assertTrue(text.contains("Abyssal whip"));
	}

	@Test
	public void testSkipsWhenValueBelowThreshold()
	{
		when(config.notifyOnLoot()).thenReturn(true);
		when(config.minLootValue()).thenReturn(100000);

		int itemId = 526; // Bones
		when(itemManager.getItemPrice(itemId)).thenReturn(50);
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Bones");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);

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
}
