package space.covalent.rocketchat.debug;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.Timer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.PluginPanel;
import space.covalent.rocketchat.notifiers.BossNotifier;
import space.covalent.rocketchat.notifiers.ChatPatternNotifier;
import space.covalent.rocketchat.notifiers.ClueNotifier;
import space.covalent.rocketchat.notifiers.CollectionLogNotifier;
import space.covalent.rocketchat.notifiers.CombatAchievementNotifier;
import space.covalent.rocketchat.notifiers.DeathNotifier;
import space.covalent.rocketchat.notifiers.DiaryNotifier;
import space.covalent.rocketchat.notifiers.GrandExchangeNotifier;
import space.covalent.rocketchat.notifiers.LevelNotifier;
import space.covalent.rocketchat.notifiers.LootNotifier;
import space.covalent.rocketchat.notifiers.PetNotifier;
import space.covalent.rocketchat.notifiers.QuestNotifier;
import space.covalent.rocketchat.notifiers.SlayerNotifier;

/**
 * Developer-mode only. Fires a synthetic event through each notifier's real onXxx() method, so
 * message formatting/rendering can be checked in Rocket.Chat without grinding up the real game
 * event. Every button still respects that notifier's own config gates (enabled toggle,
 * thresholds, filters) - it exercises the real code path, not a shortcut around it.
 */
public class DebugNotificationPanel extends PluginPanel
{
	private static final int COOLDOWN_MILLIS = 10_000;

	private final ClientThread clientThread;

	public DebugNotificationPanel(
		ClientThread clientThread,
		DeathNotifier deathNotifier,
		LevelNotifier levelNotifier,
		LootNotifier lootNotifier,
		ClueNotifier clueNotifier,
		PetNotifier petNotifier,
		QuestNotifier questNotifier,
		SlayerNotifier slayerNotifier,
		BossNotifier bossNotifier,
		CollectionLogNotifier collectionLogNotifier,
		CombatAchievementNotifier combatAchievementNotifier,
		DiaryNotifier diaryNotifier,
		ChatPatternNotifier chatPatternNotifier,
		GrandExchangeNotifier grandExchangeNotifier)
	{
		this.clientThread = clientThread;

		JLabel header = new JLabel("<html>Fires a test event through each notifier's real "
			+ "code path. Still subject to that notifier's own config (enabled toggle, "
			+ "thresholds, filters) - enable what you want to test first. Each button is "
			+ "limited to once every " + (COOLDOWN_MILLIS / 1000) + "s to avoid hammering your "
			+ "webhook.</html>");
		add(header);

		addButton("Test: Death", deathNotifier::sendTestNotification);
		addButton("Test: Level up", levelNotifier::sendTestNotification);
		addButton("Test: Loot", lootNotifier::sendTestNotification);
		addButton("Test: Clue reward", clueNotifier::sendTestNotification);
		addButton("Test: Pet", petNotifier::sendTestNotification);
		addButton("Test: Quest complete", questNotifier::sendTestNotification);
		addButton("Test: Slayer task", slayerNotifier::sendTestNotification);
		addButton("Test: Boss kill count", bossNotifier::sendTestNotification);
		addButton("Test: Collection log", collectionLogNotifier::sendTestNotification);
		addButton("Test: Combat achievement", combatAchievementNotifier::sendTestNotification);
		addButton("Test: Achievement diary", diaryNotifier::sendTestNotification);
		addButton("Test: Chat pattern", chatPatternNotifier::sendTestNotification);
		addButton("Test: GE trade", grandExchangeNotifier::sendTestNotification);
	}

	private void addButton(String label, Runnable action)
	{
		JButton button = new JButton(label);
		button.addActionListener(e -> fire(button, label, action));
		add(button);
	}

	/**
	 * Each button has its own independent cooldown, so testing several different notifiers back
	 * to back isn't blocked by one shared panel-wide timer - only clicking the *same* button
	 * again within the cooldown window is.
	 */
	private void fire(JButton button, String label, Runnable action)
	{
		// ItemManager/Client calls in the notifiers must run on the client thread, not the
		// Swing EDT this button listener fires on - see AGENTS.md's client-thread rule.
		clientThread.invoke(action);

		button.setEnabled(false);
		button.setText(label + " (cooldown)");

		Timer cooldown = new Timer(COOLDOWN_MILLIS, e ->
		{
			button.setEnabled(true);
			button.setText(label);
		});
		cooldown.setRepeats(false);
		cooldown.start();
	}

	public static BufferedImage createIcon()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = icon.createGraphics();
		g.setColor(new Color(0xE7, 0x4C, 0x3C));
		g.fillOval(0, 0, 16, 16);
		g.setColor(Color.WHITE);
		g.setFont(g.getFont().deriveFont(Font.BOLD, 10f));
		g.drawString("T", 5, 12);
		g.dispose();
		return icon;
	}
}
