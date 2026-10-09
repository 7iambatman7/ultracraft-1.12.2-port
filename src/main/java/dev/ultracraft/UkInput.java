package dev.ultracraft;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Keyboard and mouse to ULTRAKILL: the "IN dx dy wheel buttons key..." line, with keys in GLFW codes (what UltraBridge
 * expects; LWJGL 2 has its own key codes, mapped here) and the mouse as raw movement in pixels, y down.
 */
public final class UkInput {
	/** {LWJGL 2 key, GLFW key}: the keys ULTRAKILL can use. */
	private static final int[][] KEYS = {
		{Keyboard.KEY_A, 65},
		{Keyboard.KEY_B, 66},
		{Keyboard.KEY_C, 67},
		{Keyboard.KEY_D, 68},
		{Keyboard.KEY_E, 69},
		{Keyboard.KEY_F, 70},
		{Keyboard.KEY_G, 71},
		{Keyboard.KEY_H, 72},
		{Keyboard.KEY_I, 73},
		{Keyboard.KEY_J, 74},
		{Keyboard.KEY_K, 75},
		{Keyboard.KEY_L, 76},
		{Keyboard.KEY_M, 77},
		{Keyboard.KEY_N, 78},
		{Keyboard.KEY_O, 79},
		{Keyboard.KEY_P, 80},
		{Keyboard.KEY_Q, 81},
		{Keyboard.KEY_R, 82},
		{Keyboard.KEY_S, 83},
		{Keyboard.KEY_T, 84},
		{Keyboard.KEY_U, 85},
		{Keyboard.KEY_V, 86},
		{Keyboard.KEY_W, 87},
		{Keyboard.KEY_X, 88},
		{Keyboard.KEY_Y, 89},
		{Keyboard.KEY_Z, 90},
		{Keyboard.KEY_0, 48},
		{Keyboard.KEY_1, 49},
		{Keyboard.KEY_2, 50},
		{Keyboard.KEY_3, 51},
		{Keyboard.KEY_4, 52},
		{Keyboard.KEY_5, 53},
		{Keyboard.KEY_6, 54},
		{Keyboard.KEY_7, 55},
		{Keyboard.KEY_8, 56},
		{Keyboard.KEY_9, 57},
		{Keyboard.KEY_SPACE, 32},
		{Keyboard.KEY_LSHIFT, 340},
		{Keyboard.KEY_RSHIFT, 344},
		{Keyboard.KEY_LCONTROL, 341},
		{Keyboard.KEY_RCONTROL, 345},
		{Keyboard.KEY_LMENU, 342},
		{Keyboard.KEY_RMENU, 346},
		{Keyboard.KEY_TAB, 258},
		{Keyboard.KEY_CAPITAL, 280},
		{Keyboard.KEY_LEFT, 263},
		{Keyboard.KEY_RIGHT, 262},
		{Keyboard.KEY_UP, 265},
		{Keyboard.KEY_DOWN, 264},
		{Keyboard.KEY_COMMA, 44},
		{Keyboard.KEY_PERIOD, 46},
		{Keyboard.KEY_SLASH, 47},
		{Keyboard.KEY_SEMICOLON, 59},
		{Keyboard.KEY_APOSTROPHE, 39},
		{Keyboard.KEY_MINUS, 45},
		{Keyboard.KEY_EQUALS, 61},
		{Keyboard.KEY_LBRACKET, 91},
		{Keyboard.KEY_RBRACKET, 93},
		{Keyboard.KEY_BACKSLASH, 92}
	};
	private static final int GLFW_ESCAPE = 256, GLFW_ENTER = 257, GLFW_BACKSPACE = 259;
	private static final int GLFW_0 = 48, GLFW_9 = 57, GLFW_E = 69, GLFW_Q = 81;
	private static final int GLFW_W = 87, GLFW_A = 65, GLFW_S = 83, GLFW_D = 68, GLFW_SPACE = 32;
	private static final int GLFW_LSHIFT = 340, GLFW_RSHIFT = 344, GLFW_LCTRL = 341, GLFW_RCTRL = 345;

	/** Wheel movement waiting to go to ULTRAKILL (120 per notch, as Windows reports it). */
	public static double wheel;
	/** Mouse movement Minecraft's own MouseHelper read before we got to it (kept for ULTRAKILL). */
	private static double capturedDx, capturedDy;
	/** The input already went to ULTRAKILL at the start of this frame. */
	public static boolean sentEarly;
	/** When the player last moved, looked, clicked or pressed a key as V1. */
	public static volatile long lastInputAt = System.currentTimeMillis();
	private static int lastW = -1, lastH = -1;

