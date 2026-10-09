package dev.ultracraft;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Ultracraft for Minecraft 1.12.2 (Forge): real ULTRAKILL inside real Minecraft. Both sides need the mod (the server applies
 * what ULTRAKILL did to the world), so a world shared with friends (Essential, LAN, or a server) needs it on every player's
 * Minecraft; the version check accepts a vanilla server too (then ULTRAKILL's hits just don't reach the world).
 */
@Mod(modid = UltracraftMod.MODID, name = "Ultracraft", version = "1.0.4", acceptedMinecraftVersions = "[1.12.2]", acceptableRemoteVersions = "*")
public class UltracraftMod {
	public static final String MODID = "ultracraft";

	@SidedProxy(clientSide = "dev.ultracraft.UltracraftMod$ClientProxy", serverSide = "dev.ultracraft.UltracraftMod$CommonProxy")
	public static CommonProxy proxy;

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		UltracraftConfig.init(event.getModConfigurationDirectory());
		NetHandler.init();
		proxy.preInit();
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		// Common events must be registered on both the integrated server and a dedicated server.
		MinecraftForge.EVENT_BUS.register(new CommonEvents());
		proxy.init();
	}

	@Mod.EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		event.registerServerCommand(new UltracraftCommand());
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		proxy.postInit();
	}

	public static class CommonProxy {
		public void preInit() {}

		public void init() {}

		public void postInit() {}
	}

	@SideOnly(Side.CLIENT)
	public static class ClientProxy extends CommonProxy {
		@Override
		public void preInit() {
			Ultracraft.init();
		}

		@Override
		public void init() {
			MinecraftForge.EVENT_BUS.register(new ClientEvents());
			net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(new UltracraftClientCommand());
		}

		@Override
		public void postInit() {
			UkLauncher.launch();
			Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
				@Override
				public void run() {
					UkLauncher.close();
				}
			}, "ultracraft-close"));
		}
	}
}
