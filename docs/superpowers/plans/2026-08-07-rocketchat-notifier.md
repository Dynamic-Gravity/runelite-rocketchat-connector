# Rocket.Chat Notifier Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a RuneLite plugin that sends game event notifications to a self-hosted Rocket.Chat channel via incoming webhook, covering the same feature surface as the Dink plugin.

**Architecture:** Each notification type is a separate `@Singleton` class registered on RuneLite's `EventBus`; all share a single `WebhookClient` that POSTs JSON to the configured URL on the OkHttp thread pool. The plugin class manages lifecycle — registering notifiers on `startUp()` and unregistering on `shutDown()`. Config drives which notifications are active; if the webhook URL is empty, no calls are made.

**Tech Stack:** Java 11, RuneLite plugin API, OkHttp (injected), Gson (injected), Lombok, JUnit 4 + Mockito.

## Global Constraints

- Java 11 only — no records, text blocks, switch expressions
- Package: `space.covalent.rocketchat`
- Config group: `"rocketchat-notifier"` (exact, not abbreviated)
- All HTTP via `@Inject OkHttpClient` — never create your own
- All JSON via `@Inject Gson` — never create your own
- Never call OkHttp `.execute()` on the client thread — always `.enqueue()`
- Never call `client.*` from the OkHttp callback thread — use `clientThread.invoke()`
- No reflection in plugin code; Mockito reflection in tests is fine
- No `log.info` for per-event logging — use `log.debug`
- Use `net.runelite.api.gameval` constants (ItemID, etc.) — no magic numbers
- Per AGENTS.md: webhook URL config must carry the third-party warning field
- Per AGENTS.md: the notification-to-third-party toggle must be **disabled by default**
- License: BSD-2

---

## File Structure

```
src/main/java/space/covalent/rocketchat/
├── RocketChatNotifierPlugin.java       Main plugin; registers/unregisters all notifiers
├── RocketChatNotifierConfig.java       Config: webhook URL + one section per notifier
├── WebhookClient.java                  Builds JSON + POSTs via OkHttp enqueue()
├── RocketChatPayload.java              Gson model: {text, attachments[{title,text,color,thumb_url,fields[]}]}
└── notifiers/
    ├── DeathNotifier.java              ActorDeath → local player only
    ├── LevelNotifier.java              StatChanged → tracks last level, fires on increase
    ├── LootNotifier.java               LootReceived → filter by min value, uses ItemManager
    ├── ClueNotifier.java               LootReceived where name matches clue tier → filter by tier
    ├── PetNotifier.java                ChatMessage "funny feeling" pattern
    ├── QuestNotifier.java              ChatMessage quest-complete pattern
    ├── SlayerNotifier.java             ChatMessage slayer-task-complete pattern
    ├── BossNotifier.java               ChatMessage kill-count + PB patterns
    ├── CollectionLogNotifier.java      ChatMessage new-collection-log-entry pattern
    ├── CombatAchievementNotifier.java  ChatMessage CA-complete pattern, tier threshold
    ├── DiaryNotifier.java              ChatMessage diary-complete pattern, tier threshold
    ├── GrandExchangeNotifier.java      GrandExchangeOfferChanged BOUGHT/SOLD states
    └── ChatPatternNotifier.java        ChatMessage matched against user regex

src/test/java/space/covalent/rocketchat/
├── WebhookClientTest.java
└── notifiers/
    ├── DeathNotifierTest.java
    ├── LevelNotifierTest.java
    ├── LootNotifierTest.java
    ├── ChatMessageNotifiersTest.java   Covers all ChatMessage-based notifiers
    └── GrandExchangeNotifierTest.java
```

---

## Task 1: Project Scaffold & Rename

**Goal:** Delete template code; establish the correct package, plugin descriptor, build config, and `.gitignore` before any feature code lands.

**Files:**
- Modify: `.gitignore`
- Modify: `settings.gradle`
- Modify: `build.gradle`
- Modify: `runelite-plugin.properties`
- Delete: `src/main/java/com/example/ExamplePlugin.java`
- Delete: `src/main/java/com/example/ExampleConfig.java`
- Delete: `src/test/java/com/example/ExamplePluginTest.java`
- Create: `src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java`
- Create: `src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java`
- Create: `src/test/java/space/covalent/rocketchat/RocketChatNotifierPluginTest.java`

**Interfaces:**
- Produces: `RocketChatNotifierPlugin` (empty startUp/shutDown), `RocketChatNotifierConfig` (webhookUrl only), launcher test class

- [ ] **Step 1: Fix `.gitignore`**

Add `bin/` to `.gitignore` (the template leaves compiled `.class` files in `bin/` which must not be committed):

```
.gradle
build
bin
.idea/
.project
.settings/
.classpath
nbactions.xml
nb-configuration.xml
nbproject/
```

- [ ] **Step 2: Update `settings.gradle`**

```groovy
rootProject.name = 'rocketchat-notifier'
```

- [ ] **Step 3: Update `build.gradle`**

```groovy
plugins {
    id 'java'
}

repositories {
    mavenLocal()
    maven {
        url = 'https://repo.runelite.net'
        content {
            includeGroupByRegex("net\\.runelite.*")
        }
    }
    mavenCentral()
}

def runeLiteVersion = 'latest.release'
def pluginMainClass = 'space.covalent.rocketchat.RocketChatNotifierPluginTest'

dependencies {
    compileOnly group: 'net.runelite', name: 'client', version: runeLiteVersion

    compileOnly 'org.projectlombok:lombok:1.18.30'
    annotationProcessor 'org.projectlombok:lombok:1.18.30'

    testImplementation 'junit:junit:4.12'
    testImplementation 'org.mockito:mockito-core:4.8.0'
    testImplementation group: 'net.runelite', name: 'client', version: runeLiteVersion
    testImplementation group: 'net.runelite', name: 'jshell', version: runeLiteVersion
}

group = 'space.covalent'

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release.set(11)
}

tasks.register('run', JavaExec) {
    classpath = sourceSets.test.runtimeClasspath
    mainClass = pluginMainClass

    jvmArgs "-ea"
    args "--developer-mode", "--debug"
}

tasks.register('shadowJar', Jar) {
    dependsOn configurations.testRuntimeClasspath
    manifest {
        attributes('Main-Class': pluginMainClass, 'Multi-Release': true)
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from sourceSets.main.output
    from sourceSets.test.output
    from {
        configurations.testRuntimeClasspath.collect { file ->
            file.isDirectory() ? file : zipTree(file)
        }
    }

    exclude 'META-INF/INDEX.LIST'
    exclude 'META-INF/*.SF'
    exclude 'META-INF/*.DSA'
    exclude 'META-INF/*.RSA'
    exclude '**/module-info.class'

    group = BasePlugin.BUILD_GROUP
    archiveClassifier.set('shadow')
    archiveFileName.set("${rootProject.name}-${project.version}-all.jar")
}
```

- [ ] **Step 4: Update `runelite-plugin.properties`**

```properties
displayName=Rocket.Chat Notifier
author=Luke Brown
description=Send game event notifications to a self-hosted Rocket.Chat channel via webhook
tags=notification,webhook,rocketchat,discord
version=1.0.0
plugins=space.covalent.rocketchat.RocketChatNotifierPlugin
build=standard
```

- [ ] **Step 5: Delete template source files**

```bash
rm src/main/java/com/example/ExamplePlugin.java
rm src/main/java/com/example/ExampleConfig.java
rm src/test/java/com/example/ExamplePluginTest.java
rmdir src/main/java/com/example
rmdir src/test/java/com/example
```

- [ ] **Step 6: Create `RocketChatNotifierConfig.java`**

Create `src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java`:

```java
package space.covalent.rocketchat;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("rocketchat-notifier")
public interface RocketChatNotifierConfig extends Config
{
    @ConfigSection(
        name = "Webhook",
        description = "Rocket.Chat incoming webhook settings",
        position = 0
    )
    String webhookSection = "webhook";

    @ConfigItem(
        keyName = "webhookUrl",
        name = "Webhook URL",
        description = "Rocket.Chat incoming webhook URL",
        section = webhookSection,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
    )
    default String webhookUrl()
    {
        return "";
    }
}
```

