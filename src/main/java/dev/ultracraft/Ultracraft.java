package dev.ultracraft;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * Ultracraft: real ULTRAKILL inside real Minecraft (1.12.2 Forge port).
 *
 * ULTRAKILL (with the UltraBridge BepInEx plugin) runs alongside. While "V1 mode" is on, V1 is ULTRAKILL's own player:
 * keyboard and mouse go to it, Minecraft's camera sits where V1's camera is, ULTRAKILL's frame (guns, arm, HUD,
 * effects, enemies) is drawn over the world, the blocks and mobs around us go to ULTRAKILL as colliders and targets, and
 * what it hits is applied to the world by the server (ServerOps).
 */
@SideOnly(Side.CLIENT)
public final class Ultracraft {
	public static volatile boolean active;
	/** An ULTRAKILL menu is open: Minecraft's cursor is free and points at it. */
	public static volatile boolean uiMode;
	/** Minecraft hands: V1 puts the guns away and uses items like Steve. */
	public static volatile boolean hands;
	/** V1 stands at a shop's screen (not in the 1.12.2 port yet). */
	public static volatile boolean shopTouch;

	private static final double REBASE = 1024.0;
	private static KeyBinding toggle, handsKey, settingsKey;
	private static boolean originSent;
	private static double originX, originY, originZ;
	private static int originDim;
	private static BlockPos lastExport;
	private static int tick;
	private static boolean autoPending = true;
	private static boolean pauseSent;
	private static boolean inWorld;
	private static boolean wasDead;
	private static EntityPlayerSP lastPlayer;
	private static int fpsCheck, fpsSent;
	private static int viewSent = -1;
	private static double lastX, lastY, lastZ;
	private static boolean haveLast;
	private static boolean statusShown;

	private Ultracraft() {}

	public static void init() {
		toggle = new KeyBinding("key.ultracraft.toggle", Keyboard.KEY_F8, "key.categories.ultracraft");
		handsKey = new KeyBinding("key.ultracraft.hands", Keyboard.KEY_V, "key.categories.ultracraft");
		settingsKey = new KeyBinding("key.ultracraft.settings", Keyboard.KEY_F7, "key.categories.ultracraft");
		ClientRegistry.registerKeyBinding(toggle);
		ClientRegistry.registerKeyBinding(handsKey);
		ClientRegistry.registerKeyBinding(settingsKey);
		UkLink.start();
	}

	// ------------------------------------------------------------------ per tick

