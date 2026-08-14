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

	@Test
	public void testMalformedServerOriginCompletesExceptionallyInsteadOfThrowing() throws Exception
	{
		// upload() must not throw synchronously here - it previously built the request URL via
		// raw string concatenation, which threw IllegalArgumentException from Request.Builder#url
		// before a CompletableFuture was ever returned to the caller.
		CompletableFuture<Void> future = client.upload("not a url !!!", "room1", "user1", "token1", new byte[]{1});

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
