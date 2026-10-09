package dev.ultracraft;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * The channel from a player's ULTRAKILL to the server that owns the world (this process in singleplayer, the host's in a
 * world shared through Essential or opened to LAN). The server applies what ULTRAKILL did as that player.
 */
public final class NetHandler {
	public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("ultracraft");

	private NetHandler() {}

	public static void init() {
		CHANNEL.registerMessage(ServerHandler.class, UkMessage.class, 0, Side.SERVER);
	}

	/** Client to server (a no-op when the server doesn't have Ultracraft). */
	public static void toServer(String line) {
		try {
			CHANNEL.sendToServer(new UkMessage(line));
		} catch (RuntimeException ignored) {
			// not connected, or the server hasn't got this mod
		}
	}

	public static class ServerHandler implements IMessageHandler<UkMessage, IMessage> {
		@Override
		public IMessage onMessage(final UkMessage message, final MessageContext ctx) {
			final EntityPlayerMP player = ctx.getServerHandler().player;
			player.getServerWorld().addScheduledTask(new Runnable() {
				@Override
				public void run() {
					try {
						ServerOps.handle(player, message.text);
					} catch (RuntimeException e) {
						System.err.println("[Ultracraft] server op '" + message.text + "' failed: " + e);
					}
				}
			});
			return null;
		}
	}
}
