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
		Request request;
		try
		{
			request = new Request.Builder()
				.url(webhookUrl)
				.post(body)
				.build();
		}
		catch (IllegalArgumentException e)
		{
			log.debug("Invalid Rocket.Chat webhook URL: {}", webhookUrl);
			return;
		}

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
