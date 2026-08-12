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
