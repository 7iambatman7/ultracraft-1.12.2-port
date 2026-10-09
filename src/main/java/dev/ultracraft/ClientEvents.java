package dev.ultracraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/** Minecraft's side of V1 mode: ticks, the camera, the V1 layer over the HUD, and the mouse. */
@SideOnly(Side.CLIENT)
public final class ClientEvents {
	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Ultracraft.clientTick(Minecraft.getMinecraft());
	}

	/**
	 * While V1's guns are out, let UkInput read the physical keyboard but consume matching Minecraft key bindings.
	 * Without this, a single Q/number/E press is sent to ULTRAKILL and also drops an item, changes the hotbar, or opens
	 * Minecraft inventory. F8 and V remain available for the two Ultracraft controls.
	 */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onKeyInput(InputEvent.KeyInputEvent event) {
		if (!Ultracraft.active) return;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.gameSettings == null) return;
		int key = Keyboard.getEventKey();
		if (key == Keyboard.KEY_NONE) return;
		GameSettings gs = mc.gameSettings;

		// Q is reserved by V1 mode even when Minecraft hands are visible. Otherwise a
		// physical Q press leaks through to Minecraft and drops the selected block/item.
		// Keep the rest of Steve's controls when Minecraft hands are enabled.
		if (Ultracraft.hands) {
			suppressBinding(gs.keyBindDrop, key);
			return;
		}

		suppressBinding(gs.keyBindForward, key);
		suppressBinding(gs.keyBindBack, key);
		suppressBinding(gs.keyBindLeft, key);
		suppressBinding(gs.keyBindRight, key);
		suppressBinding(gs.keyBindJump, key);
		suppressBinding(gs.keyBindSneak, key);
		suppressBinding(gs.keyBindSprint, key);
		suppressBinding(gs.keyBindAttack, key);
		suppressBinding(gs.keyBindUseItem, key);
		suppressBinding(gs.keyBindDrop, key);
		suppressBinding(gs.keyBindInventory, key);
		suppressBinding(gs.keyBindPickBlock, key);
		suppressBinding(gs.keyBindSwapHands, key);
		suppressBinding(gs.keyBindSmoothCamera, key);
		for (KeyBinding binding : gs.keyBindsHotbar) suppressBinding(binding, key);
	}

	private static void suppressBinding(KeyBinding binding, int eventKey) {
		if (binding == null || binding.getKeyCode() != eventKey) return;
		KeyBinding.setKeyBindState(eventKey, false);
		while (binding.isPressed()) {
			// Drain queued edge presses so Minecraft doesn't process this same press later in runTick.
		}
	}

	/**
	 * Before each frame: wait for ULTRAKILL's next one (lock step), take it, and put Minecraft's player exactly where
	 * ULTRAKILL's camera was for it, so Minecraft's world and ULTRAKILL's layer (enemies, effects) never slide apart.
	 */
	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		if (event.phase != TickEvent.Phase.START) return;
		Minecraft mc = Minecraft.getMinecraft();
		UkFrame.waitForNext(Ultracraft.active);
		if (!Ultracraft.active) return;
		EntityPlayerSP p = mc.player;
		if (p == null || mc.world == null) return;
		if (!UkFrame.updateForCamera()) return;
		float[] f = UkFrame.framePose;
		if (f == null || !UkFrame.fresh()) return;
		// the frame's eye position and look
		double eyeHeight = p.getEyeHeight();
		double x = f[0], y = f[1] - eyeHeight, z = f[2];
		p.setPosition(x, y, z);
		p.lastTickPosX = p.prevPosX = x;
		p.lastTickPosY = p.prevPosY = y;
		p.lastTickPosZ = p.prevPosZ = z;
		p.rotationYaw = p.prevRotationYaw = f[3];
		p.rotationPitch = p.prevRotationPitch = f[4];
		p.rotationYawHead = p.prevRotationYawHead = f[3];
		p.renderYawOffset = p.prevRenderYawOffset = f[3];
	}

	@SubscribeEvent
	public void onCamera(EntityViewRenderEvent.CameraSetup event) {
		if (!Ultracraft.active) return;
		float[] f = UkFrame.framePose;
		if (f == null || !UkFrame.fresh()) return;
		// ULTRAKILL's camera leans as V1 strafes and slides (Minecraft's has no lean of its own)
		event.setYaw(f[3] + 180f);
		event.setPitch(f[4]);
		event.setRoll(f[5]);
	}

	@SubscribeEvent
	public void onFov(EntityViewRenderEvent.FOVModifier event) {
		if (!Ultracraft.active) return;
		float[] f = UkFrame.framePose;
		if (f == null || !UkFrame.fresh() || f[6] < 10f || f[6] > 170f) return;
		event.setFOV(f[6]);
	}

	/** V1 mode replaces Minecraft's HUD (health is ULTRAKILL's); with Minecraft hands out, the hotbar and crosshair come back. */
	@SubscribeEvent
	public void onOverlayPre(RenderGameOverlayEvent.Pre event) {
		if (!Ultracraft.active) return;
		switch (event.getType()) {
			case HEALTH:
			case HEALTHMOUNT:
			case ARMOR:
			case FOOD:
			case EXPERIENCE:
			case JUMPBAR:
				event.setCanceled(true);
				break;
			case HOTBAR:
			case CROSSHAIRS:
				if (!Ultracraft.hands) event.setCanceled(true);
				break;
			default:
				break;
		}
	}

	/** The V1 layer over everything Minecraft's HUD drew. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onOverlayPost(RenderGameOverlayEvent.Post event) {
		if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
		Minecraft mc = Minecraft.getMinecraft();
		ScaledResolution sr = event.getResolution();
		if (UkLink.connected && !UkInput.sentEarly) UkInput.sendInput(false);
		UkInput.sentEarly = false;
		if (!Ultracraft.active) {
			String s = Ultracraft.status();
			if (s != null && mc.player != null) mc.fontRenderer.drawStringWithShadow(s, 4, 4, 0xFFFF00);
			return;
		}
		if (UkFrame.updateForOverlay()) UkFrame.draw(sr.getScaledWidth_double(), sr.getScaledHeight_double());
	}

	/** Minecraft's own hands stay out of V1's way (the guns are ULTRAKILL's). */
	@SubscribeEvent
	public void onHand(RenderHandEvent event) {
		if (Ultracraft.active && !Ultracraft.hands) event.setCanceled(true);
	}

	/** Wheel and clicks are ULTRAKILL's while V1 (clicks are Minecraft's again with Minecraft hands out). */
	@SubscribeEvent
	public void onMouse(MouseEvent event) {
		if (!Ultracraft.active) return;
		int dw = event.getDwheel();
		if (dw != 0) {
			UkInput.wheel += dw;
			event.setCanceled(true);
			return;
		}
		if (Ultracraft.uiMode) {
			// an ULTRAKILL menu is open: a click must not give the mouse back to Minecraft
			if (event.getButton() != -1) event.setCanceled(true);
			return;
		}
		if (event.getButton() != -1 && !Ultracraft.hands) event.setCanceled(true);
	}
}
