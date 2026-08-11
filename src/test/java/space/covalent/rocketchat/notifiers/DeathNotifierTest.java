package space.covalent.rocketchat.notifiers;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DeathNotifierTest
{
	@Mock
	Client client;

	@Mock
	RocketChatNotifierConfig config;

	@Mock
	WebhookClient webhookClient;

	@InjectMocks
	DeathNotifier notifier;

	@Test
	public void testSendsNotificationWhenLocalPlayerDies()
	{
		when(config.notifyOnDeath()).thenReturn(true);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		Player localPlayer = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(localPlayer);
		when(localPlayer.getName()).thenReturn("Zezima");
		when(localPlayer.getCombatLevel()).thenReturn(126);

		ActorDeath event = new ActorDeath(localPlayer);
		notifier.onActorDeath(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(eq("http://example.com/hooks/test"), captor.capture());

		String text = captor.getValue().getAttachments().get(0).getText();
		assertTrue(text.contains("Zezima"));
	}

	@Test
	public void testDoesNotSendWhenNotLocalPlayer()
	{
		when(config.notifyOnDeath()).thenReturn(true);
		Player localPlayer = mock(Player.class);
		Player otherPlayer = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(localPlayer);

		ActorDeath event = new ActorDeath(otherPlayer);
		notifier.onActorDeath(event);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testDoesNotSendWhenDisabled()
	{
		when(config.notifyOnDeath()).thenReturn(false);
		Player localPlayer = mock(Player.class);

		ActorDeath event = new ActorDeath(localPlayer);
		notifier.onActorDeath(event);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testHardcoreDeathHasAlarmingTitleAndColor()
	{
		when(config.notifyOnDeath()).thenReturn(true);
		when(config.ironManMode()).thenReturn(IronManMode.HARDCORE_IRONMAN);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		Player localPlayer = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(localPlayer);
		when(localPlayer.getName()).thenReturn("Zezima");
		when(localPlayer.getCombatLevel()).thenReturn(126);

		ActorDeath event = new ActorDeath(localPlayer);
		notifier.onActorDeath(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment att = captor.getValue().getAttachments().get(0);
		assertTrue(att.getTitle().contains("HARDCORE DEATH"));
		assertEquals("#7B0000", att.getColor());
	}

	@Test
	public void testNonHardcoreDeathHasNormalTitleAndColor()
	{
		when(config.notifyOnDeath()).thenReturn(true);
		when(config.ironManMode()).thenReturn(IronManMode.IRONMAN);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		Player localPlayer = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(localPlayer);
		when(localPlayer.getName()).thenReturn("Zezima");
		when(localPlayer.getCombatLevel()).thenReturn(126);

		ActorDeath event = new ActorDeath(localPlayer);
		notifier.onActorDeath(event);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		RocketChatPayload.Attachment att = captor.getValue().getAttachments().get(0);
		assertFalse(att.getTitle().contains("HARDCORE"));
		assertEquals("#FF0000", att.getColor());
	}
}
