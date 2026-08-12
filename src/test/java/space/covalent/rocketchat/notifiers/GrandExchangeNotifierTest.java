package space.covalent.rocketchat.notifiers;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class GrandExchangeNotifierTest
{
	@Mock RocketChatConnectorConfig config;
	@Mock WebhookClient webhookClient;
	@Mock ItemManager itemManager;

	@InjectMocks GrandExchangeNotifier notifier;

	@Test
	public void testSendsOnBought()
	{
		when(config.notifyOnGrandExchange()).thenReturn(true);
		when(config.ironManMode()).thenReturn(IronManMode.NONE);
		when(config.minGrandExchangeValue()).thenReturn(0);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Dragon bones");
		when(itemManager.getItemComposition(536)).thenReturn(comp);

		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getState()).thenReturn(GrandExchangeOfferState.BOUGHT);
		when(offer.getItemId()).thenReturn(536);
		when(offer.getTotalQuantity()).thenReturn(100);
		when(offer.getPrice()).thenReturn(2500);

		GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
		event.setOffer(offer);
		notifier.onGrandExchangeOfferChanged(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		String title = captor.getValue().getAttachments().get(0).getTitle();
		assertTrue(title.contains("Bought"));
		assertTrue(title.contains("Dragon bones"));
	}

	@Test
	public void testSkipsActiveOffer()
	{
		when(config.notifyOnGrandExchange()).thenReturn(true);
		when(config.ironManMode()).thenReturn(IronManMode.NONE);

		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getState()).thenReturn(GrandExchangeOfferState.BUYING);

		GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
		event.setOffer(offer);
		notifier.onGrandExchangeOfferChanged(event);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testSkipsBelowMinValue()
	{
		when(config.notifyOnGrandExchange()).thenReturn(true);
		when(config.ironManMode()).thenReturn(IronManMode.NONE);
		when(config.minGrandExchangeValue()).thenReturn(1000000);

		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getState()).thenReturn(GrandExchangeOfferState.BOUGHT);
		when(offer.getTotalQuantity()).thenReturn(1);
		when(offer.getPrice()).thenReturn(1);

		GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
		event.setOffer(offer);
		notifier.onGrandExchangeOfferChanged(event);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testSendsOnSold()
	{
		when(config.notifyOnGrandExchange()).thenReturn(true);
		when(config.ironManMode()).thenReturn(IronManMode.NONE);
		when(config.minGrandExchangeValue()).thenReturn(0);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Coal");
		when(itemManager.getItemComposition(453)).thenReturn(comp);

		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getState()).thenReturn(GrandExchangeOfferState.SOLD);
		when(offer.getItemId()).thenReturn(453);
		when(offer.getTotalQuantity()).thenReturn(1000);
		when(offer.getPrice()).thenReturn(200);

		GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
		event.setOffer(offer);
		notifier.onGrandExchangeOfferChanged(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		String title = captor.getValue().getAttachments().get(0).getTitle();
		assertTrue(title.contains("Sold"));
		assertTrue(title.contains("Coal"));
	}

	@Test
	public void testSuppressedWhenIronman()
	{
		when(config.notifyOnGrandExchange()).thenReturn(true);
		when(config.ironManMode()).thenReturn(IronManMode.IRONMAN);

		GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		event.setOffer(offer);
		notifier.onGrandExchangeOfferChanged(event);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testSuppressedWhenHardcoreIronman()
	{
		when(config.notifyOnGrandExchange()).thenReturn(true);
		when(config.ironManMode()).thenReturn(IronManMode.HARDCORE_IRONMAN);

		GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		event.setOffer(offer);
		notifier.onGrandExchangeOfferChanged(event);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testNotSuppressedWhenNone()
	{
		when(config.notifyOnGrandExchange()).thenReturn(true);
		when(config.ironManMode()).thenReturn(IronManMode.NONE);
		when(config.minGrandExchangeValue()).thenReturn(0);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Coal");
		when(itemManager.getItemComposition(453)).thenReturn(comp);

		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getState()).thenReturn(GrandExchangeOfferState.BOUGHT);
		when(offer.getItemId()).thenReturn(453);
		when(offer.getTotalQuantity()).thenReturn(100);
		when(offer.getPrice()).thenReturn(200);

		GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
		event.setOffer(offer);
		notifier.onGrandExchangeOfferChanged(event);

		verify(webhookClient).send(any(), any());
	}
}
