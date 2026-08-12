package space.covalent.rocketchat;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
public class RarityLookupService
{
	private static final Pattern FRACTION = Pattern.compile("^(\\d+)/(\\d+)$");

	@Inject
	OkHttpClient okHttpClient;

	@Inject
	Gson gson;

	String apiUrl = "https://oldschool.runescape.wiki/api.php";

	private final Map<String, Rarity> cache = new ConcurrentHashMap<>();

	@Value
	public static class Rarity
	{
		String raw;
		double percent;
	}

	public void lookup(String itemName, String sourceName, Consumer<Rarity> callback)
	{
		String cacheKey = itemName + "|" + sourceName;
		Rarity cached = cache.get(cacheKey);
		if (cached != null)
		{
			callback.accept(cached);
			return;
		}

		String query = "bucket('dropsline').select('item_name','drop_json').where('item_name','"
			+ itemName.replace("'", "\\'") + "').run()";
		HttpUrl url = HttpUrl.parse(apiUrl).newBuilder()
			.addQueryParameter("action", "bucket")
			.addQueryParameter("format", "json")
			.addQueryParameter("query", query)
			.build();

		okHttpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Rarity lookup failed", e);
				callback.accept(null);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				Rarity rarity = parse(response, sourceName);
				if (rarity != null)
				{
					cache.put(cacheKey, rarity);
				}
				response.close();
				callback.accept(rarity);
			}
		});
	}

	private Rarity parse(Response response, String sourceName)
	{
		try
		{
			BucketResponse body = gson.fromJson(response.body().charStream(), BucketResponse.class);
			if (body == null || body.bucket == null)
			{
				return null;
			}

			for (BucketRow row : body.bucket)
			{
				DropJson drop = gson.fromJson(row.dropJson, DropJson.class);
				if (drop == null || drop.droppedFrom == null || drop.rarity == null)
				{
					continue;
				}

				String source = drop.droppedFrom.split("#", 2)[0];
				if (!source.equalsIgnoreCase(sourceName))
				{
					continue;
				}

				Matcher m = FRACTION.matcher(drop.rarity.trim());
				if (!m.matches())
				{
					continue;
				}

				double percent = Double.parseDouble(m.group(1)) / Double.parseDouble(m.group(2)) * 100;
				return new Rarity(drop.rarity, percent);
			}
		}
		catch (Exception e)
		{
			log.debug("Failed to parse rarity lookup response", e);
		}
		return null;
	}

	private static class BucketResponse
	{
		List<BucketRow> bucket;
	}

	private static class BucketRow
	{
		@SerializedName("drop_json")
		String dropJson;
	}

	private static class DropJson
	{
		@SerializedName("Rarity")
		String rarity;
		@SerializedName("Dropped from")
		String droppedFrom;
	}
}
