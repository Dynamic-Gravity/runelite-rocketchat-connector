package space.covalent.rocketchat.notifiers;

import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import space.covalent.rocketchat.CombatAchievementTier;
import space.covalent.rocketchat.DiaryTier;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ChatMessageNotifiersTest
{
	@Mock RocketChatNotifierConfig config;
	@Mock WebhookClient webhookClient;

	// One @InjectMocks per notifier class
	@InjectMocks PetNotifier petNotifier;
	@InjectMocks QuestNotifier questNotifier;
	@InjectMocks SlayerNotifier slayerNotifier;
	@InjectMocks BossNotifier bossNotifier;
	@InjectMocks CollectionLogNotifier collectionLogNotifier;
	@InjectMocks CombatAchievementNotifier combatAchievementNotifier;
	@InjectMocks DiaryNotifier diaryNotifier;

	private static final String WEBHOOK_URL = "http://example.com/hooks/test";

	private static ChatMessage gameMessage(String text)
	{
		ChatMessage msg = new ChatMessage();
		msg.setType(ChatMessageType.GAMEMESSAGE);
		msg.setMessage(text);
		return msg;
	}

	// ── Pet ─────────────────────────────────────────────────────────────────

	@Test
	public void testPetNotifierFiresOnFunnyFeeling()
	{
		when(config.notifyOnPet()).thenReturn(true);
		when(config.webhookUrl()).thenReturn(WEBHOOK_URL);
		petNotifier.onChatMessage(gameMessage("You have a funny feeling like you're being followed."));
		verify(webhookClient).send(any(), any());
	}

	@Test
	public void testPetNotifierFiresOnWeirdFeeling()
	{
		when(config.notifyOnPet()).thenReturn(true);
		when(config.webhookUrl()).thenReturn(WEBHOOK_URL);
		petNotifier.onChatMessage(gameMessage("You feel something weird sneaking into your backpack."));
		verify(webhookClient).send(any(), any());
	}

	@Test
	public void testPetNotifierIgnoresOtherMessages()
	{
		when(config.notifyOnPet()).thenReturn(true);
		petNotifier.onChatMessage(gameMessage("You killed the dragon."));
		verify(webhookClient, never()).send(any(), any());
	}

	// ── Quest ────────────────────────────────────────────────────────────────

	@Test
	public void testQuestNotifierFiresOnCompletion()
	{
		when(config.notifyOnQuest()).thenReturn(true);
		when(config.webhookUrl()).thenReturn(WEBHOOK_URL);
		questNotifier.onChatMessage(gameMessage("Congratulations, you've completed Dragon Slayer II!"));

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		assertTrue(captor.getValue().getAttachments().get(0).getText().contains("Dragon Slayer II"));
	}

	// ── Slayer ───────────────────────────────────────────────────────────────

	@Test
	public void testSlayerNotifierFiresOnTaskComplete()
	{
		when(config.notifyOnSlayer()).thenReturn(true);
		when(config.webhookUrl()).thenReturn(WEBHOOK_URL);
		slayerNotifier.onChatMessage(gameMessage("You have completed your task! You killed 150 Abyssal demons."));
		verify(webhookClient).send(any(), any());
	}

	// ── Boss ─────────────────────────────────────────────────────────────────

	@Test
	public void testBossNotifierFiresOnKillCount()
	{
		when(config.notifyOnBoss()).thenReturn(true);
		when(config.bossPersonalBestOnly()).thenReturn(false);
		when(config.bossKillCountInterval()).thenReturn(1);
		when(config.webhookUrl()).thenReturn(WEBHOOK_URL);
		bossNotifier.onChatMessage(gameMessage("Your Zulrah kill count is: 50."));
		verify(webhookClient).send(any(), any());
	}

	@Test
	public void testBossNotifierFiresOnPersonalBest()
	{
		when(config.notifyOnBoss()).thenReturn(true);
		when(config.bossPersonalBestOnly()).thenReturn(false);
		when(config.webhookUrl()).thenReturn(WEBHOOK_URL);
		bossNotifier.onChatMessage(gameMessage("Fight duration: 1:34. Personal best: 1:34."));
		verify(webhookClient).send(any(), any());
	}

	@Test
	public void testBossNotifierSkipsNonPbWhenPbOnly()
	{
		when(config.notifyOnBoss()).thenReturn(true);
		when(config.bossPersonalBestOnly()).thenReturn(true);
		// Kill count message — bossPersonalBestOnly causes early return, send never called
		bossNotifier.onChatMessage(gameMessage("Your Zulrah kill count is: 49."));
		verify(webhookClient, never()).send(any(), any());
	}

	// ── Collection Log ────────────────────────────────────────────────────────

	@Test
	public void testCollectionLogNotifierFires()
	{
		when(config.notifyOnCollectionLog()).thenReturn(true);
		when(config.webhookUrl()).thenReturn(WEBHOOK_URL);
		collectionLogNotifier.onChatMessage(gameMessage("New item added to your collection log: Abyssal whip."));

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient).send(any(), captor.capture());
		assertTrue(captor.getValue().getAttachments().get(0).getText().contains("Abyssal whip"));
	}

	// ── Combat Achievements ───────────────────────────────────────────────────

	@Test
	public void testCombatAchievementFires()
	{
		when(config.notifyOnCombatAchievement()).thenReturn(true);
		when(config.minCombatAchievementTier()).thenReturn(CombatAchievementTier.EASY);
		when(config.webhookUrl()).thenReturn(WEBHOOK_URL);
		combatAchievementNotifier.onChatMessage(gameMessage(
			"Congratulations, you've completed a Hard combat achievement: Whiplash."));
		verify(webhookClient).send(any(), any());
	}

	@Test
	public void testCombatAchievementSkipsBelowMinTier()
	{
		when(config.notifyOnCombatAchievement()).thenReturn(true);
		when(config.minCombatAchievementTier()).thenReturn(CombatAchievementTier.ELITE);
		combatAchievementNotifier.onChatMessage(gameMessage(
			"Congratulations, you've completed an Easy combat achievement: Block and Roll."));
		verify(webhookClient, never()).send(any(), any());
	}

	// ── Achievement Diary ──────────────────────────────────────────────────────

	@Test
	public void testDiaryNotifierFires()
	{
		when(config.notifyOnDiary()).thenReturn(true);
		when(config.minDiaryTier()).thenReturn(DiaryTier.EASY);
		when(config.webhookUrl()).thenReturn(WEBHOOK_URL);
		diaryNotifier.onChatMessage(gameMessage(
			"Congratulations! You have completed all of the Varrock Hard Diary tasks."));
		verify(webhookClient).send(any(), any());
	}
}
