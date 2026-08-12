package space.covalent.rocketchat;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RarityLookupServiceTest
{
	private MockWebServer server;
	private RarityLookupService service;

	@Before
	public void setUp() throws Exception
	{
		server = new MockWebServer();
		server.start();

		service = new RarityLookupService();
		service.okHttpClient = new OkHttpClient();
		service.gson = new Gson();
		service.apiUrl = server.url("/api.php").toString();
	}

	@After
	public void tearDown() throws Exception
	{
		server.shutdown();
	}

	private RarityLookupService.Rarity awaitLookup(String itemName, String sourceName) throws InterruptedException
	{
		AtomicReference<RarityLookupService.Rarity> result = new AtomicReference<>();
		CountDownLatch latch = new CountDownLatch(1);
		service.lookup(itemName, sourceName, rarity ->
		{
			result.set(rarity);
			latch.countDown();
		});
		assertTrue("lookup callback did not fire", latch.await(5, TimeUnit.SECONDS));
		return result.get();
	}

	@Test
	public void testMatchesSourceIgnoringVariantSuffix() throws InterruptedException
	{
		server.enqueue(new MockResponse().setResponseCode(200).setBody(
			"{\"bucket\":["
				+ "{\"item_name\":\"Abyssal whip\",\"drop_json\":\"{\\\"Rarity\\\":\\\"1/512\\\",\\\"Dropped from\\\":\\\"Abyssal demon#Standard\\\"}\"},"
				+ "{\"item_name\":\"Abyssal whip\",\"drop_json\":\"{\\\"Rarity\\\":\\\"12/128\\\",\\\"Dropped from\\\":\\\"Unsired\\\"}\"}"
				+ "]}"));

		RarityLookupService.Rarity rarity = awaitLookup("Abyssal whip", "Abyssal demon");

		assertEquals("1/512", rarity.getRaw());
		assertEquals(100.0 / 512, rarity.getPercent(), 0.0001);
	}

	@Test
	public void testReturnsNullWhenNoSourceMatches() throws InterruptedException
	{
		server.enqueue(new MockResponse().setResponseCode(200).setBody(
			"{\"bucket\":[{\"item_name\":\"Abyssal whip\",\"drop_json\":\"{\\\"Rarity\\\":\\\"1/512\\\",\\\"Dropped from\\\":\\\"Abyssal demon\\\"}\"}]}"));

		RarityLookupService.Rarity rarity = awaitLookup("Abyssal whip", "Greater abyssal demon");

		assertNull(rarity);
	}

	@Test
	public void testReturnsNullForNonNumericRarity() throws InterruptedException
	{
		server.enqueue(new MockResponse().setResponseCode(200).setBody(
			"{\"bucket\":[{\"item_name\":\"Raw shrimps\",\"drop_json\":\"{\\\"Rarity\\\":\\\"Varies\\\",\\\"Dropped from\\\":\\\"Fishing Trawler\\\"}\"}]}"));

		RarityLookupService.Rarity rarity = awaitLookup("Raw shrimps", "Fishing Trawler");

		assertNull(rarity);
	}

	@Test
	public void testReturnsNullOnServerError() throws InterruptedException
	{
		server.enqueue(new MockResponse().setResponseCode(500));

		RarityLookupService.Rarity rarity = awaitLookup("Abyssal whip", "Abyssal demon");

		assertNull(rarity);
	}

	@Test
	public void testCachesSecondLookup() throws InterruptedException
	{
		server.enqueue(new MockResponse().setResponseCode(200).setBody(
			"{\"bucket\":[{\"item_name\":\"Abyssal whip\",\"drop_json\":\"{\\\"Rarity\\\":\\\"1/512\\\",\\\"Dropped from\\\":\\\"Abyssal demon\\\"}\"}]}"));

		awaitLookup("Abyssal whip", "Abyssal demon");
		awaitLookup("Abyssal whip", "Abyssal demon");

		assertEquals(1, server.getRequestCount());
	}

	@Test
	public void testCachesNegativeLookup() throws InterruptedException
	{
		server.enqueue(new MockResponse().setResponseCode(200).setBody(
			"{\"bucket\":[{\"item_name\":\"Abyssal whip\",\"drop_json\":\"{\\\"Rarity\\\":\\\"1/512\\\",\\\"Dropped from\\\":\\\"Abyssal demon\\\"}\"}]}"));

		awaitLookup("Abyssal whip", "Greater abyssal demon");
		awaitLookup("Abyssal whip", "Greater abyssal demon");

		assertEquals(1, server.getRequestCount());
	}

	@Test
	public void testSkipsMalformedDropJson() throws InterruptedException
	{
		server.enqueue(new MockResponse().setResponseCode(200).setBody(
			"{\"bucket\":["
				+ "{\"item_name\":\"Test item\",\"drop_json\":\"malformed json\"},"
				+ "{\"item_name\":\"Test item\",\"drop_json\":\"{\\\"Rarity\\\":\\\"1/256\\\",\\\"Dropped from\\\":\\\"Test source\\\"}\"}"
				+ "]}"));

		RarityLookupService.Rarity rarity = awaitLookup("Test item", "Test source");

		assertEquals("1/256", rarity.getRaw());
		assertEquals(100.0 / 256, rarity.getPercent(), 0.0001);
	}

	@Test
	public void testDoesNotCacheErrorResponse() throws InterruptedException
	{
		server.enqueue(new MockResponse().setResponseCode(500));
		server.enqueue(new MockResponse().setResponseCode(200).setBody(
			"{\"bucket\":[{\"item_name\":\"Abyssal whip\",\"drop_json\":\"{\\\"Rarity\\\":\\\"1/512\\\",\\\"Dropped from\\\":\\\"Abyssal demon\\\"}\"}]}"));

		awaitLookup("Abyssal whip", "Abyssal demon");
		awaitLookup("Abyssal whip", "Abyssal demon");

		assertEquals(2, server.getRequestCount());
	}

	@Test
	public void testDoesNotCacheMalformedEnvelopeResponse() throws InterruptedException
	{
		server.enqueue(new MockResponse().setResponseCode(200).setBody("not valid json"));
		server.enqueue(new MockResponse().setResponseCode(200).setBody(
			"{\"bucket\":[{\"item_name\":\"Abyssal whip\",\"drop_json\":\"{\\\"Rarity\\\":\\\"1/512\\\",\\\"Dropped from\\\":\\\"Abyssal demon\\\"}\"}]}"));

		awaitLookup("Abyssal whip", "Abyssal demon");
		awaitLookup("Abyssal whip", "Abyssal demon");

		assertEquals(2, server.getRequestCount());
	}
}
