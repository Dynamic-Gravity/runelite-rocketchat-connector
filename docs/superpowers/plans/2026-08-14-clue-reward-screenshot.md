# Clue Reward Screenshot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in feature to `ClueNotifier` that uploads a cropped screenshot of the clue reward screen to Rocket.Chat as a separate message, alongside the existing item-card webhook notification.

**Architecture:** A `RocketChatFileUploadClient` handles Rocket.Chat's multipart REST file-upload API (`POST /api/v1/rooms.upload/:rid`), a separate mechanism from the existing JSON-only `WebhookClient`. `ClueNotifier` captures the reward screen via RuneLite's `DrawManager` on `WidgetLoaded`, crops it to the `TrailRewardscreen` widget bounds, and — once the corresponding `LootReceived` fires — hands the encoded PNG to the upload client. Capture/upload failures produce a whimsical in-channel fallback message; missing configuration fails silently.

**Tech Stack:** Java 11, RuneLite client API (`DrawManager`, `WidgetLoaded`, `gameval.InterfaceID`), OkHttp (multipart), JUnit 4 + Mockito + MockWebServer (existing test stack).

**Spec:** `docs/superpowers/specs/2026-08-14-clue-reward-screenshot-design.md`

## Global Constraints

- Java 11 compatible, `options.release.set(11)` — no newer language features.
- No reflection, no `Thread.sleep()`, no blocking network/disk IO on the client thread (AGENTS.md).
- Use `net.runelite.api.gameval.InterfaceID` constants — never hardcode widget/interface IDs.
- `@Inject OkHttpClient` / `@Inject Gson` only — never construct new instances, never add transitive deps to `build.gradle`.
- New third-party-server-facing config (`clueScreenshotEnabled`) must default to `false` and carry the standard warning: `"This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"`.
- Never rename existing config keys; new keys must be added to `RocketChatConnectorPlugin.CONFIG_KEYS` for the old→new config-group migration.
- Follow existing code style: tabs for indentation, package-private (no explicit `private`/`public`) `@Inject` fields on notifier/client classes, matching `WebhookClient`/`ClueNotifier`.

---

### Task 1: `RocketChatFileUploadClient`

**Files:**
- Create: `src/main/java/space/covalent/rocketchat/RocketChatFileUploadClient.java`
- Test: `src/test/java/space/covalent/rocketchat/RocketChatFileUploadClientTest.java`

**Interfaces:**
- Produces: `RocketChatFileUploadClient.upload(String serverOrigin, String roomId, String userId, String authToken, byte[] pngBytes) -> CompletableFuture<Void>` — completes normally on a 2xx response, completes exceptionally (cause is an `IOException`) on network failure or a non-2xx response. Used by Task 3.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/space/covalent/rocketchat/RocketChatFileUploadClientTest.java`:

```java
package space.covalent.rocketchat;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RocketChatFileUploadClientTest
{
	private MockWebServer server;
	private RocketChatFileUploadClient client;

	@Before
	public void setUp() throws IOException
	{
		server = new MockWebServer();
		server.start();

		client = new RocketChatFileUploadClient();
		client.okHttpClient = new OkHttpClient();
	}

	@After
	public void tearDown() throws IOException
	{
		server.shutdown();
	}

	@Test
	public void testUploadsFileWithAuthHeaders() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

		String origin = server.url("/").toString().replaceAll("/$", "");
		CompletableFuture<Void> future = client.upload(origin, "room1", "user1", "token1", new byte[]{1, 2, 3});
		future.get(2, TimeUnit.SECONDS);

		RecordedRequest request = server.takeRequest();
		assertEquals("POST", request.getMethod());
		assertEquals("/api/v1/rooms.upload/room1", request.getPath());
		assertEquals("token1", request.getHeader("X-Auth-Token"));
		assertEquals("user1", request.getHeader("X-User-Id"));
		assertTrue(request.getHeader("Content-Type").startsWith("multipart/form-data"));
	}

	@Test
	public void testFailsFutureOnNon2xxResponse() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(401).setBody("unauthorized"));

		String origin = server.url("/").toString().replaceAll("/$", "");
		CompletableFuture<Void> future = client.upload(origin, "room1", "user1", "bad-token", new byte[]{1});

		try
		{
			future.get(2, TimeUnit.SECONDS);
			fail("Expected future to complete exceptionally");
		}
		catch (ExecutionException e)
		{
			assertTrue(e.getCause() instanceof IOException);
		}
	}

	@Test
	public void testFailsFutureOnNetworkError() throws Exception
	{
		// Port 1 is a reserved port nothing listens on - connection refused immediately.
		CompletableFuture<Void> future = client.upload("http://127.0.0.1:1", "room1", "user1", "token1", new byte[]{1});

		try
		{
			future.get(2, TimeUnit.SECONDS);
			fail("Expected future to complete exceptionally");
		}
		catch (ExecutionException e)
		{
			assertTrue(e.getCause() instanceof IOException);
		}
	}
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests space.covalent.rocketchat.RocketChatFileUploadClientTest`
Expected: compilation failure — `RocketChatFileUploadClient` does not exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/space/covalent/rocketchat/RocketChatFileUploadClient.java`:

```java
package space.covalent.rocketchat;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@Singleton
public class RocketChatFileUploadClient
{
	@Inject
	OkHttpClient okHttpClient;

	public CompletableFuture<Void> upload(String serverOrigin, String roomId, String userId, String authToken, byte[] pngBytes)
	{
		CompletableFuture<Void> result = new CompletableFuture<>();

		RequestBody fileBody = RequestBody.create(MediaType.get("image/png"), pngBytes);
		RequestBody multipart = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("file", "clue-reward.png", fileBody)
			.build();

		Request request = new Request.Builder()
			.url(serverOrigin + "/api/v1/rooms.upload/" + roomId)
			.addHeader("X-Auth-Token", authToken)
			.addHeader("X-User-Id", userId)
			.post(multipart)
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Rocket.Chat screenshot upload failed", e);
				result.completeExceptionally(e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				log.debug("Rocket.Chat screenshot upload response: {}", response.code());
				boolean success = response.isSuccessful();
				response.close();
				if (success)
				{
					result.complete(null);
				}
				else
				{
					result.completeExceptionally(new IOException("Upload rejected: " + response.code()));
				}
			}
		});

		return result;
	}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests space.covalent.rocketchat.RocketChatFileUploadClientTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/RocketChatFileUploadClient.java src/test/java/space/covalent/rocketchat/RocketChatFileUploadClientTest.java
git commit -m "feat: add Rocket.Chat REST file upload client"
```

---

### Task 2: Config items

**Files:**
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatConnectorConfig.java`
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatConnectorPlugin.java`

**Interfaces:**
- Produces: `RocketChatConnectorConfig.clueScreenshotEnabled() -> boolean` (default `false`), `.rocketChatUserId() -> String` (default `""`), `.rocketChatAuthToken() -> String` (default `""`, secret), `.rocketChatRoomId() -> String` (default `""`). Used by Task 3.

No dedicated test file exists for `RocketChatConnectorConfig` or the `CONFIG_KEYS` migration list in this codebase (config interfaces here are compile-checked only, verified by the full test suite). This task is verified by a clean compile and full test run rather than a new test.

- [ ] **Step 1: Add the new config items**

In `src/main/java/space/covalent/rocketchat/RocketChatConnectorConfig.java`, add after `minClueTier()` (still inside the existing `clueSection`, i.e. before the `// Pet` comment):

```java
	@ConfigItem(
		keyName = "clueScreenshotEnabled",
		name = "Send reward screenshot",
		description = "Upload a screenshot of the clue reward screen to Rocket.Chat as a separate message",
		section = clueSection,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean clueScreenshotEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "rocketChatUserId",
		name = "Rocket.Chat user ID",
		description = "Personal access token user ID, used to upload the clue reward screenshot",
		section = clueSection
	)
	default String rocketChatUserId()
	{
		return "";
	}

	@ConfigItem(
		keyName = "rocketChatAuthToken",
		name = "Rocket.Chat auth token",
		description = "Personal access token auth token, used to upload the clue reward screenshot",
		section = clueSection,
		secret = true
	)
	default String rocketChatAuthToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "rocketChatRoomId",
		name = "Rocket.Chat room ID",
		description = "Target room/channel ID to upload the clue reward screenshot into",
		section = clueSection
	)
	default String rocketChatRoomId()
	{
		return "";
	}
```