- [ ] **Step 7: Create `RocketChatNotifierPlugin.java`**

Create `src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java`:

```java
package space.covalent.rocketchat;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
    name = "Rocket.Chat Notifier",
    description = "Send game event notifications to a Rocket.Chat channel via webhook",
    tags = {"notification", "webhook", "rocketchat"}
)
public class RocketChatNotifierPlugin extends Plugin
{
    @Inject
    private RocketChatNotifierConfig config;

    @Override
    protected void startUp()
    {
        log.debug("Rocket.Chat Notifier started");
    }

    @Override
    protected void shutDown()
    {
        log.debug("Rocket.Chat Notifier stopped");
    }

    @Provides
    RocketChatNotifierConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(RocketChatNotifierConfig.class);
    }
}
```

- [ ] **Step 8: Create launcher test**

Create `src/test/java/space/covalent/rocketchat/RocketChatNotifierPluginTest.java`:

```java
package space.covalent.rocketchat;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RocketChatNotifierPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(RocketChatNotifierPlugin.class);
        RuneLite.main(args);
    }
}
```

- [ ] **Step 9: Verify build compiles**

```bash
./gradlew compileJava compileTestJava
```

Expected: `BUILD SUCCESSFUL` with no errors.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "chore: rename template to rocketchat-notifier, set up package"
```

---

## Task 2: Core HTTP Infrastructure

**Goal:** `RocketChatPayload` (JSON model) and `WebhookClient` (sends it) are the only shared dependencies all notifiers use. Build and test them in isolation so all later tasks can assume they work.

**Files:**
- Create: `src/main/java/space/covalent/rocketchat/RocketChatPayload.java`
- Create: `src/main/java/space/covalent/rocketchat/WebhookClient.java`
- Create: `src/test/java/space/covalent/rocketchat/WebhookClientTest.java`

**Interfaces:**
- Produces:
  - `RocketChatPayload.builder()` → `RocketChatPayload` with `text` and `List<Attachment>`
  - `RocketChatPayload.Attachment.builder()` → `Attachment` with `title`, `text`, `color`, `thumb_url`, `title_link`, `List<Field>`
  - `RocketChatPayload.Field.builder()` → `Field` with `title`, `value`, `short_` (serialized as `"short"`)
  - `WebhookClient.send(String webhookUrl, RocketChatPayload payload)` — fire-and-forget, returns void

- [ ] **Step 1: Create `RocketChatPayload.java`**

Create `src/main/java/space/covalent/rocketchat/RocketChatPayload.java`:

```java
package space.covalent.rocketchat;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RocketChatPayload
{
    String text;
    List<Attachment> attachments;

    @Value
    @Builder
    public static class Attachment
    {
        String title;
        @SerializedName("title_link")
        String titleLink;
        String text;
        String color;
        @SerializedName("thumb_url")
        String thumbUrl;
        @SerializedName("image_url")
        String imageUrl;
        List<Field> fields;
    }

    @Value
    @Builder
    public static class Field
    {
        String title;
        String value;
        @SerializedName("short")
        boolean short_;
    }
}
```

- [ ] **Step 2: Create `WebhookClient.java`**

Create `src/main/java/space/covalent/rocketchat/WebhookClient.java`:

```java
package space.covalent.rocketchat;

import com.google.gson.Gson;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;

@Slf4j
@Singleton
public class WebhookClient
{
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Inject
    OkHttpClient okHttpClient;

    @Inject
    Gson gson;

    public void send(String webhookUrl, RocketChatPayload payload)
    {
        if (webhookUrl == null || webhookUrl.isEmpty())
        {
            return;
        }

        String json = gson.toJson(payload);
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
            .url(webhookUrl)
            .post(body)
            .build();

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.debug("Rocket.Chat webhook request failed", e);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                log.debug("Rocket.Chat webhook response: {}", response.code());
                response.close();
            }
        });
    }
}
```

- [ ] **Step 3: Write the failing test**

Create `src/test/java/space/covalent/rocketchat/WebhookClientTest.java`:

```java
package space.covalent.rocketchat;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WebhookClientTest
{
    private MockWebServer server;
    private WebhookClient client;

    @Before
    public void setUp() throws IOException
    {
        server = new MockWebServer();
        server.start();

        client = new WebhookClient();
        client.okHttpClient = new OkHttpClient();
        client.gson = new Gson();
    }

    @After
    public void tearDown() throws IOException
    {
        server.shutdown();
    }

    @Test
    public void testSendsJsonPost() throws InterruptedException
    {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        RocketChatPayload payload = RocketChatPayload.builder()
            .text("Test message")
            .attachments(Collections.emptyList())
            .build();

        client.send(server.url("/hooks/test").toString(), payload);

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertTrue(request.getBody().readUtf8().contains("Test message"));
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"));
    }

    @Test
    public void testSkipsWhenUrlEmpty()
    {
        RocketChatPayload payload = RocketChatPayload.builder()
            .text("Should not send")
            .build();

        client.send("", payload);

        assertEquals(0, server.getRequestCount());
    }
}
```

Note: `MockWebServer` is included transitively via the `net.runelite:client` test dependency — no extra `build.gradle` entry required. If it is missing at compile time, add `testImplementation 'com.squareup.okhttp3:mockwebserver:4.9.3'` to `build.gradle`.

- [ ] **Step 4: Run test — expect failure**

```bash
./gradlew test --tests "space.covalent.rocketchat.WebhookClientTest"
```

Expected: compile error on `WebhookClient` fields not being accessible, or test runner error. If `MockWebServer` is missing, add the dependency and re-run.

- [ ] **Step 5: Make fields package-private so tests can inject**

The test sets `client.okHttpClient` and `client.gson` directly (package-private assignment). The `@Inject` annotation allows Guice to set them at runtime; the package-private visibility allows the test in the same package to set them without reflection.

The `WebhookClient.java` already uses package-private fields (no `private` modifier) — verify the field declarations match:

```java
@Inject
OkHttpClient okHttpClient;   // no private — package-visible

@Inject
Gson gson;                    // no private — package-visible
```

- [ ] **Step 6: Run test — expect pass**

```bash
./gradlew test --tests "space.covalent.rocketchat.WebhookClientTest"
```

Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/RocketChatPayload.java \
        src/main/java/space/covalent/rocketchat/WebhookClient.java \
        src/test/java/space/covalent/rocketchat/WebhookClientTest.java \
        build.gradle
git commit -m "feat: add RocketChatPayload model and WebhookClient"
```

---

## Task 3: Death Notifier

**Goal:** When the local player dies, send a Rocket.Chat message with the player name, combat level, and world location.

**Files:**
- Create: `src/main/java/space/covalent/rocketchat/notifiers/DeathNotifier.java`
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java` (add death section)
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java` (register notifier)
- Create: `src/test/java/space/covalent/rocketchat/notifiers/DeathNotifierTest.java`

**Interfaces:**
- Consumes: `WebhookClient.send(String, RocketChatPayload)`, `RocketChatNotifierConfig.webhookUrl()`, `RocketChatNotifierConfig.notifyOnDeath()`
- Produces: `DeathNotifier` (registered on EventBus; subscribes to `ActorDeath`)

- [ ] **Step 1: Add death config to `RocketChatNotifierConfig.java`**

```java
@ConfigSection(
    name = "Death",
    description = "Notifications when you die",
    position = 1
)
String deathSection = "death";

@ConfigItem(
    keyName = "notifyOnDeath",
    name = "Notify on death",
    description = "Send a Rocket.Chat message when you die",
    section = deathSection
)
default boolean notifyOnDeath()
{
    return false;
}
```

- [ ] **Step 2: Write the failing test**

Create directory `src/test/java/space/covalent/rocketchat/notifiers/`.

Create `src/test/java/space/covalent/rocketchat/notifiers/DeathNotifierTest.java`:

```java
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
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

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

    @Before
    public void setUp()
    {
        when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
        when(config.notifyOnDeath()).thenReturn(true);
    }

    @Test
    public void testSendsNotificationWhenLocalPlayerDies()
    {
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
        when(client.getLocalPlayer()).thenReturn(localPlayer);

        ActorDeath event = new ActorDeath(localPlayer);
        notifier.onActorDeath(event);

        verify(webhookClient, never()).send(any(), any());
    }
}
```