	public static void clientTick(Minecraft mc) {
		UkInput.install(mc);
		String msg;
		while ((msg = UkLink.INBOX.poll()) != null) handle(mc, msg);
		while (toggle.isPressed()) setActive(mc, !active);
		while (settingsKey.isPressed()) mc.displayGuiScreen(new UltracraftSettingsScreen());
		while (handsKey.isPressed()) {
			if (active && !uiMode) setHands(mc, !hands);
		}
		if (active) mc.gameSettings.smoothCamera = false; // F8 is also Minecraft's cinematic camera

		// Minecraft's pause menu (Esc) stops ULTRAKILL too: V1, enemies, projectiles, sound
		boolean paused = active && mc.isSingleplayer() && mc.currentScreen != null && mc.currentScreen.doesGuiPauseGame();
		if (paused != pauseSent && UkLink.connected) {
			pauseSent = paused;
			UkLink.send("PAUSE " + (paused ? 1 : 0));
		}
		// Minecraft's frame limit (or VSync) changed: ULTRAKILL's cap follows it
		if (UkLink.connected && UltracraftConfig.ukFps == 0 && ++fpsCheck % 40 == 0) {
			int now = UltracraftConfig.ukFpsNow();
			if (now != fpsSent) {
				fpsSent = now;
				UltracraftConfig.sendOpts();
			}
		}
		EntityPlayerSP p = mc.player;
		if (mc.world == null) {
			// out of the world: nothing of it goes on to the next one
			if (inWorld) {
				inWorld = false;
				if (UkLink.connected) UkLink.send("WORLDRESET");
				if (active) {
					active = false;
					end(mc);
				}
			}
			return;
		} else if (!inWorld && p != null) {
			inWorld = true;
		}
		if (p == null) return;
		if (autoPending && UltracraftConfig.autoV1 && UkLink.ready && !active && mc.currentScreen == null) {
			// one-click play: become V1 as soon as both games are up
			autoPending = false;
			active = true;
			lastPlayer = p;
			begin(mc);
		}
		if (p != lastPlayer) {
			// Respawned or changed dimension: re-register active V1 protection on the authoritative server,
			// then tell ULTRAKILL to rebase its player and world position to the new Minecraft player.
			lastPlayer = p;
			if (active) {
				NetHandler.toServer("V1 1");
				UkLink.send("RESPAWN");
				UkLink.pose = null; // don't immediately snap back to the last pre-respawn pose
				teleportV1(mc, p);
				lastExport = null;
				haveLast = false;
			}
		}
		if (!active || !UkLink.connected) return;
		tick++;
		UkLink.Pose pose = UkLink.pose;
		if (pose != null) {
			// V1 is the player: Minecraft's player follows V1 (no Minecraft physics)
			p.capabilities.isFlying = true;
			p.motionX = 0;
			p.motionY = 0;
			p.motionZ = 0;
			p.setPosition(pose.fx, pose.fy, pose.fz);
			p.rotationYaw = pose.yaw;
			p.rotationPitch = pose.pitch;
			p.rotationYawHead = pose.yaw;
			p.onGround = pose.onGround;
			p.fallDistance = 0;
		} else {
			// teleported, ULTRAKILL not there yet: hold still where Minecraft put us
			p.capabilities.isFlying = true;
			p.motionX = 0;
			p.motionY = 0;
			p.motionZ = 0;
		}
		sendView(mc);
		BlockPos bp = new BlockPos(p.posX, p.posY, p.posZ);
		if (lastExport == null || bp.distanceSq(lastExport) > 4 || tick % 10 == 0) {
			UkExport.exportBlocks(mc.world, bp);
			lastExport = bp;
		}
		if (tick % 10 == 0) vitals(p);
		UkExport.exportEntities(mc.world, p);
		UkExport.exportProjectiles(mc.world, p);
		if (UkLink.dead && !wasDead) {
			// V1 died in ULTRAKILL: die in Minecraft too (respawn goes through Minecraft)
			NetHandler.toServer("DEAD");
		}
		wasDead = UkLink.dead;
	}

	/** V1's health to the server, which mirrors it in Minecraft's hearts (and makes walking cost hunger). */
	private static void vitals(EntityPlayerSP p) {
		double moved = 0;
		if (haveLast) moved = Math.sqrt((p.posX - lastX) * (p.posX - lastX) + (p.posY - lastY) * (p.posY - lastY) + (p.posZ - lastZ) * (p.posZ - lastZ));
		lastX = p.posX;
		lastY = p.posY;
		lastZ = p.posZ;
		haveLast = true;
		NetHandler.toServer(String.format(Locale.ROOT, "VITALS %d %d %d %.3f 0", UkLink.hp, UkLink.maxHp, UkLink.dead ? 1 : 0, moved));
	}

	/** Minecraft's camera (F5): first person, behind V1, or in front of it; ULTRAKILL's camera does the same (VIEW). */
	private static void sendView(Minecraft mc) {
		int view = mc.gameSettings.thirdPersonView;
		if (view < 0 || view > 2) view = 0;
		if (view == viewSent || !UkLink.connected) return;
		viewSent = view;
		UkLink.send("VIEW " + view);
	}

	// ------------------------------------------------------------------ state changes

	static void setActive(Minecraft mc, boolean on) {
		if (on == active || (on && !UkLink.ready)) return;
		active = on;
		if (on) begin(mc);
		else end(mc);
	}

	private static void begin(Minecraft mc) {
		EntityPlayerSP p = mc.player;
		if (p == null) return;
		NetHandler.toServer("V1 1");
		UkInput.lastInputAt = System.currentTimeMillis();
		originSent = false;
		UkExport.reset();
		haveLast = false;
		teleportV1(mc, p);
		lastExport = null;
		UkLink.send("HANDS " + (hands ? 1 : 0));
		viewSent = -1;
	}