- [ ] **Step 2: Register the new keys for config-group migration**

In `src/main/java/space/covalent/rocketchat/RocketChatConnectorPlugin.java`, update `CONFIG_KEYS`:

```java
	private static final String[] CONFIG_KEYS = {
		"webhookUrl", "notifyOnDeath", "notifyOnLevel", "minLevel", "notifyOnLoot", "minLootValue",
		"notifyOnClue", "minClueTier", "notifyOnPet", "notifyOnQuest", "notifyOnSlayer", "notifyOnBoss",
		"bossPersonalBestOnly", "bossKillCountInterval", "notifyOnCollectionLog", "notifyOnCombatAchievement",
		"minCombatAchievementTier", "notifyOnDiary", "minDiaryTier", "notifyOnChatPattern", "chatPattern",
		"notifyOnGrandExchange", "minGrandExchangeValue", "ironManMode",
		"clueScreenshotEnabled", "rocketChatUserId", "rocketChatAuthToken", "rocketChatRoomId"
	};
```

- [ ] **Step 3: Verify the build compiles and existing tests still pass**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all existing tests still pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/RocketChatConnectorConfig.java src/main/java/space/covalent/rocketchat/RocketChatConnectorPlugin.java
git commit -m "feat: add clue screenshot upload config items"
```

---

### Task 3: Screenshot capture and upload wiring in `ClueNotifier`

**Files:**
- Modify: `src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java`
- Modify: `src/test/java/space/covalent/rocketchat/notifiers/ClueNotifierTest.java`

**Interfaces:**
- Consumes: `RocketChatFileUploadClient.upload(String, String, String, String, byte[]) -> CompletableFuture<Void>` (Task 1); `RocketChatConnectorConfig.clueScreenshotEnabled()/.rocketChatUserId()/.rocketChatAuthToken()/.rocketChatRoomId()` (Task 2); `net.runelite.client.ui.DrawManager.requestNextFrameListener(Consumer<Image>)`; `net.runelite.api.Client.getWidget(int)`; `net.runelite.api.gameval.InterfaceID.TrailRewardscreen.UNIVERSE`.
- Produces: `ClueNotifier.onWidgetLoaded(WidgetLoaded event)` — new `@Subscribe` handler. No plugin-registration change needed: `RocketChatConnectorPlugin` already calls `eventBus.register(clueNotifier)` in `startUp()`, which picks up every `@Subscribe` method on the object, including this new one.

This is the coupled core of the feature — capture is meaningless without the upload path that consumes it, so both land in one task/commit, matching the spec's combined "Capture" + "Wiring" sections.

- [ ] **Step 1: Write the failing tests**

Add to `src/test/java/space/covalent/rocketchat/notifiers/ClueNotifierTest.java`. First, add these imports alongside the existing ones:

```java
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.DrawManager;
import okhttp3.OkHttpClient;
import org.junit.Before;
import space.covalent.rocketchat.RocketChatFileUploadClient;

import static org.mockito.Mockito.timeout;
```

Add these fields next to the existing `@Mock`/`@InjectMocks` fields:

```java
	@Mock Client client;
	@Mock DrawManager drawManager;
	@Mock RocketChatFileUploadClient fileUploadClient;
```

Add a `@Before` method (there is none currently) to give the notifier a real `OkHttpClient` — its thread pool is what runs the PNG-encode step, and a Mockito mock would return `null` from `.dispatcher()` unless deep-stubbed, which wouldn't actually execute anything:

```java
	@Before
	public void setUp()
	{
		notifier.okHttpClient = new OkHttpClient();
	}