- [ ] **Step 3: Run test — expect compile error**

```bash
./gradlew test --tests "space.covalent.rocketchat.notifiers.DeathNotifierTest"
```

Expected: `DeathNotifier` does not exist yet — compile error.

- [ ] **Step 4: Create `DeathNotifier.java`**

Create `src/main/java/space/covalent/rocketchat/notifiers/DeathNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class DeathNotifier
{
    @Inject
    Client client;

    @Inject
    RocketChatNotifierConfig config;

    @Inject
    WebhookClient webhookClient;

    @Subscribe
    public void onActorDeath(ActorDeath event)
    {
        if (!config.notifyOnDeath())
        {
            return;
        }
        Player local = client.getLocalPlayer();
        if (event.getActor() != local)
        {
            return;
        }

        String name = local.getName() != null ? local.getName() : "Unknown";
        int combatLevel = local.getCombatLevel();
        WorldPoint location = local.getWorldLocation();
        String locationStr = location != null
            ? location.getX() + ", " + location.getY() + " (plane " + location.getPlane() + ")"
            : "Unknown";

        RocketChatPayload payload = RocketChatPayload.builder()
            .attachments(Collections.singletonList(
                RocketChatPayload.Attachment.builder()
                    .title(":skull: " + name + " has died")
                    .text("**Combat level:** " + combatLevel + "\n**Location:** " + locationStr)
                    .color("#FF0000")
                    .build()
            ))
            .build();

        webhookClient.send(config.webhookUrl(), payload);
    }
}
```

- [ ] **Step 5: Run test — expect pass**

```bash
./gradlew test --tests "space.covalent.rocketchat.notifiers.DeathNotifierTest"
```

Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 6: Wire `DeathNotifier` into `RocketChatNotifierPlugin`**

Add to `RocketChatNotifierPlugin.java`:

```java
// At top of class — new imports and fields:
import net.runelite.client.eventbus.EventBus;
import space.covalent.rocketchat.notifiers.DeathNotifier;

@Inject
private EventBus eventBus;

@Inject
private DeathNotifier deathNotifier;

// In startUp():
eventBus.register(deathNotifier);

// In shutDown():
eventBus.unregister(deathNotifier);
```

- [ ] **Step 7: Verify build**

```bash
./gradlew compileJava compileTestJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/notifiers/DeathNotifier.java \
        src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java \
        src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java \
        src/test/java/space/covalent/rocketchat/notifiers/DeathNotifierTest.java
git commit -m "feat: add death notifier"
```

---

## Task 4: Level-Up Notifier

**Goal:** When the local player gains a skill level, send a notification. Fires once per level gain (not per XP tick). Configurable minimum level threshold.

**Files:**
- Create: `src/main/java/space/covalent/rocketchat/notifiers/LevelNotifier.java`
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java` (level section)
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java` (register)
- Create: `src/test/java/space/covalent/rocketchat/notifiers/LevelNotifierTest.java`

**Interfaces:**
- Consumes: `RocketChatNotifierConfig.notifyOnLevel()`, `RocketChatNotifierConfig.minLevel()`, `WebhookClient.send()`
- Produces: `LevelNotifier` subscribing to `StatChanged`

- [ ] **Step 1: Add level config to `RocketChatNotifierConfig.java`**

```java
@ConfigSection(
    name = "Levels",
    description = "Notifications when you gain a level",
    position = 2
)
String levelSection = "level";

@ConfigItem(
    keyName = "notifyOnLevel",
    name = "Notify on level up",
    description = "Send a Rocket.Chat message when you gain a skill level",
    section = levelSection
)
default boolean notifyOnLevel()
{
    return false;
}

@ConfigItem(
    keyName = "minLevel",
    name = "Minimum level",
    description = "Only notify for levels at or above this value",
    section = levelSection
)
@Range(min = 1, max = 99)
default int minLevel()
{
    return 1;
}
```

Add import: `import net.runelite.client.config.Range;`

- [ ] **Step 2: Write the failing test**

Create `src/test/java/space/covalent/rocketchat/notifiers/LevelNotifierTest.java`:

```java
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
        StatChanged init = new StatChanged();
        init.setSkill(Skill.ATTACK);
        init.setLevel(70);
        init.setXp(737627);
        notifier.onStatChanged(init);
        verify(webhookClient, never()).send(any(), any());

        // Second event with higher level — notification fires
        StatChanged levelUp = new StatChanged();
        levelUp.setSkill(Skill.ATTACK);
        levelUp.setLevel(71);
        levelUp.setXp(800000);
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
        when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

        StatChanged init = new StatChanged();
        init.setSkill(Skill.STRENGTH);
        init.setLevel(30);
        notifier.onStatChanged(init);

        StatChanged levelUp = new StatChanged();
        levelUp.setSkill(Skill.STRENGTH);
        levelUp.setLevel(31);
        notifier.onStatChanged(levelUp);

        verify(webhookClient, never()).send(any(), any());
    }

    @Test
    public void testDoesNotNotifyOnXpChangeWithoutLevelGain()
    {
        when(config.notifyOnLevel()).thenReturn(true);
        when(config.minLevel()).thenReturn(1);

        StatChanged init = new StatChanged();
        init.setSkill(Skill.MAGIC);
        init.setLevel(75);
        notifier.onStatChanged(init);

        StatChanged xpTick = new StatChanged();
        xpTick.setSkill(Skill.MAGIC);
        xpTick.setLevel(75);
        xpTick.setXp(1200000);
        notifier.onStatChanged(xpTick);

        verify(webhookClient, never()).send(any(), any());
    }
}
```

- [ ] **Step 3: Run test — expect compile error**

```bash
./gradlew test --tests "space.covalent.rocketchat.notifiers.LevelNotifierTest"
```

Expected: `LevelNotifier` does not exist — compile error.

- [ ] **Step 4: Create `LevelNotifier.java`**

Create `src/main/java/space/covalent/rocketchat/notifiers/LevelNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class LevelNotifier
{
    private static final String SKILL_ICON_BASE = "https://oldschool.runescape.wiki/images/thumb/%s_icon.png/25px-%s_icon.png";

    @Inject
    RocketChatNotifierConfig config;

    @Inject
    WebhookClient webhookClient;

    private final Map<Skill, Integer> lastLevels = new EnumMap<>(Skill.class);

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        if (!config.notifyOnLevel())
        {
            return;
        }

        Skill skill = event.getSkill();
        int newLevel = event.getLevel();
        Integer lastLevel = lastLevels.put(skill, newLevel);

        if (lastLevel == null || newLevel <= lastLevel)
        {
            return;
        }

        if (newLevel < config.minLevel())
        {
            return;
        }

        String skillName = skill.getName();
        String iconUrl = String.format(SKILL_ICON_BASE, skillName, skillName);

        RocketChatPayload payload = RocketChatPayload.builder()
            .attachments(Collections.singletonList(
                RocketChatPayload.Attachment.builder()
                    .title(":chart_with_upwards_trend: Level " + newLevel + " " + skillName + "!")
                    .text("You have reached level **" + newLevel + "** " + skillName + ".")
                    .color("#00FF00")
                    .thumbUrl(iconUrl)
                    .build()
            ))
            .build();

        webhookClient.send(config.webhookUrl(), payload);
    }
}
```

- [ ] **Step 5: Run test — expect pass**

```bash
./gradlew test --tests "space.covalent.rocketchat.notifiers.LevelNotifierTest"
```

Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 6: Wire into plugin**

Add to `RocketChatNotifierPlugin.java`:

```java
import space.covalent.rocketchat.notifiers.LevelNotifier;

@Inject
private LevelNotifier levelNotifier;

// startUp():
eventBus.register(levelNotifier);

// shutDown():
eventBus.unregister(levelNotifier);
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/notifiers/LevelNotifier.java \
        src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java \
        src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java \
        src/test/java/space/covalent/rocketchat/notifiers/LevelNotifierTest.java
git commit -m "feat: add level-up notifier"
```

---

## Task 5: Loot & Clue Scroll Notifiers

**Goal:** When loot is received (NPC drop, pickpocket, etc.), send a notification if total value meets a configurable threshold. Separately, detect clue scroll completions (which are also `LootReceived` events) with a tier-based filter.

