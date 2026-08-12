package space.covalent.rocketchat.notifiers;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.WebhookClient;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class HardcoreStatusNotifierTest
{
    @Mock Client client;
    @Mock RocketChatConnectorConfig config;
    @Mock WebhookClient webhookClient;

    @InjectMocks HardcoreStatusNotifier notifier;

    private static ChatMessage gameMessage(String text)
    {
        ChatMessage msg = new ChatMessage();
        msg.setType(ChatMessageType.GAMEMESSAGE);
        msg.setMessage(text);
        return msg;
    }

    @Test
    public void testFiresOnHcimStatusLost()
    {
        when(config.ironManMode()).thenReturn(IronManMode.HARDCORE_IRONMAN);
        when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Zezima");
        when(client.getLocalPlayer()).thenReturn(player);

        notifier.onChatMessage(gameMessage("You have lost your Hardcore Ironman status."));

        verify(webhookClient).send(any(), any());
    }

    @Test
    public void testFiresOnHcgimStatusLost()
    {
        when(config.ironManMode()).thenReturn(IronManMode.HARDCORE_GROUP_IRONMAN);
        when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Zezima");
        when(client.getLocalPlayer()).thenReturn(player);

        notifier.onChatMessage(gameMessage("You have lost your Hardcore Group Ironman status."));

        verify(webhookClient).send(any(), any());
    }

    @Test
    public void testSkipsWhenNotHardcore()
    {
        when(config.ironManMode()).thenReturn(IronManMode.IRONMAN);

        notifier.onChatMessage(gameMessage("You have lost your Hardcore Ironman status."));

        verify(webhookClient, never()).send(any(), any());
    }

    @Test
    public void testSkipsNonMatchingMessage()
    {
        when(config.ironManMode()).thenReturn(IronManMode.HARDCORE_IRONMAN);

        notifier.onChatMessage(gameMessage("You have completed a hard task."));

        verify(webhookClient, never()).send(any(), any());
    }

    @Test
    public void testSkipsNonGameMessageType()
    {
        when(config.ironManMode()).thenReturn(IronManMode.HARDCORE_IRONMAN);

        ChatMessage msg = new ChatMessage();
        msg.setType(ChatMessageType.PUBLICCHAT);
        msg.setMessage("You have lost your Hardcore Ironman status.");
        notifier.onChatMessage(msg);

        verify(webhookClient, never()).send(any(), any());
    }
}
