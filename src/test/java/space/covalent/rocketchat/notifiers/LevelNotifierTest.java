package space.covalent.rocketchat.notifiers;

import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LevelNotifierTest
{
	@Mock
	RocketChatNotifierConfig config;

	@Mock
	WebhookClient webhookClient;

	@InjectMocks
	LevelNotifier notifier;

	@Test
	public void testSendsNotificationOnLevelGain()
	{
		when(config.notifyOnLevel()).thenReturn(true);
		when(config.minLevel()).thenReturn(1);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		// First event initialises level — no notification
		StatChanged init = new StatChanged(Skill.ATTACK, 737627, 70, 0);
		notifier.onStatChanged(init);
		verify(webhookClient, never()).send(any(), any());

		// Second event with higher level — notification fires
		StatChanged levelUp = new StatChanged(Skill.ATTACK, 800000, 71, 0);
		notifier.onStatChanged(levelUp);

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		String title = captor.getValue().getAttachments().get(0).getTitle();
		assertTrue(title.contains("71"));
		assertTrue(title.contains("Attack"));
	}

	@Test
	public void testDoesNotNotifyBelowMinLevel()
	{
		when(config.notifyOnLevel()).thenReturn(true);
		when(config.minLevel()).thenReturn(50);

		StatChanged init = new StatChanged(Skill.STRENGTH, 0, 30, 0);
		notifier.onStatChanged(init);

		StatChanged levelUp = new StatChanged(Skill.STRENGTH, 0, 31, 0);
		notifier.onStatChanged(levelUp);

		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testDoesNotNotifyOnXpChangeWithoutLevelGain()
	{
		when(config.notifyOnLevel()).thenReturn(true);

		StatChanged init = new StatChanged(Skill.MAGIC, 0, 75, 0);
		notifier.onStatChanged(init);

		StatChanged xpTick = new StatChanged(Skill.MAGIC, 1200000, 75, 0);
		notifier.onStatChanged(xpTick);

		verify(webhookClient, never()).send(any(), any());
	}
}
