package dev.ultracraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;

/**
 * A boss fight with friends. A V1 who dies while a boss is here and another V1 is still fighting it isn't dead, only
 * down: they watch a teammate (Minecraft's spectator camera, with their ULTRAKILL drawing the fight from it) until the
 * boss is beaten or leaves, and then they're back on their feet beside that teammate, in a burst of light. Only when
 * every one of them is down is the fight lost: the boss leaves and they all die. Server thread.
 */
final class BossParty {
	private record Down(GameType mode, UUID watching) {}

	private static final Map<UUID, Down> downed = new HashMap<>();
	private static MinecraftServer server;

	private BossParty() {}

	static boolean isDown(ServerPlayer sp) {
		return downed.containsKey(sp.getUUID());
	}

	/** A V1 still standing in the fight, other than this one, nearest first. */
	private static ServerPlayer mate(ServerPlayer sp) {
		ServerPlayer best = null;
		double bestD = 160.0 * 160.0;
		for (ServerPlayer o : sp.level().getServer().getPlayerList().getPlayers()) {
			if (o == sp || !o.isAlive() || o.isSpectator() || downed.containsKey(o.getUUID()) || !UcNet.isV1(o) || o.level() != sp.level()) continue;
			double d = o.distanceToSqr(sp);
			if (d < bestD) {
				bestD = d;
				best = o;
			}
		}
		return best;
	}

	/** DEAD from this V1's ULTRAKILL during a boss fight: down instead of dead, if a teammate fights on. */
	static boolean tryDown(ServerPlayer sp) {
		if (!UkBosses.fighting() || downed.containsKey(sp.getUUID())) return false;
		ServerPlayer mate = mate(sp);
		if (mate == null) return false;
		server = sp.level().getServer();
		downed.put(sp.getUUID(), new Down(sp.gameMode.getGameModeForPlayer(), mate.getUUID()));
		sp.setHealth(sp.getMaxHealth());
		sp.setGameMode(GameType.SPECTATOR);
		sp.setCamera(mate);
		UcNet.send(sp, "C:DOWNED 1");
		sp.displayClientMessage(Component.literal("You're down: watching " + mate.getName().getString() + ". Beat the boss and you're back.")
			.withStyle(ChatFormatting.RED), false);
		for (ServerPlayer o : server.getPlayerList().getPlayers()) {
			if (o != sp) o.displayClientMessage(Component.literal(sp.getName().getString() + " is down! Beat the boss to bring them back.").withStyle(ChatFormatting.RED), false);
		}
		return true;
	}

	/** Every 10 ticks: each downed player keeps watching someone still up (and is kept near them, so they see the
	 * fight); with nobody left standing, the fight is lost. */
	static void tick(MinecraftServer s) {
		if (downed.isEmpty()) return;
		server = s;
		for (var e : new ArrayList<>(downed.entrySet())) {
			ServerPlayer d = s.getPlayerList().getPlayer(e.getKey());
			if (d == null) {
				downed.remove(e.getKey());
				continue;
			}
			ServerPlayer watching = s.getPlayerList().getPlayer(e.getValue().watching());
			if (watching == null || !watching.isAlive() || downed.containsKey(watching.getUUID()) || !UcNet.isV1(watching)) {
				watching = mate(d);
				if (watching == null) {
					everyoneDown(s);
					return;
				}
				downed.put(e.getKey(), new Down(e.getValue().mode(), watching.getUUID()));
			}
			if (d.getCamera() != watching) d.setCamera(watching);
			if (d.level() != watching.level() || d.distanceToSqr(watching) > 16.0) {
				d.teleportTo(watching.level(), watching.getX(), watching.getY(), watching.getZ(), Set.of(), d.getYRot(), d.getXRot(), false);
			}
		}
	}

	/** Nobody's left standing: the boss leaves and the downed die with the last one. */
	private static void everyoneDown(MinecraftServer s) {
		List<UUID> all = new ArrayList<>(downed.keySet());
		Map<UUID, Down> was = new HashMap<>(downed);
		downed.clear();
		UkBosses.forceEnd(s, "everyone is dead");
		for (UUID id : all) {
			ServerPlayer d = s.getPlayerList().getPlayer(id);
			if (d == null) continue;
			d.setCamera(d);
			d.setGameMode(was.get(id).mode());
			UcNet.send(d, "C:DOWNED 0 dead");
			d.kill(d.level());
		}
	}

	/** The fight's over and somebody won it (or the boss went): everyone who was down gets up. */
	static void reviveAll() {
		if (downed.isEmpty() || server == null) return;
		Map<UUID, Down> was = new HashMap<>(downed);
		downed.clear();
		for (var e : was.entrySet()) {
			ServerPlayer d = server.getPlayerList().getPlayer(e.getKey());
			if (d == null) continue;
			ServerPlayer mate = server.getPlayerList().getPlayer(e.getValue().watching());
			if (mate == null) mate = mate(d);
			d.setCamera(d);
			d.setGameMode(e.getValue().mode());
			if (mate != null) {
				d.teleportTo(mate.level(), mate.getX() + 1.0, mate.getY(), mate.getZ(), Set.of(), mate.getYRot(), 0f, false);
			}
			d.setHealth(d.getMaxHealth());
			d.fallDistance = 0;
			UcNet.send(d, "C:DOWNED 0");
			ServerLevel level = d.level();
			// back, in a burst of light
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, d.getX(), d.getY() + 1.0, d.getZ(), 120, 0.6, 1.0, 0.6, 0.6);
			level.sendParticles(ParticleTypes.END_ROD, d.getX(), d.getY() + 1.0, d.getZ(), 40, 0.4, 1.2, 0.4, 0.15);
			level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1f);
			d.displayClientMessage(Component.literal("You're back!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
		}
	}

	/** The world closed. */
	static void reset() {
		downed.clear();
		server = null;
	}
}
