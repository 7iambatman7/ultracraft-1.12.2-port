package dev.ultracraft;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.Display;

/** Client-only helpers kept out of the common classes (a dedicated server never loads this). */
@SideOnly(Side.CLIENT)
final class ClientUtil {
	private ClientUtil() {}

	/** Minecraft's frame limit (or the display's refresh rate with VSync), 30 to 240. */
	static int minecraftFpsLimit() {
		int limit = 240;
		try {
			Minecraft mc = Minecraft.getMinecraft();
			int cap = mc.gameSettings.limitFramerate;
			if (cap < 260) limit = cap;
			if (mc.gameSettings.enableVsync) {
				int hz = Display.getDisplayMode().getFrequency();
				if (hz > 0) limit = Math.min(limit, hz);
			}
		} catch (RuntimeException ignored) {
		}
		return Math.max(30, Math.min(240, limit));
	}
}