**Files:**
- Create: `src/main/java/space/covalent/rocketchat/notifiers/LootNotifier.java`
- Create: `src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java`
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java` (loot + clue sections)
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java` (register both)
- Create: `src/test/java/space/covalent/rocketchat/notifiers/LootNotifierTest.java`

**Interfaces:**
- Consumes: `ItemManager.getItemPrice(int)` for GE value; `LootReceived.getItems()` → `Collection<ItemStack>`
- Produces: `LootNotifier`, `ClueNotifier` (both subscribe to `LootReceived`)

- [ ] **Step 1: Add loot and clue config to `RocketChatNotifierConfig.java`**

```java
@ConfigSection(
    name = "Loot",
    description = "Notifications when you receive loot",
    position = 3
)
String lootSection = "loot";

@ConfigItem(
    keyName = "notifyOnLoot",
    name = "Notify on loot",
    description = "Send a Rocket.Chat message when you receive loot",
    section = lootSection
)
default boolean notifyOnLoot()
{
    return false;
}

@ConfigItem(
    keyName = "minLootValue",
    name = "Minimum loot value",
    description = "Only notify if total loot value (GE) meets this threshold (gp)",
    section = lootSection
)
default int minLootValue()
{
    return 100000;
}

@ConfigSection(
    name = "Clue Scrolls",
    description = "Notifications when you complete a clue scroll",
    position = 4
)
String clueSection = "clue";

@ConfigItem(
    keyName = "notifyOnClue",
    name = "Notify on clue completion",
    description = "Send a Rocket.Chat message when you complete a clue scroll",
    section = clueSection
)
default boolean notifyOnClue()
{
    return false;
}

@ConfigItem(
    keyName = "minClueTier",
    name = "Minimum clue tier",
    description = "Only notify for clues at or above this tier",
    section = clueSection
)
default ClueTier minClueTier()
{
    return ClueTier.EASY;
}
```

Also create the enum `ClueTier` in the same package:

Create `src/main/java/space/covalent/rocketchat/ClueTier.java`:

```java
package space.covalent.rocketchat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClueTier
{
    BEGINNER("Clue Scroll (Beginner)", 0),
    EASY("Clue Scroll (Easy)", 1),
    MEDIUM("Clue Scroll (Medium)", 2),
    HARD("Clue Scroll (Hard)", 3),
    ELITE("Clue Scroll (Elite)", 4),
    MASTER("Clue Scroll (Master)", 5);

    private final String lootSourceName;
    private final int rank;

    public static ClueTier fromLootSource(String name)
    {
        for (ClueTier tier : values())
        {
            if (tier.lootSourceName.equalsIgnoreCase(name))
            {
                return tier;
            }
        }
        return null;
    }
}
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/space/covalent/rocketchat/notifiers/LootNotifierTest.java`:

```java
package space.covalent.rocketchat.notifiers;

import net.runelite.api.ItemComposition;
import net.runelite.api.events.LootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LootNotifierTest
{
    @Mock
    RocketChatNotifierConfig config;

    @Mock
    WebhookClient webhookClient;

    @Mock
    ItemManager itemManager;

    @InjectMocks
    LootNotifier notifier;

    @Test
    public void testSendsNotificationWhenValueMeetsThreshold()
    {
        when(config.notifyOnLoot()).thenReturn(true);
        when(config.minLootValue()).thenReturn(100000);
        when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");

        int itemId = 4151; // Abyssal whip
        when(itemManager.getItemPrice(itemId)).thenReturn(2000000);
        ItemComposition comp = mock(ItemComposition.class);
        when(comp.getName()).thenReturn("Abyssal whip");
        when(itemManager.getItemComposition(itemId)).thenReturn(comp);

        LootReceived event = new LootReceived();
        event.setName("Abyssal Sire");
        event.setType(LootRecordType.NPC);
        event.setItems(Collections.singletonList(new ItemStack(itemId, 1, null)));
        notifier.onLootReceived(event);

        ArgumentCaptor<RocketChatPayload> captor = ArgumentCaptor.forClass(RocketChatPayload.class);
        verify(webhookClient).send(any(), captor.capture());
        String text = captor.getValue().getAttachments().get(0).getText();
        assertTrue(text.contains("Abyssal whip"));
    }

    @Test
    public void testSkipsWhenValueBelowThreshold()
    {
        when(config.notifyOnLoot()).thenReturn(true);
        when(config.minLootValue()).thenReturn(100000);

        int itemId = 526; // Bones
        when(itemManager.getItemPrice(itemId)).thenReturn(50);
        ItemComposition comp = mock(ItemComposition.class);
        when(comp.getName()).thenReturn("Bones");
        when(itemManager.getItemComposition(itemId)).thenReturn(comp);

        LootReceived event = new LootReceived();
        event.setName("Goblin");
        event.setType(LootRecordType.NPC);
        event.setItems(Collections.singletonList(new ItemStack(itemId, 1, null)));
        notifier.onLootReceived(event);

        verify(webhookClient, never()).send(any(), any());
    }
}
```

**Note on imports:** `net.runelite.client.game.ItemStack` is the loot item stack class; `LootRecordType` is in `net.runelite.http.api.loottracker`. Adjust the import based on what's available in the RuneLite API version resolved at build time. If `LootReceived` uses a different type enum, check the API via: `./gradlew dependencies | grep runelite`.

- [ ] **Step 3: Run test — expect compile error**

```bash
./gradlew test --tests "space.covalent.rocketchat.notifiers.LootNotifierTest"
```

- [ ] **Step 4: Create `LootNotifier.java`**

Create `src/main/java/space/covalent/rocketchat/notifiers/LootNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.LootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.http.api.loottracker.LootRecordType;
import space.covalent.rocketchat.ClueTier;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class LootNotifier
{
    @Inject
    RocketChatNotifierConfig config;

    @Inject
    WebhookClient webhookClient;

    @Inject
    ItemManager itemManager;

    @Subscribe
    public void onLootReceived(LootReceived event)
    {
        if (!config.notifyOnLoot())
        {
            return;
        }

        // Skip clue scroll rewards — handled by ClueNotifier
        if (ClueTier.fromLootSource(event.getName()) != null)
        {
            return;
        }

        Collection<ItemStack> items = event.getItems();
        long totalValue = 0;
        List<String> itemLines = new ArrayList<>();

        for (ItemStack stack : items)
        {
            long price = (long) itemManager.getItemPrice(stack.getId()) * stack.getQuantity();
            totalValue += price;
            ItemComposition comp = itemManager.getItemComposition(stack.getId());
            itemLines.add(stack.getQuantity() + "x **" + comp.getName() + "** (" + formatGp(price) + " gp)");
        }

        if (totalValue < config.minLootValue())
        {
            return;
        }

        String body = String.join("\n", itemLines) + "\n\n**Total:** " + formatGp(totalValue) + " gp";

        RocketChatPayload payload = RocketChatPayload.builder()
            .attachments(Collections.singletonList(
                RocketChatPayload.Attachment.builder()
                    .title(":moneybag: Loot from " + event.getName())
                    .text(body)
                    .color("#FFD700")
                    .build()
            ))
            .build();

        webhookClient.send(config.webhookUrl(), payload);
    }

    private static String formatGp(long value)
    {
        if (value >= 1_000_000)
        {
            return String.format("%.1fM", value / 1_000_000.0);
        }
        if (value >= 1_000)
        {
            return String.format("%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }
}
```

- [ ] **Step 5: Create `ClueNotifier.java`**

