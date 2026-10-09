package dev.ultracraft;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Locale;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * ULTRAKILL's frames, from the file UltraBridge maps (%TEMP%/ultracraft_frame2.bin). Each frame is the final colour image
 * plus V1's mask (the alpha, or whole pre-post-processing target, of V1's 3D render target), read back by ULTRAKILL's GPU
 * straight into a slot of the file. Both are uploaded into two GL textures and combined by a small shader while drawing
 * over Minecraft's world. Same file layout as the Fabric version (header 64 bytes, 4 slots of colour + mask).
 */
public final class UkFrame {
	private static final int HEADER = 64;
	private static final long MAX_PIXELS = 3840L * 2160L;
	private static final int SLOTS = 4;
	private static final long SLOT_BYTES = MAX_PIXELS * 8L;

	private static MappedByteBuffer map;
	private static int colorTex, maskTex;
	private static int texW, texH, texMaskBpp;
	private static int lastSeq = -1;
	public static long frames;
	/** The view the shown frame was drawn from (eye x y z, yaw, pitch, roll, fov), or null. */
	public static volatile float[] framePose;
	/** When the newest frame arrived. */
	public static long lastFrameAt;

	private static int program, programRgba;
	private static boolean shaderFailed;
	private static boolean taken;

	private UkFrame() {}