```

Add a small helper and the new test methods at the end of the class, before the final closing `}`:

```java
	private void loadRewardScreen(Rectangle widgetBounds)
	{
		Widget rewardWidget = mock(Widget.class);
		when(rewardWidget.getBounds()).thenReturn(widgetBounds);
		when(client.getWidget(InterfaceID.TrailRewardscreen.UNIVERSE)).thenReturn(rewardWidget);

		WidgetLoaded widgetLoaded = new WidgetLoaded();
		widgetLoaded.setGroupId(InterfaceID.TrailRewardscreen.UNIVERSE >>> 16);
		notifier.onWidgetLoaded(widgetLoaded);
	}

	@SuppressWarnings("unchecked")
	private void deliverFrame(BufferedImage frame)
	{
		ArgumentCaptor<Consumer<Image>> captor = ArgumentCaptor.forClass(Consumer.class);
		verify(drawManager, atLeastOnce()).requestNextFrameListener(captor.capture());
		captor.getValue().accept(frame);
	}

	private LootReceived clueEasyLootEvent(int itemId)
	{
		return new LootReceived("Clue Scroll (Easy)", 0, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(itemId, 1)), 1, null);
	}

	@Test
	public void testIgnoresWidgetLoadedForOtherInterfaces()
	{
		WidgetLoaded widgetLoaded = new WidgetLoaded();
		widgetLoaded.setGroupId(999);
		notifier.onWidgetLoaded(widgetLoaded);

		verify(drawManager, never()).requestNextFrameListener(any());
	}

	@Test
	public void testUploadsScreenshotWhenCaptureAndConfigSucceed()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.clueScreenshotEnabled()).thenReturn(true);
		when(config.rocketChatRoomId()).thenReturn("room1");
		when(config.rocketChatUserId()).thenReturn("user1");
		when(config.rocketChatAuthToken()).thenReturn("token1");
		when(fileUploadClient.upload(any(), any(), any(), any(), any()))
			.thenReturn(CompletableFuture.completedFuture(null));

		loadRewardScreen(new Rectangle(0, 0, 10, 10));
		deliverFrame(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		notifier.onLootReceived(clueEasyLootEvent(itemId));

		verify(fileUploadClient, timeout(2000))
			.upload(eq("http://example.com"), eq("room1"), eq("user1"), eq("token1"), any());
		// item card was sent synchronously before the async screenshot chain runs - no race here
		verify(webhookClient, times(1)).send(any(), any());
	}

	@Test
	public void testNoUploadAttemptedWhenScreenshotDisabled()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.clueScreenshotEnabled()).thenReturn(false);

		loadRewardScreen(new Rectangle(0, 0, 10, 10));
		deliverFrame(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		notifier.onLootReceived(clueEasyLootEvent(itemId));

		verify(fileUploadClient, never()).upload(any(), any(), any(), any(), any());
		verify(webhookClient, times(1)).send(any(), any());
	}

	@Test
	public void testUploadSkippedSilentlyWhenCredentialsIncomplete()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.clueScreenshotEnabled()).thenReturn(true);
		when(config.rocketChatRoomId()).thenReturn("");
		when(config.rocketChatUserId()).thenReturn("user1");
		when(config.rocketChatAuthToken()).thenReturn("token1");

		loadRewardScreen(new Rectangle(0, 0, 10, 10));
		deliverFrame(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		notifier.onLootReceived(clueEasyLootEvent(itemId));

		verify(fileUploadClient, never()).upload(any(), any(), any(), any(), any());
		// exactly the one item-card message - no whimsical fallback either
		verify(webhookClient, timeout(500).times(1)).send(any(), any());
	}

	@Test
	public void testSneakingSuspicionMessageWhenRewardWidgetMissing()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.clueScreenshotEnabled()).thenReturn(true);
		when(config.rocketChatRoomId()).thenReturn("room1");
		when(config.rocketChatUserId()).thenReturn("user1");
		when(config.rocketChatAuthToken()).thenReturn("token1");
		// client.getWidget(...) not stubbed -> returns null, simulating the reward widget having closed already

		WidgetLoaded widgetLoaded = new WidgetLoaded();
		widgetLoaded.setGroupId(InterfaceID.TrailRewardscreen.UNIVERSE >>> 16);
		notifier.onWidgetLoaded(widgetLoaded);
		deliverFrame(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		notifier.onLootReceived(clueEasyLootEvent(itemId));

		verify(fileUploadClient, never()).upload(any(), any(), any(), any(), any());
		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient, timeout(2000).times(2)).send(any(), captor.capture());
		assertTrue(captor.getAllValues().stream()
			.anyMatch(p -> p.getText() != null && p.getText().contains("sneaking suspicion")));
	}

	@Test
	public void testSneakingSuspicionMessageWhenUploadFails()
	{
		when(config.notifyOnClue()).thenReturn(true);
		when(config.minClueTier()).thenReturn(ClueTier.EASY);
		when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
		when(config.clueScreenshotEnabled()).thenReturn(true);
		when(config.rocketChatRoomId()).thenReturn("room1");
		when(config.rocketChatUserId()).thenReturn("user1");
		when(config.rocketChatAuthToken()).thenReturn("token1");

		CompletableFuture<Void> failedUpload = new CompletableFuture<>();
		failedUpload.completeExceptionally(new java.io.IOException("Upload rejected: 401"));
		when(fileUploadClient.upload(any(), any(), any(), any(), any())).thenReturn(failedUpload);

		loadRewardScreen(new Rectangle(0, 0, 10, 10));
		deliverFrame(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

		int itemId = 4151;
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Abyssal whip");
		when(itemManager.getItemComposition(itemId)).thenReturn(comp);
		when(itemManager.getItemPrice(itemId)).thenReturn(2000000);

		notifier.onLootReceived(clueEasyLootEvent(itemId));

		ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
		verify(webhookClient, timeout(2000).times(2)).send(any(), captor.capture());
		assertTrue(captor.getAllValues().stream()
			.anyMatch(p -> p.getText() != null && p.getText().contains("sneaking suspicion")));
	}
```

Also add this static import next to the existing ones:

```java
import static org.mockito.Mockito.atLeastOnce;
```

(`assertTrue` is already imported in the file from the existing tests — no need to re-add it; `atLeastOnce` is the only new static import this task needs.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests space.covalent.rocketchat.notifiers.ClueNotifierTest`
Expected: compilation failure — `onWidgetLoaded`, `client`, `drawManager`, `fileUploadClient`, `okHttpClient` don't exist on `ClueNotifier` yet.

- [ ] **Step 3: Implement capture and upload wiring**

In `src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java`, add these imports:

```java
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.eventbus.Subscribe;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import space.covalent.rocketchat.RocketChatFileUploadClient;
```

(`net.runelite.client.eventbus.Subscribe` is already imported — skip re-adding it.)

Add `@Slf4j` above the class declaration (the class has no logger yet):

```java
@Slf4j
@Singleton
public class ClueNotifier
```

Add these fields alongside the existing `@Inject` fields:

```java
	@Inject
	Client client;

	@Inject
	DrawManager drawManager;

	@Inject
	OkHttpClient okHttpClient;

	@Inject
	RocketChatFileUploadClient fileUploadClient;

	private static final int REWARD_SCREEN_GROUP = InterfaceID.TrailRewardscreen.UNIVERSE >>> 16;

	private volatile CompletableFuture<byte[]> pendingScreenshot;
```

Add the new `@Subscribe` handler (place it above `onLootReceived`):

```java
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != REWARD_SCREEN_GROUP)
		{
			return;
		}

		CompletableFuture<byte[]> future = new CompletableFuture<>();
		future.orTimeout(5, TimeUnit.SECONDS);
		pendingScreenshot = future;

		drawManager.requestNextFrameListener(image -> captureAndCrop(image, future));
	}

	private void captureAndCrop(Image image, CompletableFuture<byte[]> future)
	{
		Widget rewardWidget = client.getWidget(InterfaceID.TrailRewardscreen.UNIVERSE);
		if (rewardWidget == null)
		{
			future.completeExceptionally(new IllegalStateException("Reward widget not present"));
			return;
		}

		Rectangle bounds = rewardWidget.getBounds();
		BufferedImage cropped;
		try
		{
			cropped = ((BufferedImage) image).getSubimage(bounds.x, bounds.y, bounds.width, bounds.height);
		}
		catch (RuntimeException e)
		{
			future.completeExceptionally(e);
			return;
		}

		okHttpClient.dispatcher().executorService().execute(() -> encodeToPng(cropped, future));
	}

	private void encodeToPng(BufferedImage image, CompletableFuture<byte[]> future)
	{
		try
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			future.complete(out.toByteArray());
		}
		catch (IOException e)
		{
			future.completeExceptionally(e);
		}
	}
```

At the end of `onLootReceived`, after the existing `sendCard(tierName, wikiSource, bestStack, bestComp, bestPrice);` line, add:

```java
		if (config.clueScreenshotEnabled())
		{
			CompletableFuture<byte[]> screenshot = pendingScreenshot;
			if (screenshot != null)
			{
				screenshot.whenComplete((bytes, captureError) -> handleScreenshot(bytes, captureError));
			}
		}
```

Add the new private helpers (place near the bottom of the class, alongside `formatGp`/`formatRarityLine`):

```java
	private void handleScreenshot(byte[] bytes, Throwable captureError)
	{
		if (captureError != null)
		{
			sendSneakingSuspicion();
			return;
		}

		if (!hasUploadConfig())
		{
			log.debug("Clue screenshot enabled but Rocket.Chat upload credentials are incomplete");
			return;
		}

		String origin = serverOrigin(config.webhookUrl());
		if (origin == null)
		{
			log.debug("Could not determine Rocket.Chat server origin from webhook URL");
			return;
		}

		fileUploadClient.upload(origin, config.rocketChatRoomId(), config.rocketChatUserId(), config.rocketChatAuthToken(), bytes)
			.whenComplete((v, uploadError) ->
			{
				if (uploadError != null)
				{
					sendSneakingSuspicion();
				}
			});
	}

	private boolean hasUploadConfig()
	{
		return !config.rocketChatRoomId().isEmpty()
			&& !config.rocketChatUserId().isEmpty()
			&& !config.rocketChatAuthToken().isEmpty();
	}

	private void sendSneakingSuspicion()
	{
		webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
			.text("🕵️ You have a sneaking suspicion this reward should've come with a screenshot...")
			.build());
	}

	private static String serverOrigin(String webhookUrl)
	{
		HttpUrl url = HttpUrl.parse(webhookUrl);
		if (url == null)
		{
			return null;
		}
		boolean defaultPort = url.port() == HttpUrl.defaultPort(url.scheme());
		return url.scheme() + "://" + url.host() + (defaultPort ? "" : ":" + url.port());
	}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests space.covalent.rocketchat.notifiers.ClueNotifierTest`
Expected: PASS (all existing tests plus the 6 new ones)

- [ ] **Step 5: Run the full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, no regressions in other notifiers/tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java src/test/java/space/covalent/rocketchat/notifiers/ClueNotifierTest.java
git commit -m "feat: capture and upload clue reward screenshot to Rocket.Chat"
```

---

## Manual verification (after all tasks)

Per AGENTS.md, this plan's automated tests cannot verify real in-game behavior (RuneLite widget capture, actual Rocket.Chat delivery). After implementation:

1. Offer to run `./gradlew run` from the plugin root to launch a dev RuneLite client.
2. Point the user to the "Using Jagex Accounts" instructions (https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) to log into the dev client.
3. Ask the user to configure a real `webhookUrl`, enable "Send reward screenshot", and fill in a real Rocket.Chat personal-access-token `rocketChatUserId`/`rocketChatAuthToken` and a `rocketChatRoomId` they own.
4. Ask them to complete a clue scroll (any tier ≥ their configured `minClueTier`) and confirm: the existing item-card message arrives, and a second message with the cropped reward-screen screenshot arrives in the same channel.
5. Edge cases worth exercising: minimize/occlude the client right as the reward screen opens (should trigger the "sneaking suspicion" fallback, not a hang); leave `rocketChatRoomId` blank with the feature enabled (should send only the item card, no fallback message, matches `log.debug` in the RuneLite log).
6. Wait for the user to confirm before considering this complete.