Create `src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.LootReceived;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import space.covalent.rocketchat.ClueTier;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class ClueNotifier
{
    @Inject
    RocketChatNotifierConfig config;

    @Inject
    WebhookClient webhookClient;

    @Inject
    ItemManager itemManager;

    @Subscribe
    public void onLootReceived(LootReceived event)
    {
        if (!config.notifyOnClue())
        {
            return;
        }

        ClueTier tier = ClueTier.fromLootSource(event.getName());
        if (tier == null)
        {
            return;
        }

        if (tier.getRank() < config.minClueTier().getRank())
        {
            return;
        }

        Collection<ItemStack> items = event.getItems();
        List<String> itemLines = new ArrayList<>();
        long totalValue = 0;

        for (ItemStack stack : items)
        {
            long price = (long) itemManager.getItemPrice(stack.getId()) * stack.getQuantity();
            totalValue += price;
            ItemComposition comp = itemManager.getItemComposition(stack.getId());
            itemLines.add(stack.getQuantity() + "x **" + comp.getName() + "**");
        }

        String body = String.join("\n", itemLines);
        if (totalValue > 0)
        {
            body += "\n\n**Total:** " + formatGp(totalValue) + " gp";
        }

        RocketChatPayload payload = RocketChatPayload.builder()
            .attachments(Collections.singletonList(
                RocketChatPayload.Attachment.builder()
                    .title(":scroll: " + tier.name().charAt(0) + tier.name().substring(1).toLowerCase() + " Clue Scroll completed")
                    .text(body)
                    .color("#8B4513")
                    .build()
            ))
            .build();

        webhookClient.send(config.webhookUrl(), payload);
    }

    private static String formatGp(long value)
    {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format("%.1fK", value / 1_000.0);
        return String.valueOf(value);
    }
}
```

- [ ] **Step 6: Run test — expect pass**

```bash
./gradlew test --tests "space.covalent.rocketchat.notifiers.LootNotifierTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Wire both into plugin**

```java
import space.covalent.rocketchat.notifiers.LootNotifier;
import space.covalent.rocketchat.notifiers.ClueNotifier;

@Inject
private LootNotifier lootNotifier;

@Inject
private ClueNotifier clueNotifier;

// startUp():
eventBus.register(lootNotifier);
eventBus.register(clueNotifier);

// shutDown():
eventBus.unregister(lootNotifier);
eventBus.unregister(clueNotifier);
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/notifiers/LootNotifier.java \
        src/main/java/space/covalent/rocketchat/notifiers/ClueNotifier.java \
        src/main/java/space/covalent/rocketchat/ClueTier.java \
        src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java \
        src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java \
        src/test/java/space/covalent/rocketchat/notifiers/LootNotifierTest.java
git commit -m "feat: add loot and clue scroll notifiers"
```

---

## Task 6: ChatMessage-Based Notifiers (Batch)

**Goal:** Implement seven notifiers that each match a specific `GAMEMESSAGE` pattern: Pet drops, Quest completions, Slayer task completions, Boss kill counts (with personal-best handling), Collection log entries, Combat achievements, and Achievement diaries. All share the same event type and test file.

**Files:**
- Create: `src/main/java/space/covalent/rocketchat/notifiers/PetNotifier.java`
- Create: `src/main/java/space/covalent/rocketchat/notifiers/QuestNotifier.java`
- Create: `src/main/java/space/covalent/rocketchat/notifiers/SlayerNotifier.java`
- Create: `src/main/java/space/covalent/rocketchat/notifiers/BossNotifier.java`
- Create: `src/main/java/space/covalent/rocketchat/notifiers/CollectionLogNotifier.java`
- Create: `src/main/java/space/covalent/rocketchat/notifiers/CombatAchievementNotifier.java`
- Create: `src/main/java/space/covalent/rocketchat/notifiers/DiaryNotifier.java`
- Create: `src/main/java/space/covalent/rocketchat/DiaryTier.java`
- Create: `src/main/java/space/covalent/rocketchat/CombatAchievementTier.java`
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java`
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java`
- Create: `src/test/java/space/covalent/rocketchat/notifiers/ChatMessageNotifiersTest.java`

**Interfaces:**
- Consumes: `ChatMessage` (type `GAMEMESSAGE`, `SPAM`), `net.runelite.api.util.Text.removeTags()`
- Produces: Seven notifier singletons, each subscribing to `onChatMessage`

- [ ] **Step 1: Create enum helpers**

Create `src/main/java/space/covalent/rocketchat/DiaryTier.java`:

```java
package space.covalent.rocketchat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DiaryTier
{
    EASY("Easy", 0),
    MEDIUM("Medium", 1),
    HARD("Hard", 2),
    ELITE("Elite", 3);

    private final String displayName;
    private final int rank;
}
```

Create `src/main/java/space/covalent/rocketchat/CombatAchievementTier.java`:

```java
package space.covalent.rocketchat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CombatAchievementTier
{
    EASY("Easy", 0),
    MEDIUM("Medium", 1),
    HARD("Hard", 2),
    ELITE("Elite", 3),
    MASTER("Master", 4),
    GRANDMASTER("Grandmaster", 5);

    private final String displayName;
    private final int rank;
}
```

- [ ] **Step 2: Add config sections for all seven notifiers**

Append to `RocketChatNotifierConfig.java`:

```java
// Pet
@ConfigSection(name = "Pets", description = "Pet drop notifications", position = 5)
String petSection = "pet";

@ConfigItem(keyName = "notifyOnPet", name = "Notify on pet", description = "Send a message when you receive a pet", section = petSection)
default boolean notifyOnPet() { return false; }

// Quest
@ConfigSection(name = "Quests", description = "Quest completion notifications", position = 6)
String questSection = "quest";

@ConfigItem(keyName = "notifyOnQuest", name = "Notify on quest", description = "Send a message when you complete a quest", section = questSection)
default boolean notifyOnQuest() { return false; }

// Slayer
@ConfigSection(name = "Slayer", description = "Slayer task completion notifications", position = 7)
String slayerSection = "slayer";

@ConfigItem(keyName = "notifyOnSlayer", name = "Notify on slayer task", description = "Send a message when you complete a slayer task", section = slayerSection)
default boolean notifyOnSlayer() { return false; }

// Boss
@ConfigSection(name = "Boss Kills", description = "Boss kill count notifications", position = 8)
String bossSection = "boss";

@ConfigItem(keyName = "notifyOnBoss", name = "Notify on boss kill", description = "Send a message on boss kill count milestones", section = bossSection)
default boolean notifyOnBoss() { return false; }

@ConfigItem(keyName = "bossPersonalBestOnly", name = "Personal best only", description = "Only notify when a personal best time is set", section = bossSection)
default boolean bossPersonalBestOnly() { return false; }

@ConfigItem(keyName = "bossKillCountInterval", name = "Kill count interval", description = "Notify every N kills (0 = only on personal best)", section = bossSection)
default int bossKillCountInterval() { return 1; }

// Collection log
@ConfigSection(name = "Collection Log", description = "Collection log new-entry notifications", position = 9)
String collectionLogSection = "collectionlog";

@ConfigItem(keyName = "notifyOnCollectionLog", name = "Notify on collection log", description = "Send a message when a new item is added to your collection log", section = collectionLogSection)
default boolean notifyOnCollectionLog() { return false; }

// Combat achievements
@ConfigSection(name = "Combat Achievements", description = "Combat achievement notifications", position = 10)
String combatAchievementSection = "combatachievement";

@ConfigItem(keyName = "notifyOnCombatAchievement", name = "Notify on CA", description = "Send a message when you complete a combat achievement", section = combatAchievementSection)
default boolean notifyOnCombatAchievement() { return false; }

@ConfigItem(keyName = "minCombatAchievementTier", name = "Minimum tier", description = "Only notify for this tier or above", section = combatAchievementSection)
default CombatAchievementTier minCombatAchievementTier() { return CombatAchievementTier.EASY; }

// Achievement diaries
@ConfigSection(name = "Achievement Diaries", description = "Diary completion notifications", position = 11)
String diarySection = "diary";

@ConfigItem(keyName = "notifyOnDiary", name = "Notify on diary", description = "Send a message when you complete an achievement diary", section = diarySection)
default boolean notifyOnDiary() { return false; }

@ConfigItem(keyName = "minDiaryTier", name = "Minimum tier", description = "Only notify for this tier or above", section = diarySection)
default DiaryTier minDiaryTier() { return DiaryTier.EASY; }
```

- [ ] **Step 3: Write the failing test**

Create `src/test/java/space/covalent/rocketchat/notifiers/ChatMessageNotifiersTest.java`:

```java
package space.covalent.rocketchat.notifiers;

