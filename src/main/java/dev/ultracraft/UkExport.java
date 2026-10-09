package dev.ultracraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * What Minecraft's world looks like to ULTRAKILL: the solid blocks around V1 as boxes (exposed full cubes merged into
 * vertical runs, partial shapes as they are), the mobs as enemies' targets, and the projectiles in flight. Same lines as
 * the Fabric version (BLOCKS / ENTS / PROJS).
 */
public final class UkExport {
	private static final int RH = 12, RDOWN = 8, RUP = 14;
	private static String lastBlocks;
	private static long lastBlocksAt;

	private UkExport() {}

	public static void reset() {
		lastBlocks = null;
	}

	private static boolean full(World world, BlockPos pos, IBlockState s) {
		if (s.getMaterial().isReplaceable() && !s.isFullCube()) return false;
		AxisAlignedBB b = s.getCollisionBoundingBox(world, pos);
		if (b == null || b == net.minecraft.block.Block.NULL_AABB) return false;
		return s.isFullCube() && b.minX <= 0.001 && b.minY <= 0.001 && b.minZ <= 0.001 && b.maxX >= 0.999 && b.maxY >= 0.999 && b.maxZ >= 0.999;
	}

	/** Solid blocks around the player as boxes (BLOCKS x0,y0,z0,x1,y1,z1;...). */
	public static void exportBlocks(World world, BlockPos c) {
		if (world == null) return;
		StringBuilder sb = new StringBuilder(64 * 1024);
		sb.append("BLOCKS ");
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();
		for (int x = c.getX() - RH; x <= c.getX() + RH; x++) {
			for (int z = c.getZ() - RH; z <= c.getZ() + RH; z++) {
				int runStart = Integer.MIN_VALUE;
				for (int y = c.getY() - RDOWN; y <= c.getY() + RUP + 1; y++) {
					boolean fullExposed = false;
					if (y <= c.getY() + RUP && y >= 0 && y < 256) {
						m.setPos(x, y, z);
						IBlockState s = world.getBlockState(m);
						if (s.getMaterial() != net.minecraft.block.material.Material.AIR) {
							AxisAlignedBB cb = s.getCollisionBoundingBox(world, m);
							if (cb != null && cb != net.minecraft.block.Block.NULL_AABB) {
								if (full(world, m, s)) {
									fullExposed = exposed(world, x, y, z, m);
								} else {
									boxes.clear();
									// every collision box of the block (stairs, slabs, fences, ...), in world coordinates
									s.addCollisionBoxToList(world, m, new AxisAlignedBB(x - 1, y - 1, z - 1, x + 2, y + 2, z + 2), boxes, null, false);
									for (AxisAlignedBB b : boxes) {
										sb.append(String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f,%.4f,%.4f;", b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ));
									}
								}
							}
						}
					}
					if (fullExposed && runStart == Integer.MIN_VALUE) runStart = y;
					if (!fullExposed && runStart != Integer.MIN_VALUE) {
						sb.append(x).append(',').append(runStart).append(',').append(z).append(',')
							.append(x + 1).append(',').append(y).append(',').append(z + 1).append(';');
						runStart = Integer.MIN_VALUE;
					}
				}
			}
		}
		// unchanged surroundings: don't make ULTRAKILL parse (and garbage-collect) the same list again
		String s = sb.toString();
		long now = System.currentTimeMillis();
		if (s.equals(lastBlocks) && now - lastBlocksAt < 5000) return;
		lastBlocks = s;
		lastBlocksAt = now;
		UkLink.send(s);
	}

	private static boolean exposed(World world, int x, int y, int z, BlockPos.MutableBlockPos m) {
		int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int[] o : d) {
			m.setPos(x + o[0], y + o[1], z + o[2]);
			if (m.getY() < 0 || m.getY() > 255) continue;
			if (!full(world, m, world.getBlockState(m))) {
				m.setPos(x, y, z);
				return true;
			}
		}
		m.setPos(x, y, z);
		return false;
	}

	private static String typeName(Entity e) {
		ResourceLocation key = EntityList.getKey(e);
		if (key == null) return "unknown";
		// Use ResourceLocation.toString() and extract the path instead of relying on
		// a mapping-specific getter; this also behaves correctly for modded namespaces.
		String location = key.toString();
		int separator = location.indexOf(':');
		return separator >= 0 ? location.substring(separator + 1) : location;
	}

	/** Projectiles flying around V1 (not V1's own): ULTRAKILL tracks them for parries and decides hits on V1. */
	public static void exportProjectiles(World world, EntityPlayer self) {
		StringBuilder sb = new StringBuilder("PROJS ");
		for (Entity e : new ArrayList<Entity>(world.loadedEntityList)) {
			if (!(e instanceof IProjectile) || e instanceof EntityFishHook) continue;
			if (e instanceof EntityArrow && ((EntityArrow) e).shootingEntity == self) continue;
			if (e.getDistanceSq(self) > 48 * 48) continue;
			double vx = e.motionX, vy = e.motionY, vz = e.motionZ;
			if (vx * vx + vy * vy + vz * vz < 1e-4) continue; // stuck in a block
			sb.append(String.format(Locale.ROOT, "%d,%s,%.3f,%.3f,%.3f,%.4f,%.4f,%.4f,%.3f;",
				e.getEntityId(), typeName(e), e.posX, e.posY + e.height * 0.5, e.posZ, vx, vy, vz, Math.max(e.width, e.height)));
		}
		UkLink.send(sb.toString());
	}

	/** Mobs (and end crystals) around V1 (ENTS id,type,x,y,z,w,h,eye,hp,maxhp,hostile,yaw,onfire;...). */
	public static void exportEntities(World world, EntityPlayer self) {
		StringBuilder sb = new StringBuilder("ENTS ");
		for (Entity e : new ArrayList<Entity>(world.loadedEntityList)) {
			if (e instanceof EntityEnderCrystal) {
				if (e.isDead || e.getDistanceSq(self) > 80 * 80) continue;
				sb.append(String.format(Locale.ROOT, "%d,end_crystal,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,1.0,1.0,0,0.0;", e.getEntityId(), e.posX, e.posY, e.posZ,
					e.width, e.height, e.height * 0.5));
				continue;
			}
			if (e == self || !(e instanceof EntityLivingBase) || e.isDead) continue;
			EntityLivingBase le = (EntityLivingBase) e;
			if (!le.isEntityAlive()) continue;
			// a spectator isn't there to be seen or hit
			if (e instanceof EntityPlayer && ((EntityPlayer) e).isSpectator()) continue;
			if (e.getDistanceSq(self) > 80 * 80) continue;
			int hostile = e instanceof IMob ? 1 : 0;
			sb.append(String.format(Locale.ROOT, "%d,%s,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.1f,%.1f,%d,%.1f,%d;",
				e.getEntityId(), typeName(e), e.posX, e.posY, e.posZ, e.width, e.height, e.getEyeHeight(), le.getHealth(), le.getMaxHealth(), hostile,
				e.rotationYaw, e.isBurning() ? 1 : 0));
		}
		UkLink.send(sb.toString());
	}
}
