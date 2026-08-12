package space.covalent.rocketchat.notifiers;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import space.covalent.rocketchat.ClueTier;
import space.covalent.rocketchat.IronManMode;
import space.covalent.rocketchat.RocketChatConnectorConfig;
import space.covalent.rocketchat.WebhookClient;

import java.util.Collections;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ClueNotifierTest
{
    @Mock RocketChatConnectorConfig config;
    @Mock WebhookClient webhookClient;
    @Mock ItemManager itemManager;

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
}