	private UkInput() {}

	/** Minecraft's mouse helper, passing movement on to ULTRAKILL and keeping Minecraft's own camera still while V1. */
	public static final class Helper extends MouseHelper {
		@Override
		public void mouseXYChange() {
			super.mouseXYChange();
			capturedDx += this.deltaX;
			capturedDy += this.deltaY;
			if (Ultracraft.active) {
				this.deltaX = 0;
				this.deltaY = 0;
			}
		}
	}

	/** A (re)started ULTRAKILL needs our window size again. */
	public static void resetSize() {
		lastW = -1;
		lastH = -1;
	}

	public static void install(Minecraft mc) {
		if (!(mc.mouseHelper instanceof Helper)) mc.mouseHelper = new Helper();
	}

	/** The size ULTRAKILL renders at (V1 height, same aspect, width a multiple of 4): sent when it changes. */
	public static int[] renderSize() {
		int fw = Display.getWidth(), fh = Display.getHeight();
		int cap = Ultracraft.shopTouch && UltracraftConfig.sharpShop ? 0 : UltracraftConfig.v1Height;
		double scale = cap > 0 && fh > cap ? (double) cap / fh : 1.0;
		int w = Math.max(4, ((int) Math.round(fw * scale)) & ~3), h = Math.max(1, (int) Math.round(fh * scale));
		return new int[] {w, h};
	}

	public static void sendInput(boolean early) {
		Minecraft mc = Minecraft.getMinecraft();
		if (!UkLink.connected || !Display.isCreated()) return;
		int[] size = renderSize();
		int w = size[0], h = size[1];
		if (w != lastW || h != lastH) {
			lastW = w;
			lastH = h;
			UkLink.send("SIZE " + w + " " + h);
		}
		if (early) sentEarly = true;
		if (!Ultracraft.active) return;
		boolean ui = Ultracraft.uiMode;
		boolean ingame = mc.currentScreen == null && (mc.inGameHasFocus || ui);
		boolean look = ingame && !ui;
		double dx = capturedDx + Mouse.getDX();
		double dy = -(capturedDy + Mouse.getDY());
		capturedDx = 0;
		capturedDy = 0;
		if (!look) {
			dx = 0;
			dy = 0;
		}
		if (ingame && ui) {
			// a menu is open: point at it (window coordinates scaled to the size ULTRAKILL renders at)
			double sx = Display.getWidth() > 0 ? (double) w / Display.getWidth() : 1.0;
			double sy = Display.getHeight() > 0 ? (double) h / Display.getHeight() : 1.0;
			UkLink.send(String.format(Locale.ROOT, "PTR %.1f %.1f", Mouse.getX() * sx, (Display.getHeight() - 1 - Mouse.getY()) * sy));
		}
		double notches = ingame ? wheel : 0;
		wheel = 0;
		boolean mcHands = Ultracraft.hands && !ui;
		int buttons = 0;
		if (ingame) {
			for (int b = 0; b < 5; b++) if (b < Mouse.getButtonCount() && Mouse.isButtonDown(b)) buttons |= 1 << b;
			if (mcHands && !Ultracraft.shopTouch) buttons &= ~7;
		}
		StringBuilder sb = new StringBuilder("IN ");
		sb.append(String.format(Locale.ROOT, "%.3f %.3f %.1f ", dx, dy, notches));
		sb.append(buttons);
		int bare = sb.length();
		boolean carried = false;
		if (ingame) {
			for (int[] k : KEYS) {
				int g = k[1];
				if (mcHands && ((g >= GLFW_0 && g <= GLFW_9) || g == GLFW_E || g == GLFW_Q)) continue;
				if (carried && (g == GLFW_W || g == GLFW_A || g == GLFW_S || g == GLFW_D || g == GLFW_SPACE || g == GLFW_LSHIFT || g == GLFW_RSHIFT
					|| g == GLFW_LCTRL || g == GLFW_RCTRL)) continue;
				if (Keyboard.isKeyDown(k[0])) sb.append(' ').append(g);
			}
			if (ui) {
				if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) sb.append(' ').append(GLFW_ESCAPE);
				if (Keyboard.isKeyDown(Keyboard.KEY_RETURN)) sb.append(' ').append(GLFW_ENTER);
				if (Keyboard.isKeyDown(Keyboard.KEY_BACK)) sb.append(' ').append(GLFW_BACKSPACE);
			}
		}
		if (dx != 0 || dy != 0 || buttons != 0 || notches != 0 || sb.length() > bare) lastInputAt = System.currentTimeMillis();
		UkLink.send(sb.toString());
	}
}
