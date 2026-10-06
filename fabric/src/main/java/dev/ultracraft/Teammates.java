package dev.ultracraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * The other players, over ULTRAKILL's view: Minecraft doesn't draw another V1 (ULTRAKILL draws V1's body there, and only
 * within reach), so there was no name tag, and past that nothing showed where they were. Each gets their name, health
 * and how far off, over their head and seen through walls; off the screen, at its edge on their side.
 */
final class Teammates {
	private static final int EDGE = 18;

	private Teammates() {}

	static void render(Minecraft mc, GuiGraphics ctx, float partial) {
		if (!UltracraftConfig.teammateMarkers || mc.level == null || mc.player == null || mc.options.hideGui) return;
		if (!Ultracraft.active && !Ultracraft.steveView) return;
		var camera = mc.gameRenderer.getMainCamera();
		Vec3 eye = camera.position();
		Vector3fc fwd = camera.forwardVector();
		int w = ctx.guiWidth(), h = ctx.guiHeight();
		for (AbstractClientPlayer o : mc.level.players()) {
			if (o == mc.player || o.isSpectator() || !o.isAlive()) continue;
			// the one we're watching while down: their view is ours
			if (mc.getCameraEntity() == o) continue;
			Vec3 at = o.getPosition(partial).add(0.0, o.getBbHeight() + 0.45, 0.0);
			Vec3 to = at.subtract(eye);
			double dist = to.length();
			boolean ahead = to.x * fwd.x() + to.y * fwd.y() + to.z * fwd.z() > 0.05;
			Vec3 ndc = mc.gameRenderer.projectPointToScreen(at);
			double sx = (ndc.x + 1.0) * 0.5 * w, sy = (1.0 - ndc.y) * 0.5 * h;
			boolean onScreen = ahead && sx >= EDGE && sx <= w - EDGE && sy >= EDGE && sy <= h - EDGE;
			if (!onScreen) {
				// off the screen: at its edge, on the side they are (behind us: the projection is mirrored)
				double dx = ahead ? sx - w / 2.0 : w / 2.0 - sx, dy = ahead ? sy - h / 2.0 : h / 2.0 - sy;
				if (!ahead && Math.abs(dy) < 1.0) dy = h;
				double scale = Math.min((w / 2.0 - EDGE) / Math.max(1e-3, Math.abs(dx)), (h / 2.0 - EDGE) / Math.max(1e-3, Math.abs(dy)));
				sx = w / 2.0 + dx * scale;
				sy = h / 2.0 + dy * scale;
			}
			int x = (int) Math.round(sx), y = (int) Math.round(sy);
			String name = o.getName().getString();
			boolean v1 = UcNet.isV1(o);
			String label = (onScreen ? "" : "◆ ") + name + (dist > 12 ? "  " + (int) Math.round(dist) + "m" : "");
			int tw = mc.font.width(label);
			// a dark plate behind it, so it reads over anything ULTRAKILL draws
			ctx.fill(x - tw / 2 - 3, y - 12, x + tw / 2 + 3, y + 1, 0x90000000);
			ctx.drawString(mc.font, label, x - tw / 2, y - 10, v1 ? 0xFF7FD4FF : 0xFFFFFFFF, true);
			// health: a bar under the name
			float frac = Mth.clamp(o.getHealth() / Math.max(1f, o.getMaxHealth()), 0f, 1f);
			int bw = Math.max(30, tw), bx = x - bw / 2;
			ctx.fill(bx, y + 2, bx + bw, y + 5, 0xB0200000);
			int col = frac > 0.5f ? 0xFF40E040 : frac > 0.25f ? 0xFFE0C030 : 0xFFE03030;
			ctx.fill(bx, y + 2, bx + Math.round(bw * frac), y + 5, col);
		}
	}
}
