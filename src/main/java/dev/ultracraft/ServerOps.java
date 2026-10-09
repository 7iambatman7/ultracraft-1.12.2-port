package dev.ultracraft;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.init.Blocks;

/**
 * What a player's ULTRAKILL did to the shared world, applied on the server as that player: damage to mobs, explosions,
 * fire, knockback, and V1's health mirrored in Minecraft's hearts. Lines are the Fabric version's (DMG, BOOM, SLAM, FIRE,
 * MKNOCK, WHIP, SHURT, VITALS, DEAD, V1).
 */
public final class ServerOps {
	/** ULTRAKILL damage (100 HP scale) to Minecraft's (about 20 HP scale). */
	private static final float V1_HIT = 6f;
	/** Players who are V1 right now. */
	private static final Set<UUID> V1S = ConcurrentHashMap.newKeySet();

	private ServerOps() {}

	public static boolean isV1(EntityPlayer p) {
		return V1S.contains(p.getUniqueID());
	}

	/** Clear transient server-side V1 state when its player leaves the server. */
	public static void clearV1(EntityPlayer p) {
		if (p != null) V1S.remove(p.getUniqueID());
	}

	private static boolean breaks(boolean enemy) {
		return enemy ? UltracraftConfig.enemyBlockDamage : UltracraftConfig.playerBlockDamage;
	}