import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import org.junit.Before;
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

    private static ChatMessage gameMessage(String text)
    {
        ChatMessage msg = new ChatMessage();
        msg.setType(ChatMessageType.GAMEMESSAGE);
        msg.setMessage(text);
        return msg;
    }

    @Before
    public void setUp()
    {
        when(config.webhookUrl()).thenReturn("http://example.com/hooks/test");
    }

    // ── Pet ─────────────────────────────────────────────────────────────────

    @Test
    public void testPetNotifierFiresOnFunnyFeeling()
    {
        when(config.notifyOnPet()).thenReturn(true);
        petNotifier.onChatMessage(gameMessage("You have a funny feeling like you're being followed."));
        verify(webhookClient).send(any(), any());
    }

    @Test
    public void testPetNotifierFiresOnWeirdFeeling()
    {
        when(config.notifyOnPet()).thenReturn(true);
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
        slayerNotifier.onChatMessage(gameMessage("You have completed your task! You killed 150 Abyssal demons."));
        verify(webhookClient).send(any(), any());
    }

    // ── Boss ─────────────────────────────────────────────────────────────────

    @Test
    public void testBossNotifierFiresOnKillCount()
    {
        when(config.notifyOnBoss()).thenReturn(true);
        when(config.bossKillCountInterval()).thenReturn(1);
        when(config.bossPersonalBestOnly()).thenReturn(false);
        bossNotifier.onChatMessage(gameMessage("Your Zulrah kill count is: 50."));
        verify(webhookClient).send(any(), any());
    }

    @Test
    public void testBossNotifierFiresOnPersonalBest()
    {
        when(config.notifyOnBoss()).thenReturn(true);
        when(config.bossPersonalBestOnly()).thenReturn(false);
        when(config.bossKillCountInterval()).thenReturn(1);
        bossNotifier.onChatMessage(gameMessage("Fight duration: 1:34. Personal best: 1:34."));
        verify(webhookClient).send(any(), any());
    }

    @Test
    public void testBossNotifierSkipsNonPbWhenPbOnly()
    {
        when(config.notifyOnBoss()).thenReturn(true);
        when(config.bossPersonalBestOnly()).thenReturn(true);
        // Kill count message without personal best
        bossNotifier.onChatMessage(gameMessage("Your Zulrah kill count is: 49."));
        verify(webhookClient, never()).send(any(), any());
    }

    // ── Collection Log ────────────────────────────────────────────────────────

    @Test
    public void testCollectionLogNotifierFires()
    {
        when(config.notifyOnCollectionLog()).thenReturn(true);
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
        diaryNotifier.onChatMessage(gameMessage(
            "Congratulations! You have completed all of the Varrock Hard Diary tasks."));
        verify(webhookClient).send(any(), any());
    }
}
```

- [ ] **Step 4: Run test — expect compile errors (all notifiers missing)**

```bash
./gradlew test --tests "space.covalent.rocketchat.notifiers.ChatMessageNotifiersTest"
```

- [ ] **Step 5: Create `PetNotifier.java`**

Create `src/main/java/space/covalent/rocketchat/notifiers/PetNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.util.Text;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class PetNotifier
{
    @Inject RocketChatNotifierConfig config;
    @Inject WebhookClient webhookClient;

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!config.notifyOnPet()) return;
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) return;

        String msg = Text.removeTags(event.getMessage());
        if (!msg.contains("funny feeling") && !msg.contains("weird sneaking into your backpack")) return;

        webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
            .attachments(Collections.singletonList(
                RocketChatPayload.Attachment.builder()
                    .title(":dog: You received a pet!")
                    .text(msg)
                    .color("#9B59B6")
                    .build()
            ))
            .build());
    }
}
```

- [ ] **Step 6: Create `QuestNotifier.java`**

Create `src/main/java/space/covalent/rocketchat/notifiers/QuestNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.util.Text;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class QuestNotifier
{
    private static final Pattern QUEST_COMPLETE = Pattern.compile(
        "Congratulations, you've completed (.+)!");

    @Inject RocketChatNotifierConfig config;
    @Inject WebhookClient webhookClient;

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!config.notifyOnQuest()) return;
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String msg = Text.removeTags(event.getMessage());
        Matcher m = QUEST_COMPLETE.matcher(msg);
        if (!m.find()) return;

        String questName = m.group(1);

        webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
            .attachments(Collections.singletonList(
                RocketChatPayload.Attachment.builder()
                    .title(":trophy: Quest complete!")
                    .text("You have completed **" + questName + "**.")
                    .color("#1E8449")
                    .build()
            ))
            .build());
    }
}
```

- [ ] **Step 7: Create `SlayerNotifier.java`**

Create `src/main/java/space/covalent/rocketchat/notifiers/SlayerNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.util.Text;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class SlayerNotifier
{
    private static final Pattern TASK_COMPLETE = Pattern.compile(
        "You have completed your task! You killed (\\d[\\d,]*) (.+)\\.");

    @Inject RocketChatNotifierConfig config;
    @Inject WebhookClient webhookClient;

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!config.notifyOnSlayer()) return;
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String msg = Text.removeTags(event.getMessage());
        Matcher m = TASK_COMPLETE.matcher(msg);
        if (!m.find()) return;

        String count = m.group(1);
        String monster = m.group(2);

        webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
            .attachments(Collections.singletonList(
                RocketChatPayload.Attachment.builder()
                    .title(":crossed_swords: Slayer task complete!")
                    .text("Killed **" + count + "** " + monster + ".")
                    .color("#E74C3C")
                    .build()
            ))
            .build());
    }
}
```

- [ ] **Step 8: Create `BossNotifier.java`**

Create `src/main/java/space/covalent/rocketchat/notifiers/BossNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.util.Text;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class BossNotifier
{
    private static final Pattern KILL_COUNT = Pattern.compile(
        "Your (.+) kill count is: ([\\d,]+)\\.");
    private static final Pattern FIGHT_DURATION = Pattern.compile(
        "Fight duration: ([\\d:]+)\\.(?:.* Personal best: ([\\d:]+))?");

    @Inject RocketChatNotifierConfig config;
    @Inject WebhookClient webhookClient;

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!config.notifyOnBoss()) return;
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String msg = Text.removeTags(event.getMessage());

        Matcher kc = KILL_COUNT.matcher(msg);
        if (kc.find())
        {
            if (config.bossPersonalBestOnly()) return;
            String boss = kc.group(1);
            String count = kc.group(2);
            int countVal;
            try { countVal = Integer.parseInt(count.replace(",", "")); }
            catch (NumberFormatException e) { return; }

            int interval = config.bossKillCountInterval();
            if (interval > 1 && countVal % interval != 0) return;

            webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
                .attachments(Collections.singletonList(
                    RocketChatPayload.Attachment.builder()
                        .title(":skull_crossbones: " + boss + " kill count: " + count)
                        .color("#C0392B")
                        .build()
                ))
                .build());
            return;
        }

        Matcher fd = FIGHT_DURATION.matcher(msg);
        if (fd.find())
        {
            String duration = fd.group(1);
            String pb = fd.group(2);
            boolean isNewPb = pb != null && pb.equals(duration);

            if (config.bossPersonalBestOnly() && !isNewPb) return;

            String title = isNewPb
                ? ":star: New personal best: " + duration
                : ":timer_clock: Fight duration: " + duration;

            webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
                .attachments(Collections.singletonList(
                    RocketChatPayload.Attachment.builder()
                        .title(title)
                        .color(isNewPb ? "#F1C40F" : "#95A5A6")
                        .build()
                ))
                .build());
        }
    }
}
```

- [ ] **Step 9: Create `CollectionLogNotifier.java`**

Create `src/main/java/space/covalent/rocketchat/notifiers/CollectionLogNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.util.Text;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class CollectionLogNotifier
{
    private static final Pattern NEW_ENTRY = Pattern.compile(
        "New item added to your collection log: (.+)\\.");

    @Inject RocketChatNotifierConfig config;
    @Inject WebhookClient webhookClient;

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!config.notifyOnCollectionLog()) return;
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String msg = Text.removeTags(event.getMessage());
        Matcher m = NEW_ENTRY.matcher(msg);
        if (!m.find()) return;

        String itemName = m.group(1);

        webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
            .attachments(Collections.singletonList(
                RocketChatPayload.Attachment.builder()
                    .title(":book: Collection log update")
                    .text("New entry: **" + itemName + "**")
                    .color("#2980B9")
                    .build()
            ))
            .build());
    }
}
```

- [ ] **Step 10: Create `CombatAchievementNotifier.java`**

Create `src/main/java/space/covalent/rocketchat/notifiers/CombatAchievementNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.util.Text;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.CombatAchievementTier;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class CombatAchievementNotifier
{
    private static final Pattern CA_COMPLETE = Pattern.compile(
        "Congratulations, you've completed (?:a|an) (Easy|Medium|Hard|Elite|Master|Grandmaster) combat achievement: (.+)\\.");

    @Inject RocketChatNotifierConfig config;
    @Inject WebhookClient webhookClient;

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!config.notifyOnCombatAchievement()) return;
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String msg = Text.removeTags(event.getMessage());
        Matcher m = CA_COMPLETE.matcher(msg);
        if (!m.find()) return;

        String tierName = m.group(1);
        String taskName = m.group(2);

        CombatAchievementTier tier;
        try { tier = CombatAchievementTier.valueOf(tierName.toUpperCase()); }
        catch (IllegalArgumentException e) { return; }

        if (tier.getRank() < config.minCombatAchievementTier().getRank()) return;

        webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
            .attachments(Collections.singletonList(
                RocketChatPayload.Attachment.builder()
                    .title(":medal: Combat Achievement: " + taskName)
                    .text("Tier: **" + tierName + "**")
                    .color("#E67E22")
                    .build()
            ))
            .build());
    }
}
```

- [ ] **Step 11: Create `DiaryNotifier.java`**

Create `src/main/java/space/covalent/rocketchat/notifiers/DiaryNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.util.Text;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.DiaryTier;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class DiaryNotifier
{
    private static final Pattern DIARY_COMPLETE = Pattern.compile(
        "Congratulations! You have completed all of the (.+) (Easy|Medium|Hard|Elite) Diary tasks\\.");

    @Inject RocketChatNotifierConfig config;
    @Inject WebhookClient webhookClient;

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!config.notifyOnDiary()) return;
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String msg = Text.removeTags(event.getMessage());
        Matcher m = DIARY_COMPLETE.matcher(msg);
        if (!m.find()) return;

        String area = m.group(1);
        String tierName = m.group(2);

        DiaryTier tier;
        try { tier = DiaryTier.valueOf(tierName.toUpperCase()); }
        catch (IllegalArgumentException e) { return; }

        if (tier.getRank() < config.minDiaryTier().getRank()) return;

        webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
            .attachments(Collections.singletonList(
                RocketChatPayload.Attachment.builder()
                    .title(":clipboard: " + area + " " + tierName + " Diary complete!")
                    .color("#27AE60")
                    .build()
            ))
            .build());
    }
}
```

- [ ] **Step 12: Run tests — expect pass**

```bash
./gradlew test --tests "space.covalent.rocketchat.notifiers.ChatMessageNotifiersTest"
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 13: Wire all seven into plugin**

