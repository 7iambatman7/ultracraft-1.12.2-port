package dev.ultracraft;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/**
 * ULTRAKILL's frames from the file UltraBridge maps (%TEMP%/ultracraft_frame2.bin). Each frame is the final colour
 * image plus V1's mask (alpha of its 3D render target, one byte per pixel), read back by ULTRAKILL's GPU straight into
 * a slot of the file. Both are uploaded straight from the mapped file into two textures and combined on the GPU by
 * the v1_composite shader while drawing.
 */
public final class UkFrame {
	private static final int HEADER = 64;
	private static final long MAX_PIXELS = 3840L * 2160L;
	private static final int SLOTS = 4;
	private static final long SLOT_BYTES = MAX_PIXELS * 8L;

	private static RenderPipeline pipeline(String name, boolean rgbaMask) {
		RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("ultracraft", "pipeline/" + name))
			.withVertexShader(Identifier.fromNamespaceAndPath("ultracraft", "core/v1_composite"))
			.withFragmentShader(Identifier.fromNamespaceAndPath("ultracraft", "core/v1_composite"))
			.withSampler("Sampler1");
		// RGBA mask: premultiplied, so ULTRAKILL's additive light (tracers, flashes) adds onto Minecraft
		if (rgbaMask) b = b.withShaderDefine("MASK_RGBA").withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA);
		return b.build();
	}

	/** RGB from Sampler0; coverage (and, for an RGBA mask, added light) from Sampler1; flipped by the UVs. */
	public static final RenderPipeline COMPOSITE = pipeline("v1_composite", false);
	public static final RenderPipeline COMPOSITE_RGBA_MASK = pipeline("v1_composite_rgba_mask", true);

	private static MappedByteBuffer map;
	private static GpuTexture color, mask;
	private static GpuTextureView colorView, maskView;
	private static int texW, texH, texMaskBpp;
	private static int lastSeq = -1;
	public static long frames;
	/**
	 * The view the shown frame was drawn from (eye x y z, yaw, pitch, roll, fov), or null. Minecraft's camera uses it
	 * so its world and ULTRAKILL's layer always line up, even mid-turn.
	 */
	public static volatile float[] framePose;

	private UkFrame() {}

	private static boolean open() {
		if (map != null) return true;
		Path p = Path.of(System.getProperty("java.io.tmpdir"), "ultracraft_frame2" + UltracraftConfig.instanceSuffix() + ".bin");
		try (RandomAccessFile f = new RandomAccessFile(p.toFile(), "r"); FileChannel ch = f.getChannel()) {
			long need = HEADER + SLOTS * SLOT_BYTES;
			if (ch.size() < need) return false;
			map = ch.map(FileChannel.MapMode.READ_ONLY, 0, need);
			map.order(ByteOrder.LITTLE_ENDIAN);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static void makeTextures(int w, int h, int maskBpp) {
		if (color != null) {
			colorView.close();
			color.close();
			maskView.close();
			mask.close();
		}
		GpuDevice device = RenderSystem.getDevice();
		int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;
		color = device.createTexture(() -> "ultracraft v1 colour", usage, TextureFormat.RGBA8, w, h, 1, 1);
		mask = device.createTexture(() -> "ultracraft v1 mask", usage, maskBpp == 1 ? TextureFormat.RED8 : TextureFormat.RGBA8, w, h, 1, 1);
		colorView = device.createTextureView(color);
		maskView = device.createTextureView(mask);
		texW = w;
		texH = h;
		texMaskBpp = maskBpp;
		lastSeq = -1;
	}

	// the camera took a frame for this render frame: the overlay shows that one, not a newer one
	private static boolean taken;

	/** The camera's call, before the world is drawn: take the newest frame and use the view it was drawn from. */
	public static boolean updateForCamera() {
		taken = true;
		return update();
	}

	/**
	 * The overlay's call, after the world is drawn: the frame the camera took, so ULTRAKILL's layer matches the view
	 * Minecraft's world was just drawn from (a newer frame, landed mid-render, would slide out of line while turning).
	 */
	public static boolean updateForOverlay() {
		if (taken) {
			taken = false;
			return color != null;
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
		if (color == null || w != texW || h != texH || maskBpp != texMaskBpp) makeTextures(w, h, maskBpp);
		if (seq != lastSeq) {
			lastSeq = seq;
			int base = (int) (HEADER + slot * SLOT_BYTES);
			var enc = RenderSystem.getDevice().createCommandEncoder();
			// straight from the mapped file to the GPU: no copy on our side
			enc.writeToTexture(color, map.slice(base, w * h * 4), NativeImage.Format.RGBA, 0, 0, 0, 0, w, h);
			enc.writeToTexture(mask, map.slice(base + (int) (MAX_PIXELS * 4), w * h * maskBpp),
				maskBpp == 1 ? NativeImage.Format.LUMINANCE : NativeImage.Format.RGBA, 0, 0, 0, 0, w, h);
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

	/** Draw the composited V1 layer over the whole GUI area (scaled up from ULTRAKILL's render size). */
	public static void draw(GuiGraphics ctx) {
		if (color == null) return;
		int w = ctx.guiWidth(), h = ctx.guiHeight();
		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		TextureSetup ts = TextureSetup.doubleTexture(colorView, sampler, maskView, sampler);
		RenderPipeline pipe = texMaskBpp == 1 ? COMPOSITE : COMPOSITE_RGBA_MASK;
		// rows arrive bottom-up from Unity: v runs 1 -> 0 to flip on the GPU
		ctx.guiRenderState.submitGuiElement(new BlitRenderState(pipe, ts, new Matrix3x2f(ctx.pose()), 0, 0, w, h, 0f, 1f, 1f, 0f, -1, null));
	}
}
