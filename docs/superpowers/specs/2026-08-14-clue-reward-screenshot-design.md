# Clue Reward Screenshot — Design Spec

**Date:** 2026-08-14
**Status:** Approved, pending implementation

---

## Overview

`ClueNotifier` gains an opt-in feature: when a clue casket is opened, capture a screenshot of the reward screen (`TrailRewardscreen` widget, cropped) and upload it to Rocket.Chat as a separate message, in addition to the existing item-card webhook message.

Rocket.Chat's incoming-webhook JSON payload (`WebhookClient` / `RocketChatPayload`) has no way to carry binary image data — `image_url` is a link only. Actually delivering the screenshot bytes requires a different mechanism: Rocket.Chat's REST API file upload endpoint (`POST /api/v1/rooms.upload/:rid`), authenticated with a personal access token. This is a materially different delivery path from the existing webhook, with its own credentials and its own message identity (posts as the token's user, not the webhook's bot).

---

## Capture

**Trigger:** subscribe `ClueNotifier` to `WidgetLoaded`, filtering on the reward-screen widget group (`InterfaceID.TrailRewardscreen.UNIVERSE >>> 16`, i.e. group `0x0049`).

RuneLite's own clue-scroll loot detection reads item data from this same widget when it loads, so `WidgetLoaded` for this group always fires *before* `LootReceived`. This ordering means the capture is always kicked off before `onLootReceived` runs — no race to guard against on the trigger side.

On match:

```java
drawManager.requestNextFrameListener(image -> {
    Widget rewardWidget = client.getWidget(InterfaceID.TrailRewardscreen.UNIVERSE);
    if (rewardWidget == null)
    {
        return;
    }
    Rectangle bounds = rewardWidget.getBounds();
    BufferedImage cropped = ((BufferedImage) image).getSubimage(bounds.x, bounds.y, bounds.width, bounds.height);
    okHttpClient.dispatcher().executorService().execute(() -> encodeAndUpload(cropped));
});
```

- `requestNextFrameListener` callback fires on the **client thread**. Only the cheap part happens there: reading widget bounds and taking a subimage view. PNG encoding and the network call are hopped onto the existing OkHttp thread pool (`okHttpClient.dispatcher().executorService()`) — no new executor to create or shut down, consistent with AGENTS.md's "never block the client thread" and "use the OkHttp thread pool" rules.
- The pending capture is stored as `volatile CompletableFuture<byte[]> pendingScreenshot`, completed with the encoded PNG bytes (or completed exceptionally on any failure). A fresh clue overwrites any prior in-flight future — only the latest clue's screenshot matters.
- The future gets `.orTimeout(5, TimeUnit.SECONDS)` applied at creation, so a frame that never renders (client minimized, window occluded) doesn't hang the flow indefinitely.

---

## Delivery

New class, parallel to `WebhookClient`:

**File:** `src/main/java/space/covalent/rocketchat/RocketChatFileUploadClient.java`

```java
@Slf4j
@Singleton
public class RocketChatFileUploadClient
{
    @Inject OkHttpClient okHttpClient;

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

Returns a `CompletableFuture<Void>` rather than taking a callback, so the caller can chain the whimsical-fallback decision in one place (see Wiring below) instead of duplicating it inside two different callback shapes.

`serverOrigin` (scheme + host[:port]) is parsed from the existing `webhookUrl` config value rather than asking for a separate field — one fewer config item, and the webhook and the upload target are always the same Rocket.Chat server in practice.

---

## Wiring into `ClueNotifier`

`onLootReceived` keeps its existing best-item selection and webhook send unchanged. After that, if screenshots are enabled:

```java
if (config.clueScreenshotEnabled())
{
    CompletableFuture<byte[]> screenshot = pendingScreenshot;
    if (screenshot != null)
    {
        screenshot.whenComplete((bytes, captureError) -> {
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
            String origin = serverOriginFrom(config.webhookUrl());
            fileUploadClient.upload(origin, config.rocketChatRoomId(), config.rocketChatUserId(), config.rocketChatAuthToken(), bytes)
                .whenComplete((v, uploadError) -> {
                    if (uploadError != null)
                    {
                        sendSneakingSuspicion();
                    }
                });
        });
    }
}
```

- `hasUploadConfig()` — `true` only if `rocketChatRoomId`/`rocketChatUserId`/`rocketChatAuthToken` are all non-empty. Missing config is a **silent** `log.debug` — a setup gap only the user can see and fix in their own RuneLite settings, not worth surfacing in the channel.
- Two distinct failure points both funnel into the same `sendSneakingSuspicion()` call: capture timeout (`captureError != null`, from the `pendingScreenshot` future) and upload failure (`uploadError != null`, from `RocketChatFileUploadClient.upload()`'s returned future — network failure or non-2xx response). `sendSneakingSuspicion()` sends a plain text-only message through the existing `WebhookClient`:

  ```java
  webhookClient.send(config.webhookUrl(),
      RocketChatPayload.builder()
          .text("🕵️ You have a sneaking suspicion this reward should've come with a screenshot...")
          .build());
  ```

  This only fires for actual runtime hiccups (capture timeout, upload rejected/network failure) — not for the disabled or unconfigured cases.

---

## Config

New items in the existing "Clue Scrolls" section:

```java
@ConfigItem(
    keyName = "clueScreenshotEnabled",
    name = "Send reward screenshot",
    description = "Upload a screenshot of the clue reward screen to Rocket.Chat as a separate message",
    section = clueSection,
    warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
)
default boolean clueScreenshotEnabled() { return false; }

