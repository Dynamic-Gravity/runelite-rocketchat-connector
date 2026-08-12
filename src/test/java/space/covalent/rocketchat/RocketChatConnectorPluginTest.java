package space.covalent.rocketchat;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RocketChatConnectorPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RocketChatConnectorPlugin.class);
		RuneLite.main(args);
	}
}
