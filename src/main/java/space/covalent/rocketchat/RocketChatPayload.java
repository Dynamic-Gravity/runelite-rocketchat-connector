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
		@SerializedName("author_name")
		String authorName;
		@SerializedName("author_icon")
		String authorIcon;
		@SerializedName("author_link")
		String authorLink;
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