	/** Back to Steve: nothing of V1's may linger. */
	private static void end(Minecraft mc) {
		setUiMode(mc, false);
		shopTouch = false;
		UkLink.send("ZOOM 1");
		UkLink.send("VIEW 0");
		viewSent = 0;
		if (mc.player != null) mc.player.capabilities.isFlying = mc.player.capabilities.allowFlying && mc.player.capabilities.isFlying;
		NetHandler.toServer("V1 0");
	}

	static void setHands(Minecraft mc, boolean on) {
		hands = on;
		UkLink.send("HANDS " + (on ? 1 : 0));
		if (mc.player != null) {
			mc.player.sendStatusMessage(new TextComponentString(on ? "Minecraft hands (V for guns)" : "V1's guns (V for Minecraft hands)"), true);
		}
	}

	static void setUiMode(Minecraft mc, boolean on) {
		uiMode = on;
		if (on && active) {
			mc.setIngameNotInFocus();
		} else if (mc.currentScreen == null && mc.player != null && !mc.inGameHasFocus) {
			mc.setIngameFocus();
		}
	}

	/** Where (and in which dimension) ULTRAKILL's world origin is: moved along after a long teleport (its floats lose precision far out). */
	private static void teleportV1(Minecraft mc, EntityPlayerSP p) {
		boolean rebase = false;
		if (originSent && (originDim != p.dimension || Math.max(Math.abs(p.posX - originX), Math.abs(p.posZ - originZ)) > REBASE || Math.abs(p.posY - originY) > REBASE)) {
			originSent = false;
			rebase = true;
			UkExport.reset();
			lastExport = null;
		}
		if (!originSent) {
			UkLink.send(String.format(Locale.ROOT, "ORIGIN %.3f %.3f %.3f", p.posX, p.posY, p.posZ) + (rebase ? " keep" : ""));
			originSent = true;
			originX = p.posX;
			originY = p.posY;
			originZ = p.posZ;
			originDim = p.dimension;
		}
		UkExport.exportBlocks(mc.world, new BlockPos(p.posX, p.posY, p.posZ));
		UkLink.send(String.format(Locale.ROOT, "TP %.3f %.3f %.3f %.2f %.2f", p.posX, p.posY + 0.05, p.posZ, p.rotationYaw, p.rotationPitch));
	}

	// ------------------------------------------------------------------ messages from ULTRAKILL

	private static void handle(Minecraft mc, String msg) {
		if (msg.equals("READY")) {
			if (mc.player != null) mc.player.sendMessage(new TextComponentString("[Ultracraft] ULTRAKILL is ready. Press F8 to become V1."));
		} else if (msg.equals("CONNECTED")) {
			// a (re)started ULTRAKILL needs our window size again, and whether we're paused
			UkInput.resetSize();
			pauseSent = false;
			UkExport.reset();
			UltracraftConfig.sendOpts();
		} else if (msg.equals("DISCONNECTED")) {
			active = false;
			autoPending = true;
			end(mc);
		} else if (msg.startsWith("UI ")) {
			setUiMode(mc, msg.endsWith("1"));
		} else if (msg.startsWith("UKPREFS ") || msg.startsWith("SONGS ") || msg.startsWith("FIGHT ") || msg.startsWith("STAINS ") || msg.startsWith("OILS ")) {
			// settings screen, music and blood stains: not in the 1.12.2 port yet
		} else if (msg.matches("^(SLAM|BOOM|FIRE|DMG|WHIP|MKNOCK|SHURT) .*")) {
			// what our ULTRAKILL did to the shared world: the server (this process in singleplayer, the host's in a shared world) applies it
			NetHandler.toServer(msg);
		}
	}

	/** Top-left status while V1 mode is off. */
	public static String status() {
		if (active || !UkLink.connected) return null;
		String where = UkLink.loadingScene.isEmpty() ? "" : " (" + UkLink.loadingScene + ")";
		return UkLink.ready ? "ULTRAKILL ready - press F8 to become V1" : "ULTRAKILL connected - loading V1..." + where;
	}
}