Add to `RocketChatNotifierPlugin.java`:

```java
import space.covalent.rocketchat.notifiers.PetNotifier;
import space.covalent.rocketchat.notifiers.QuestNotifier;
import space.covalent.rocketchat.notifiers.SlayerNotifier;
import space.covalent.rocketchat.notifiers.BossNotifier;
import space.covalent.rocketchat.notifiers.CollectionLogNotifier;
import space.covalent.rocketchat.notifiers.CombatAchievementNotifier;
import space.covalent.rocketchat.notifiers.DiaryNotifier;

@Inject private PetNotifier petNotifier;
@Inject private QuestNotifier questNotifier;
@Inject private SlayerNotifier slayerNotifier;
@Inject private BossNotifier bossNotifier;
@Inject private CollectionLogNotifier collectionLogNotifier;
@Inject private CombatAchievementNotifier combatAchievementNotifier;
@Inject private DiaryNotifier diaryNotifier;

// startUp(): register each
// shutDown(): unregister each
```

Full `startUp()` body at this point:

```java
eventBus.register(deathNotifier);
eventBus.register(levelNotifier);
eventBus.register(lootNotifier);
eventBus.register(clueNotifier);
eventBus.register(petNotifier);
eventBus.register(questNotifier);
eventBus.register(slayerNotifier);
eventBus.register(bossNotifier);
eventBus.register(collectionLogNotifier);
eventBus.register(combatAchievementNotifier);
eventBus.register(diaryNotifier);
```

Mirror in `shutDown()` with `unregister`.

- [ ] **Step 14: Verify build**

```bash
./gradlew compileJava compileTestJava
```

- [ ] **Step 15: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/notifiers/ \
        src/main/java/space/covalent/rocketchat/DiaryTier.java \
        src/main/java/space/covalent/rocketchat/CombatAchievementTier.java \
        src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java \
        src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java \
        src/test/java/space/covalent/rocketchat/notifiers/ChatMessageNotifiersTest.java
git commit -m "feat: add pet, quest, slayer, boss, collection log, CA, and diary notifiers"
```

---

## Task 7: Custom Chat Pattern Notifier

**Goal:** Allow the user to specify a regex pattern; any `ChatMessage` matching it triggers a notification. Useful for custom game messages or server-specific events.

**Files:**
- Create: `src/main/java/space/covalent/rocketchat/notifiers/ChatPatternNotifier.java`
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java`
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java`
- Create: `src/test/java/space/covalent/rocketchat/notifiers/ChatPatternNotifierTest.java`

**Interfaces:**
- Consumes: `RocketChatNotifierConfig.chatPattern()` (String regex), `RocketChatNotifierConfig.notifyOnChatPattern()`
- Produces: `ChatPatternNotifier`

- [ ] **Step 1: Add config**

```java
@ConfigSection(name = "Custom Pattern", description = "Notify on custom chat messages", position = 12)
String chatPatternSection = "chatpattern";

@ConfigItem(keyName = "notifyOnChatPattern", name = "Notify on pattern match",
    description = "Send a message when a chat message matches the custom pattern",
    section = chatPatternSection)
default boolean notifyOnChatPattern() { return false; }

@ConfigItem(keyName = "chatPattern", name = "Pattern (regex)",
    description = "Java regex to match against chat messages",
    section = chatPatternSection)
default String chatPattern() { return ""; }
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/space/covalent/rocketchat/notifiers/ChatPatternNotifierTest.java`:

```java
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
}
```

- [ ] **Step 3: Run — expect compile error**

```bash
./gradlew test --tests "space.covalent.rocketchat.notifiers.ChatPatternNotifierTest"
```

- [ ] **Step 4: Create `ChatPatternNotifier.java`**

Create `src/main/java/space/covalent/rocketchat/notifiers/ChatPatternNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.util.Text;
import net.runelite.client.eventbus.Subscribe;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Slf4j
@Singleton
public class ChatPatternNotifier
{
    @Inject RocketChatNotifierConfig config;
    @Inject WebhookClient webhookClient;

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!config.notifyOnChatPattern()) return;

        String rawPattern = config.chatPattern();
        if (rawPattern == null || rawPattern.isEmpty()) return;

        String msg = Text.removeTags(event.getMessage());

        Pattern pattern;
        try
        {
            pattern = Pattern.compile(rawPattern, Pattern.CASE_INSENSITIVE);
        }
        catch (PatternSyntaxException e)
        {
            log.debug("Invalid chat pattern regex: {}", rawPattern);
            return;
        }

        if (!pattern.matcher(msg).find()) return;

        webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
            .attachments(Collections.singletonList(
                RocketChatPayload.Attachment.builder()
                    .title(":bell: Pattern match")
                    .text(msg)
                    .color("#3498DB")
                    .build()
            ))
            .build());
    }
}
```

- [ ] **Step 5: Run — expect pass**

```bash
./gradlew test --tests "space.covalent.rocketchat.notifiers.ChatPatternNotifierTest"
```

- [ ] **Step 6: Wire into plugin**

```java
import space.covalent.rocketchat.notifiers.ChatPatternNotifier;
@Inject private ChatPatternNotifier chatPatternNotifier;
// startUp: eventBus.register(chatPatternNotifier);
// shutDown: eventBus.unregister(chatPatternNotifier);
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/notifiers/ChatPatternNotifier.java \
        src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java \
        src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java \
        src/test/java/space/covalent/rocketchat/notifiers/ChatPatternNotifierTest.java