	private static boolean open() {
		if (map != null) return true;
		File f = new File(System.getProperty("java.io.tmpdir"), "ultracraft_frame2" + UltracraftConfig.instanceSuffix() + ".bin");
		if (!f.isFile()) return false;
		try (RandomAccessFile raf = new RandomAccessFile(f, "r"); FileChannel ch = raf.getChannel()) {
			long need = HEADER + SLOTS * SLOT_BYTES;
			if (ch.size() < need) return false;
			map = ch.map(FileChannel.MapMode.READ_ONLY, 0, need);
			map.order(ByteOrder.LITTLE_ENDIAN);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static int makeTexture(int w, int h, int internal, int format) {
		int id = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, w, h, 0, format, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		return id;
	}

	private static void makeTextures(int w, int h, int maskBpp) {
		if (colorTex != 0) {
			GL11.glDeleteTextures(colorTex);
			GL11.glDeleteTextures(maskTex);
		}
		int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		colorTex = makeTexture(w, h, GL11.GL_RGBA8, GL11.GL_RGBA);
		maskTex = maskBpp == 1 ? makeTexture(w, h, GL30.GL_R8, GL11.GL_RED) : makeTexture(w, h, GL11.GL_RGBA8, GL11.GL_RGBA);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
		texW = w;
		texH = h;
		texMaskBpp = maskBpp;
		lastSeq = -1;
	}

	/** ULTRAKILL is sending frames (one in the last quarter second). */
	public static boolean fresh() {
		return System.currentTimeMillis() - lastFrameAt < 250;
	}

	/** The camera's call, before the world is drawn: take the newest frame and use the view it was drawn from. */
	public static boolean updateForCamera() {
		taken = true;
		return update();
	}

	/** The overlay's call, after the world is drawn: the frame the camera took (so the two line up). */
	public static boolean updateForOverlay() {
		if (taken) {
			taken = false;
			return colorTex != 0;
		}
		return update();
	}

	/** Upload the newest frame if there is one. Returns false if there is nothing to show. */
	public static boolean update() {
		if (!open()) return false;
		int seq = map.getInt(0);
		int w = map.getInt(4);
		int h = map.getInt(8);
		int slot = map.getInt(16);
		int maskBpp = map.getInt(20);
		int version = map.getInt(24);
		if (version != 2 || w <= 0 || h <= 0 || (long) w * h > MAX_PIXELS || slot < 0 || slot >= SLOTS || (maskBpp != 1 && maskBpp != 4)) return false;
		if (colorTex == 0 || w != texW || h != texH || maskBpp != texMaskBpp) makeTextures(w, h, maskBpp);
		if (seq != lastSeq) {
			countRate(seq);
			lastSeq = seq;
			lastFrameAt = System.currentTimeMillis();
			long base = HEADER + slot * SLOT_BYTES;
			int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
			GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, view(base, (long) w * h * 4));
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskTex);
			GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h, maskBpp == 1 ? GL11.GL_RED : GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
				view(base + MAX_PIXELS * 4L, (long) w * h * maskBpp));
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
			frames++;
			if (map.getInt(60) == 1) {
				float[] p = new float[7];
				for (int i = 0; i < 7; i++) p[i] = map.getFloat(32 + i * 4);
				framePose = p;
			} else {
				framePose = null;
			}
		}
		return true;
	}

	/** A window onto the mapped file (no copy). */
	private static ByteBuffer view(long start, long length) {
		ByteBuffer b = map.duplicate();
		b.position((int) start);
		b.limit((int) (start + length));
		return b.slice();
	}

	// ------------------------------------------------------------ shaders

	private static final String VERT = "#version 120\nvarying vec2 tc;\nvoid main() {\n    gl_Position = ftransform();\n    tc = gl_MultiTexCoord0.xy;\n}\n";

	private static final String FRAG = "#version 120\n"
		+ "uniform sampler2D Sampler0;\nuniform sampler2D Sampler1;\nvarying vec2 tc;\n"
		+ "void main() {\n"
		+ "#ifdef MASK_RGBA\n"
		// Sampler1 is ULTRAKILL's render target before post-processing, drawn over transparent black: colour is premultiplied
		// light (additive tracers, flashes), alpha is how much of Minecraft is covered. out = ultrakill + minecraft * (1 - alpha)
		+ "    vec4 m = texture2D(Sampler1, tc);\n"
		+ "    float glow = max(m.r, max(m.g, m.b));\n"
		+ "    if (m.a == 0.0 && glow < 0.004) discard;\n"
		+ "    vec3 c = texture2D(Sampler0, tc).rgb;\n"
		+ "    if (max(c.r, max(c.g, c.b)) < 0.01 && glow > 0.03) c = m.rgb;\n"
		+ "    gl_FragColor = vec4(c, m.a);\n"
		+ "#else\n"
		+ "    float a = texture2D(Sampler1, tc).r;\n"
		+ "    if (a == 0.0) discard;\n"
		+ "    gl_FragColor = vec4(texture2D(Sampler0, tc).rgb, a);\n"
		+ "#endif\n"
		+ "}\n";

	private static int compile(int type, String src) {
		int s = GL20.glCreateShader(type);
		GL20.glShaderSource(s, src);
		GL20.glCompileShader(s);
		if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
			String log = GL20.glGetShaderInfoLog(s, 4096);
			GL20.glDeleteShader(s);
			throw new IllegalStateException("shader: " + log);
		}
		return s;
	}

	private static int link(boolean rgbaMask) {
		int v = compile(GL20.GL_VERTEX_SHADER, VERT);
		int f = compile(GL20.GL_FRAGMENT_SHADER, rgbaMask ? FRAG.replace("#version 120\n", "#version 120\n#define MASK_RGBA\n") : FRAG);
		int p = GL20.glCreateProgram();
		GL20.glAttachShader(p, v);
		GL20.glAttachShader(p, f);
		GL20.glLinkProgram(p);
		if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
			throw new IllegalStateException("program: " + GL20.glGetProgramInfoLog(p, 4096));
		}
		GL20.glDeleteShader(v);
		GL20.glDeleteShader(f);
		GL20.glUseProgram(p);
		GL20.glUniform1i(GL20.glGetUniformLocation(p, "Sampler0"), 0);
		GL20.glUniform1i(GL20.glGetUniformLocation(p, "Sampler1"), 1);
		GL20.glUseProgram(0);
		return p;
	}

	/** Draw the composited V1 layer over the whole GUI area (scaled up from ULTRAKILL's render size). */
	public static void draw(double guiWidth, double guiHeight) {
		if (colorTex == 0 || shaderFailed) return;
		try {
			if (program == 0) {
				program = link(false);
				programRgba = link(true);
			}
		} catch (RuntimeException e) {
			shaderFailed = true;
			System.err.println("[Ultracraft] " + e);
			return;
		}
		boolean rgba = texMaskBpp != 1;
		GlStateManager.pushMatrix();
		GlStateManager.disableLighting();
		GlStateManager.disableDepth();
		GlStateManager.disableAlpha();
		GlStateManager.enableBlend();
		if (rgba) GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		else GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager.color(1f, 1f, 1f, 1f);
		GL13.glActiveTexture(GL13.GL_TEXTURE1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskTex);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL20.glUseProgram(rgba ? programRgba : program);
		// rows arrive bottom-up from Unity: the top of the screen samples v = 1
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glTexCoord2f(0f, 1f);
		GL11.glVertex2d(0, 0);
		GL11.glTexCoord2f(0f, 0f);
		GL11.glVertex2d(0, guiHeight);
		GL11.glTexCoord2f(1f, 0f);
		GL11.glVertex2d(guiWidth, guiHeight);
		GL11.glTexCoord2f(1f, 1f);
		GL11.glVertex2d(guiWidth, 0);
		GL11.glEnd();
		GL20.glUseProgram(0);
		GL13.glActiveTexture(GL13.GL_TEXTURE1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GlStateManager.disableBlend();
		GlStateManager.enableAlpha();
		GlStateManager.enableDepth();
		GlStateManager.popMatrix();
	}

	// ------------------------------------------------------------ frame lock

	private static long lockFrames, lockWaitNs, lockTimeouts, lockStatsAt;
	private static int recentFrames, recentLate;
	private static long freeRunUntil;
	private static double ukRate = 60.0;
	private static int rateSeq = -1;
	private static long rateAt;
	private static long nextClockPing;
	private static volatile long clockOffsetUs = Long.MIN_VALUE;
	/** Set while this frame is waiting for ULTRAKILL. */
	public static boolean locked;

	private static void countRate(int seq) {
		long now = System.nanoTime();
		int n = seq - rateSeq;
		if (rateSeq >= 0 && n > 0 && n < 200 && now > rateAt) {
			double inst = n / ((now - rateAt) / 1e9);
			ukRate += (Math.min(inst, 1000.0) - ukRate) * 0.05;
		}
		rateSeq = seq;
		rateAt = now;
	}

	private static void pingClock() {
		long now = System.nanoTime() / 1000L;
		if (now < nextClockPing) return;
		nextClockPing = now + 5000000L;
		UkLink.send("CLOCK " + now);
	}

	/** CLOCK reply (on the link's thread): the offset, if the round trip was quick. */
	static void clockReply(String line) {
		String[] a = line.split(" ");
		if (a.length < 3) return;
		long sent = Long.parseLong(a[1]), uk = Long.parseLong(a[2]), back = System.nanoTime() / 1000L;
		if (back - sent > 2000) return;
		long offset = uk - (sent + back) / 2;
		long was = clockOffsetUs;
		clockOffsetUs = was == Long.MIN_VALUE ? offset : (was * 3 + offset) / 4;
	}

	public static String rateInfo() {
		return String.format(Locale.ROOT, "ukRate=%.0f waiting=%b", ukRate, UltracraftConfig.lockStep && System.nanoTime() >= freeRunUntil);
	}

	/**
	 * At the start of a Minecraft frame: wait for ULTRAKILL's next frame instead of drawing the same picture again, so every
	 * ULTRAKILL frame is shown once. Only while ULTRAKILL keeps up: missing its frame over and over (a slower computer) turns
	 * the wait into a stutter, so after that Minecraft stops waiting for a few seconds.
	 */
	public static void waitForNext(boolean active) {
		locked = false;
		if (colorTex == 0 || map == null || !fresh() || !active) return;
		if (!UltracraftConfig.lockStep || System.nanoTime() < freeRunUntil) {
			UkInput.sendInput(true);
			return;
		}
		locked = true;
		pingClock();
		long start = System.nanoTime();
		long deadline = start + (long) (1.5e9 / Math.max(30, UltracraftConfig.ukFpsNow()));
		long nextPoll = start + 1000000L;
		int seen = lastSeq;
		while (true) {
			if (map.getInt(0) != seen) break;
			long now = System.nanoTime();
			if (now >= deadline) {
				lockTimeouts++;
				recentLate++;
				break;
			}
			// keep the window answering while waiting (a few milliseconds at most)
			if (now >= nextPoll) {
				Display.processMessages();
				nextPoll = now + 1000000L;
			}
			Thread.yield();
		}
		UkInput.sendInput(true);
		long end = System.nanoTime();
		if (++recentFrames >= 60) {
			if (recentLate > 12) {
				freeRunUntil = end + 4000000000L;
				System.out.println("[Ultracraft] ULTRAKILL missed " + recentLate + " of the last 60 frames: not waiting for it for a few seconds");
			}
			recentFrames = recentLate = 0;
		}
		lockFrames++;
		lockWaitNs += end - start;
		if (lockStatsAt == 0) lockStatsAt = end;
		if (end - lockStatsAt > 30000000000L) {
			double secs = (end - lockStatsAt) / 1e9;
			System.out.println(String.format(Locale.ROOT, "[Ultracraft] [frames] %.0f fps in lock step with ULTRAKILL (cap %d), waited %.1f ms a frame, %d late",
				lockFrames / secs, UltracraftConfig.ukFpsNow(), lockWaitNs / 1e6 / Math.max(1, lockFrames), lockTimeouts));
			lockFrames = lockWaitNs = lockTimeouts = 0;
			lockStatsAt = end;
		}
	}
}
