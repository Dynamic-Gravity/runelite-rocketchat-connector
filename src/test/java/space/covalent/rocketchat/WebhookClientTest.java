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

	@Test
	public void testSkipsWhenUrlMalformed()
	{
		RocketChatPayload payload = RocketChatPayload.builder().text("test").build();
		client.send("not a valid url !!!", payload);
		assertEquals(0, server.getRequestCount());
	}
}
