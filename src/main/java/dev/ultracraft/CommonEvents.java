package dev.ultracraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Server-authoritative fall-damage protection for V1, Forge-side cheats, and world state. */
public final class CommonEvents {
	/**
	 * While a player is controlled as V1, cancel only Minecraft fall damage.
	 * Other damage sources, including void damage, remain enabled.
	 */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onV1Fall(LivingFallEvent event) {
		if (event.getEntityLiving() instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) event.getEntityLiving();
			if (player.world != null && !player.world.isRemote && ServerOps.isV1(player)) {
				event.setCanceled(true);
			}
		}
	}

	@SubscribeEvent
	public void onPlayerLogout(PlayerLoggedOutEvent event) {
		// Avoid leaving a stale V1 state entry if a player disconnects mid-session.
		ServerOps.clearV1(event.player);
	}

	@SubscribeEvent
	public void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		EntityPlayer player = event.player;
		if (player == null || player.world == null || player.world.isRemote) return;

		// Clear the server-side fall tracker while V1 is active so ordinary falling never
		// accumulates enough distance to apply damage if the fall event timing changes.
		if (ServerOps.isV1(player)) player.fallDistance = 0.0F;

		// This is intentionally server-side so hunger does not desync in LAN/Essential worlds.
		if (UltracraftConfig.cheatNeverHungry) {
			player.getFoodStats().setFoodLevel(20);
			player.getFoodStats().setFoodSaturationLevel(20.0F);
		}

		// The Forge side cannot accelerate ULTRAKILL's own V1 simulation. This potion only
		// affects ordinary Minecraft movement while the player is back in Steve mode.
		if (UltracraftConfig.cheatSuperSpeed && !ServerOps.isV1(player)
				&& !player.isSpectator() && player.ticksExisted % 20 == 0) {
			player.addPotionEffect(new PotionEffect(MobEffects.SPEED, 40, 1, true, false));
		}
	}
}
