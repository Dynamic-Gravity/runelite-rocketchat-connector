package space.covalent.rocketchat;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
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

		HttpUrl url = HttpUrl.parse(serverOrigin);
		if (url == null)
		{
			result.completeExceptionally(new IOException("Invalid server origin: " + serverOrigin));
			return result;
		}
		url = url.newBuilder()
			.addPathSegments("api/v1/rooms.upload")
			.addPathSegment(roomId)
			.build();

		RequestBody fileBody = RequestBody.create(MediaType.get("image/png"), pngBytes);
		RequestBody multipart = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("file", "clue-reward.png", fileBody)
			.build();

		Request request = new Request.Builder()
			.url(url)
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
