package space.covalent.rocketchat.notifiers;

import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.WebhookClient;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ChatPatternNotifierTest
{
	@Mock RocketChatNotifierConfig config;
	@Mock WebhookClient webhookClient;

	@InjectMocks ChatPatternNotifier notifier;

	private ChatMessage msg(String text)
	{
		ChatMessage m = new ChatMessage();
		m.setType(ChatMessageType.GAMEMESSAGE);
		m.setMessage(text);
		return m;
	}

	@Test
	public void testMatchingPatternFiresNotification()
	{
		when(config.notifyOnChatPattern()).thenReturn(true);
		when(config.chatPattern()).thenReturn("You found.*diamond");
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		notifier.onChatMessage(msg("You found a diamond."));
		verify(webhookClient).send(any(), any());
	}

	@Test
	public void testNonMatchingPatternSkips()
	{
		when(config.notifyOnChatPattern()).thenReturn(true);
		when(config.chatPattern()).thenReturn("You found.*diamond");

		notifier.onChatMessage(msg("You found nothing."));
		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testEmptyPatternSkips()
	{
		when(config.notifyOnChatPattern()).thenReturn(true);
		when(config.chatPattern()).thenReturn("");

		notifier.onChatMessage(msg("Anything."));
		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testInvalidRegexSkipsGracefully()
	{
		when(config.notifyOnChatPattern()).thenReturn(true);
		when(config.chatPattern()).thenReturn("[invalid");

		notifier.onChatMessage(msg("Anything."));
		verify(webhookClient, never()).send(any(), any());
	}

	@Test
	public void testCacheUpdatesWhenPatternChanges()
	{
		when(config.notifyOnChatPattern()).thenReturn(true);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

		when(config.chatPattern()).thenReturn("diamond");
		notifier.onChatMessage(msg("You found a diamond."));
		verify(webhookClient, times(1)).send(any(), any());

		when(config.chatPattern()).thenReturn("sapphire");
		notifier.onChatMessage(msg("You found a diamond."));
		verify(webhookClient, times(1)).send(any(), any());
	}
}