git commit -m "feat: add custom chat pattern notifier"
```

---

## Task 8: Grand Exchange Notifier

**Goal:** When a GE offer fully completes (bought or sold), send a notification with the item name, quantity, and total value.

**Files:**
- Create: `src/main/java/space/covalent/rocketchat/notifiers/GrandExchangeNotifier.java`
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java`
- Modify: `src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java`
- Create: `src/test/java/space/covalent/rocketchat/notifiers/GrandExchangeNotifierTest.java`

**Interfaces:**
- Consumes: `GrandExchangeOfferChanged`, `GrandExchangeOffer.getState()`, `ItemManager.getItemComposition()`
- Produces: `GrandExchangeNotifier`

- [ ] **Step 1: Add GE config**

```java
@ConfigSection(name = "Grand Exchange", description = "Grand Exchange trade notifications", position = 13)
String grandExchangeSection = "grandexchange";

@ConfigItem(keyName = "notifyOnGrandExchange", name = "Notify on GE trade",
    description = "Send a message when a Grand Exchange offer completes",
    section = grandExchangeSection)
default boolean notifyOnGrandExchange() { return false; }

@ConfigItem(keyName = "minGrandExchangeValue", name = "Minimum trade value",
    description = "Only notify if the completed trade value meets this threshold (gp)",
    section = grandExchangeSection)
default int minGrandExchangeValue() { return 0; }
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/space/covalent/rocketchat/notifiers/GrandExchangeNotifierTest.java`:

```java
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
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class GrandExchangeNotifierTest
{
    @Mock RocketChatNotifierConfig config;
    @Mock WebhookClient webhookClient;
    @Mock ItemManager itemManager;

    @InjectMocks GrandExchangeNotifier notifier;

    @Test
    public void testSendsOnBought()
    {
        when(config.notifyOnGrandExchange()).thenReturn(true);
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
        when(config.minGrandExchangeValue()).thenReturn(1000000);

        ItemComposition comp = mock(ItemComposition.class);
        when(comp.getName()).thenReturn("Coins");
        when(itemManager.getItemComposition(995)).thenReturn(comp);

        GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
        when(offer.getState()).thenReturn(GrandExchangeOfferState.BOUGHT);
        when(offer.getItemId()).thenReturn(995);
        when(offer.getTotalQuantity()).thenReturn(1);
        when(offer.getPrice()).thenReturn(1);

        GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
        event.setOffer(offer);
        notifier.onGrandExchangeOfferChanged(event);

        verify(webhookClient, never()).send(any(), any());
    }
}
```

- [ ] **Step 3: Run — expect compile error**

```bash
./gradlew test --tests "space.covalent.rocketchat.notifiers.GrandExchangeNotifierTest"
```

- [ ] **Step 4: Create `GrandExchangeNotifier.java`**

Create `src/main/java/space/covalent/rocketchat/notifiers/GrandExchangeNotifier.java`:

```java
package space.covalent.rocketchat.notifiers;

import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import space.covalent.rocketchat.RocketChatNotifierConfig;
import space.covalent.rocketchat.RocketChatPayload;
import space.covalent.rocketchat.WebhookClient;

@Singleton
public class GrandExchangeNotifier
{
    @Inject RocketChatNotifierConfig config;
    @Inject WebhookClient webhookClient;
    @Inject ItemManager itemManager;

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
    {
        if (!config.notifyOnGrandExchange()) return;

        GrandExchangeOffer offer = event.getOffer();
        GrandExchangeOfferState state = offer.getState();

        if (state != GrandExchangeOfferState.BOUGHT && state != GrandExchangeOfferState.SOLD) return;

        long totalValue = (long) offer.getTotalQuantity() * offer.getPrice();
        if (totalValue < config.minGrandExchangeValue()) return;

        ItemComposition comp = itemManager.getItemComposition(offer.getItemId());
        String itemName = comp.getName();
        String action = state == GrandExchangeOfferState.BOUGHT ? "Bought" : "Sold";
        String color = state == GrandExchangeOfferState.BOUGHT ? "#27AE60" : "#E74C3C";

        String body = "**Quantity:** " + offer.getTotalQuantity()
            + "\n**Price each:** " + offer.getPrice() + " gp"
            + "\n**Total:** " + formatGp(totalValue) + " gp";

        webhookClient.send(config.webhookUrl(), RocketChatPayload.builder()
            .attachments(Collections.singletonList(
                RocketChatPayload.Attachment.builder()
                    .title(":shopping_cart: " + action + " " + itemName)
                    .text(body)
                    .color(color)
                    .build()
            ))
            .build());
    }

    private static String formatGp(long value)
    {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format("%.1fK", value / 1_000.0);
        return String.valueOf(value);
    }
}
```

- [ ] **Step 5: Run — expect pass**

```bash
./gradlew test --tests "space.covalent.rocketchat.notifiers.GrandExchangeNotifierTest"
```

- [ ] **Step 6: Wire into plugin**

```java
import space.covalent.rocketchat.notifiers.GrandExchangeNotifier;
@Inject private GrandExchangeNotifier grandExchangeNotifier;
// startUp: eventBus.register(grandExchangeNotifier);
// shutDown: eventBus.unregister(grandExchangeNotifier);
```

- [ ] **Step 7: Run all tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/space/covalent/rocketchat/notifiers/GrandExchangeNotifier.java \
        src/main/java/space/covalent/rocketchat/RocketChatNotifierConfig.java \
        src/main/java/space/covalent/rocketchat/RocketChatNotifierPlugin.java \
        src/test/java/space/covalent/rocketchat/notifiers/GrandExchangeNotifierTest.java
git commit -m "feat: add Grand Exchange notifier"
```

---

## Self-Review

### Spec coverage

| Dink feature | Covered by |
|---|---|
| Death | Task 3 DeathNotifier |
| Collection Log | Task 6 CollectionLogNotifier |
| Skill Leveling | Task 4 LevelNotifier |
| Loot Drops | Task 5 LootNotifier |
| Slayer Tasks | Task 6 SlayerNotifier |
| Quest Completion | Task 6 QuestNotifier |
| Clue Scrolls | Task 5 ClueNotifier |
| Boss Kills | Task 6 BossNotifier |
| Combat Achievements | Task 6 CombatAchievementNotifier |
| Achievement Diaries | Task 6 DiaryNotifier |
| Pet Drops | Task 6 PetNotifier |
| Grand Exchange Trades | Task 8 GrandExchangeNotifier |
| Custom Chat Patterns | Task 7 ChatPatternNotifier |
| Quest Speedruns | Not included — complex, low priority |
| BA Gambles | Not included — niche |
| GIM Bank Transactions | Not included — niche |
| Leagues Events | Not included — seasonal |
| Player Kills | Not included — complex PvP detection |
| P2P Trades | Not included — no clean RuneLite event |
| External Plugin Requests | Not included — scope |

### Placeholder scan

No TBD, TODO, or "similar to" references present.

### Type consistency

- `RocketChatPayload.Attachment.thumbUrl` — accessed via `.thumbUrl()` (Lombok `@Value` generates `getThumbUrl()`)
- `LevelNotifier` calls `.thumbUrl(iconUrl)` on builder — matches `RocketChatPayload.Attachment.Builder.thumbUrl(String)`
- `ClueTier.getRank()` and `DiaryTier.getRank()` — defined in their enums and consumed in notifiers consistently
- `BossNotifier` uses `config.bossKillCountInterval()` — defined in Task 6 config step

---

## Running the Plugin

Once all tasks are complete, launch with:

```bash
./gradlew run
```

Follow the [Jagex Accounts login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) to authenticate in the development client.

**What to test:**
1. Configure a Rocket.Chat incoming webhook URL in the plugin config panel
2. Enable one notification type (e.g. "Notify on death")
3. Trigger the event in-game (die somewhere safe)
4. Confirm the message appears in your Rocket.Chat channel
5. Repeat for each enabled notification type
6. Verify that disabled notification types do **not** fire
7. Test the custom pattern notifier with a regex matching a known in-game message