	static void handle(EntityPlayerMP sp, String line) {
		String[] a = line.split(" ");
		String cmd = a[0];
		String rest = line.length() > cmd.length() ? line.substring(cmd.length() + 1) : "";
		World world = sp.world;
		if (cmd.equals("V1")) {
			if (a.length > 1 && a[1].equals("1")) V1S.add(sp.getUniqueID());
			else V1S.remove(sp.getUniqueID());
		} else if (cmd.equals("DEAD")) {
			// V1 died in ULTRAKILL: Minecraft's player dies too (respawn goes through Minecraft)
			V1S.remove(sp.getUniqueID());
			sp.attackEntityFrom(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
		} else if (cmd.equals("VITALS")) {
			// VITALS hp max dead moved carried: Minecraft's hearts mirror V1's health; walking makes V1 hungry
			int hp = Integer.parseInt(a[1]), max = Integer.parseInt(a[2]);
			boolean dead = a[3].equals("1"), carried = a.length > 5 && a[5].equals("1");
			double moved = Double.parseDouble(a[4]);
			if (!dead && max > 0 && sp.isEntityAlive()) {
				float h = MathHelper.clamp(hp * sp.getMaxHealth() / max, 1f, sp.getMaxHealth());
				if (Math.abs(sp.getHealth() - h) > 0.01f) sp.setHealth(h);
			}
			if (moved > 0.01 && moved < 8 && !carried && !UltracraftConfig.cheatNeverHungry) {
				sp.addExhaustion((float) (moved * 0.02));
			}
		} else if (cmd.equals("SHURT")) {
			// as Steve, an ULTRAKILL enemy's hit (ULTRAKILL's 100 HP -> Minecraft's 20)
			float dmg = Float.parseFloat(rest.trim()) / 5f;
			if (dmg > 0 && sp.isEntityAlive() && !isV1(sp)) sp.attackEntityFrom(DamageSource.GENERIC, dmg);
		} else if (cmd.equals("DMG")) {
			damage(sp, world, a);
		} else if (cmd.equals("BOOM")) {
			// an ULTRAKILL explosion: blow the same spot up in Minecraft; "e": an enemy's
			double x = Double.parseDouble(a[1]), y = Double.parseDouble(a[2]), z = Double.parseDouble(a[3]);
			String kind = a.length > 5 ? a[5] : "";
			float size = Float.parseFloat(a[4]);
			boolean blocks = breaks(kind.equals("e"));
			if (kind.equals("2")) {
				// the mini nuke: a burning crater twice anything else's size
				float power = Math.max(18f, Math.min(24f, size * 2f));
				world.newExplosion(sp, x, y, z, power, blocks, blocks);
				return;
			}
			float power = Math.max(1f, Math.min(kind.equals("1") ? 16f : 10f, size));
			world.newExplosion(sp, x, y, z, power, false, blocks);
		} else if (cmd.equals("SLAM")) {
			// SLAM x y z drop: V1 slammed into the ground after falling `drop` blocks; from high up it leaves a crater
			double x = Double.parseDouble(a[1]), y = Double.parseDouble(a[2]), z = Double.parseDouble(a[3]);
			float power = Math.min(16f, Float.parseFloat(a[4]) / 12.5f);
			if (power < 1f) return;
			world.newExplosion(sp, x, y - 0.5, z, power, false, breaks(false));
		} else if (cmd.equals("FIRE")) {
			// burning gasoline, or a Streetcleaner's flames ("e"): Minecraft fire there
			if (!breaks(a.length > 4 && a[4].equals("e"))) return;
			int x = MathHelper.floor(Double.parseDouble(a[1])), y = MathHelper.floor(Double.parseDouble(a[2])), z = MathHelper.floor(Double.parseDouble(a[3]));
			int[] dys = {0, 1, -1};
			for (int dy : dys) {
				BlockPos p = new BlockPos(x, y + dy, z);
				if (world.isAirBlock(p) && Blocks.FIRE.canPlaceBlockAt(world, p)) {
					world.setBlockState(p, Blocks.FIRE.getDefaultState());
					break;
				}
			}
		} else if (cmd.equals("MKNOCK")) {
			// MKNOCK id vx vy vz: one of ULTRAKILL's enemies launched this mob as it would launch V1; it lands without a fall
			Entity e = world.getEntityByID(Integer.parseInt(a[1]));
			if (e instanceof EntityLivingBase && !e.isDead && !(e instanceof EntityPlayer)) {
				e.motionX = Double.parseDouble(a[2]);
				e.motionY = Double.parseDouble(a[3]);
				e.motionZ = Double.parseDouble(a[4]);
				e.velocityChanged = true;
				e.fallDistance = 0;
			}
		} else if (cmd.equals("WHIP")) {
			// WHIP id vx vy vz: V1's Whiplash reels this mob in
			Entity e = world.getEntityByID(Integer.parseInt(a[1]));
			if (e instanceof EntityLivingBase && !e.isDead && !(e instanceof EntityPlayer && isV1((EntityPlayer) e))) {
				e.motionX = Double.parseDouble(a[2]);
				e.motionY = Double.parseDouble(a[3]);
				e.motionZ = Double.parseDouble(a[4]);
				e.fallDistance = 0;
				e.velocityChanged = true;
			}
		}
		// HIT, RAIL (block damage), P, gear, upgrades, bosses, arenas, the Cyber Grind: not in the 1.12.2 port yet
	}

	private static void damage(EntityPlayerMP sp, World world, String[] a) {
		int id = Integer.parseInt(a[1]);
		float amount = Float.parseFloat(a[2]);
		boolean explosion = a.length > 4 && a[4].equals("1");
		boolean parry = a.length > 5 && a[5].equals("1");
		boolean fire = a.length > 7 && a[7].equals("1");
		Entity e = world.getEntityByID(id);
		if (e instanceof EntityEnderCrystal) {
			// an end crystal V1 shot or blew up: it goes off
			if (!e.isDead) e.attackEntityFrom(explosion ? DamageSource.causeExplosionDamage(sp) : DamageSource.causePlayerDamage(sp), Math.max(1f, amount * 10f));
			return;
		}
		if (!(e instanceof EntityLivingBase) || e.isDead) return;
		EntityLivingBase le = (EntityLivingBase) e;
		if (!le.isEntityAlive()) return;
		// V1s don't hurt each other
		if (le instanceof EntityPlayer && isV1((EntityPlayer) le)) return;
		// ULTRAKILL has no invulnerability frames
		le.hurtResistantTime = 0;
		DamageSource src = fire ? DamageSource.ON_FIRE : explosion ? DamageSource.causeExplosionDamage(sp) : DamageSource.causePlayerDamage(sp);
		if (fire && !le.isImmuneToFire()) le.setFire(3);
		le.attackEntityFrom(src, amount * (parry ? 10f : V1_HIT));
		if (parry && le.isEntityAlive()) {
			// a parried mob is sent flying
			double dx = le.posX - sp.posX, dz = le.posZ - sp.posZ;
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len > 1e-4) {
				le.motionX += dx / len * 1.6;
				le.motionY += 0.6;
				le.motionZ += dz / len * 1.6;
				le.velocityChanged = true;
			}
		}
	}
}