@ConfigItem(
    keyName = "rocketChatUserId",
    name = "Rocket.Chat user ID",
    description = "Personal access token user ID, used to upload the clue reward screenshot",
    section = clueSection
)
default String rocketChatUserId() { return ""; }

@ConfigItem(
    keyName = "rocketChatAuthToken",
    name = "Rocket.Chat auth token",
    description = "Personal access token auth token, used to upload the clue reward screenshot",
    section = clueSection,
    secret = true
)
default String rocketChatAuthToken() { return ""; }

@ConfigItem(
    keyName = "rocketChatRoomId",
    name = "Rocket.Chat room ID",
    description = "Target room/channel ID to upload the clue reward screenshot into",
    section = clueSection
)
default String rocketChatRoomId() { return ""; }
```

`clueScreenshotEnabled` defaults off with the standard third-party-server warning per AGENTS.md. `rocketChatAuthToken` uses `secret = true` so RuneLite masks it in the settings UI.

Add the four new keys to `RocketChatConnectorPlugin.CONFIG_KEYS` for the existing config-group migration path.

---

## Known limitation

The screenshot arrives as a **separate message**, posted under the personal-access-token user's identity — not visually attached to the existing item-card message from the webhook's bot identity, since it goes through a different Rocket.Chat API entirely. Accepted tradeoff (confirmed with user) in exchange for the image landing natively in Rocket.Chat rather than requiring a second external image host.

---

## Files Changed

| File | Change |
|------|--------|
| `RocketChatFileUploadClient.java` | New — multipart upload to Rocket.Chat REST API |
| `RocketChatConnectorConfig.java` | New `clueScreenshotEnabled`, `rocketChatUserId`, `rocketChatAuthToken`, `rocketChatRoomId` |
| `RocketChatConnectorPlugin.java` | Add new keys to `CONFIG_KEYS` migration list |
| `ClueNotifier.java` | `WidgetLoaded` subscription, capture/crop/timeout, upload wiring, whimsical fallback |

---

## Testing

- `RocketChatFileUploadClientTest` (new, using the existing `mockwebserver` test dependency): asserts request URL (`{origin}/api/v1/rooms.upload/{roomId}`), `X-Auth-Token`/`X-User-Id` headers, and multipart body shape.
- `ClueNotifierTest`: extend for the new behavior —
  - upload attempted with correct args when `clueScreenshotEnabled()` is true, upload config complete, and the screenshot future resolves;
  - upload skipped (no call, no channel message) when disabled;
  - upload skipped silently (`log.debug` only, no channel message) when enabled but upload config incomplete;
  - whimsical fallback message sent (and *only* the fallback, not a second real upload attempt) when the screenshot future completes exceptionally (timeout) or the upload client reports failure;
  - existing item-card webhook send is unaffected in all of the above.
- Actual frame capture via `DrawManager`/AWT rendering is not unit-testable — only the plumbing around it (crop math can still be tested in isolation if `getBounds()` and `getSubimage()` are exercised against a fixed `BufferedImage`/`Rectangle`, but this is lower priority than the config/wiring tests above).
