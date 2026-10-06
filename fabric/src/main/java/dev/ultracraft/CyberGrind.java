package dev.ultracraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Cyber Grind, started from a shop's screen: instead of ULTRAKILL's own arena, waves of ULTRAKILL's enemies come
 * for V1, each wave bigger and nastier than the last. A wave is cleared when its last enemy dies; the next comes a few
 * seconds later. With Cyber Grind Arenas on, V1 (and the other V1s by the shop) are taken into the Grind's own arenas
 * (GrindArenas): a few waves in each, then on to the next once it's cleared; the temporary shop in each is the way out.
 * Otherwise the waves come round the shop it was started from. The run ends when V1 dies, leaves (80 blocks from the
 * shop, or the arenas), turns back into Steve, or presses Leave on a shop's screen. The best wave is kept. Runs on the server thread (ULTRAKILL's messages come
 * in on the client thread and are handed over).
 */
final class CyberGrind {
	/** An enemy, what it costs out of a wave's budget, the first wave it can come in, whether it flies. */
	private record Kind(String type, int cost, int from, boolean flies) {}

	private static final List<Kind> KINDS = List.of(
		new Kind("Filth", 1, 1, false), new Kind("Stray", 2, 1, false), new Kind("Drone", 2, 2, true), new Kind("Schism", 3, 3, false),
		new Kind("Soldier", 3, 4, false), new Kind("Streetcleaner", 5, 5, false), new Kind("Mannequin", 4, 6, false), new Kind("Virtue", 6, 7, true),
		new Kind("Cerberus", 7, 8, false), new Kind("Swordsmachine", 8, 9, false), new Kind("MaliciousFace", 8, 10, true), new Kind("Stalker", 6, 12, false),
		new Kind("Mindflayer", 12, 14, false), new Kind("Gutterman", 12, 16, false), new Kind("Idol", 10, 18, false), new Kind("Power", 18, 22, false));

	private static final double LEAVE = 80.0;
	/** Waves in each arena before the run moves on to the next; every so many waves is a boss. */
	private static final int WAVES_PER_ARENA = 5, BOSS_EVERY = 15;
	/** This wave is a boss (UkBosses' arena fight): it's cleared when the boss is beaten. */
	private static boolean bossWave;

	static volatile boolean running;
	static volatile int wave;
	private static Vec3 center = Vec3.ZERO;
	private static long shopId;
	private static int left;
	/** Ticks until the next wave comes (after one is cleared), or -1 while a wave is on. */
	private static int countdown = -1;
	/** Ticks since the wave started (a wave whose enemies never arrived, or wandered off, is called after a while). */
	private static int waveTicks;
	/** The player who started it (their ULTRAKILL runs the waves; the others see them and can join in). */
	private static java.util.UUID runner;
	private static net.minecraft.server.MinecraftServer server;

	/** In the Grind's own arenas: which one, how many of its waves are cleared, and whether the next is due. */
	private static boolean arenaMode, nextArenaDue;
	private static int arenaIndex, wavesHere;
	private static GrindArenas.Built arena;
	/** This run's arenas, shuffled: each run starts somewhere new and goes through all of them before any comes back. */
	private static final List<Integer> order = new ArrayList<>();
	private static int orderPos;
	/** Who came along, and where each goes back to. */
	private record Return(ResourceKey<Level> level, Vec3 at, float yaw, float pitch) {}
	private static final Map<UUID, Return> party = new HashMap<>();

	private CyberGrind() {}

	static boolean isRunner(ServerPlayer sp) {
		return running && runner != null && runner.equals(sp.getUUID());
	}

	static void stopIfRunner(ServerPlayer sp, String why) {
		if (isRunner(sp)) stop(sp, why);
	}

	/** The world closed. */
	static void reset() {
		running = false;
		runner = null;
		server = null;
		arenaMode = false;
		nextArenaDue = false;
		bossWave = false;
		arena = null;
		party.clear();
	}

	private static ServerPlayer runnerPlayer() {
		return server == null || runner == null ? null : server.getPlayerList().getPlayer(runner);
	}

	/** GRIND id from a shop's screen: start a run around that shop, or end the one going on. */
	static void toggle(ServerPlayer sp, long id) {
		if (running) {
			stop(sp, "left");
			return;
		}
		if (UkBosses.busy()) {
			sp.displayClientMessage(Component.literal("Not now: a boss is coming for you."), true);
			return;
		}
		BlockPos anchor = BlockPos.of(id);
		var state = sp.level().getBlockState(anchor);
		if (!state.is(UltracraftCommon.UK_SHOP)) return;
		Direction f = state.getValue(UkShopBlock.FACING), right = UkShopBlock.right(f);
		// the arena: the open ground in front of the screen
		center = new Vec3(anchor.getX() + 0.5 + 0.5 * right.getStepX() + f.getStepX() * 6, anchor.getY(), anchor.getZ() + 0.5 + 0.5 * right.getStepZ() + f.getStepZ() * 6);
		shopId = id;
		runner = sp.getUUID();
		server = sp.level().getServer();
		running = true;
		wave = 0;
		left = 0;
		countdown = 60;
		arenaMode = false;
		nextArenaDue = false;
		party.clear();
		if (UltracraftConfig.grindArenas && sp.level().dimension() != GrindArenas.DIMENSION && GrindArenas.level(server) != null) {
			// into the Grind's own arenas, with every V1 standing by the shop
			arenaMode = true;
			for (ServerPlayer o : server.getPlayerList().getPlayers()) {
				if (o == sp || (UcNet.isV1(o) && o.level() == sp.level() && o.position().distanceTo(center) < 16)) {
					party.put(o.getUUID(), new Return(o.level().dimension(), o.position(), o.getYRot(), o.getXRot()));
				}
			}
			hud("THE CYBER GRIND");
			shuffle(-1);
			enterArena(order.get(0));
			sendState();
			return;
		}
		hud("THE CYBER GRIND");
		sp.displayClientMessage(Component.literal("The Cyber Grind: waves of enemies around this shop. Dying or leaving ends it."), true);
		sendState();
	}

	/** /uc grind start or arena n: a run in the Grind's arenas from arena index on (-1: any), for this V1 alone. */
	static boolean startArenas(ServerPlayer sp, int index) {
		if (running || !UcNet.isV1(sp)) return false;
		server = sp.level().getServer();
		if (GrindArenas.level(server) == null) return false;
		runner = sp.getUUID();
		running = true;
		wave = 0;
		left = 0;
		arenaMode = true;
		nextArenaDue = false;
		party.clear();
		party.put(sp.getUUID(), new Return(sp.level().dimension(), sp.position(), sp.getYRot(), sp.getXRot()));
		hud("THE CYBER GRIND");
		shuffle(index < 0 ? -1 : Math.floorMod(index, GrindArenas.ARENAS.size()));
		enterArena(order.get(0));
		sendState();
		return true;
	}

	/** A new order of arenas for the run, first first (or any, for -1). */
	private static void shuffle(int first) {
		order.clear();
		for (int i = 0; i < GrindArenas.ARENAS.size(); i++) order.add(i);
		java.util.Collections.shuffle(order, new java.util.Random(server.overworld().getRandom().nextLong()));
		if (first >= 0) {
			order.remove(Integer.valueOf(first));
			order.add(0, first);
		}
		orderPos = 0;
	}

	/** The arena after this one: the next in the order, or, once all have been, a new order (not this one again). */
	private static int nextArena(boolean take) {
		if (orderPos + 1 < order.size()) {
			if (take) orderPos++;
			return order.get(take ? orderPos : orderPos + 1);
		}
		if (!take) return -1;
		int last = arenaIndex;
		shuffle(-1);
		if (order.get(0) == last) java.util.Collections.swap(order, 0, order.size() - 1);
		return order.get(0);
	}

	/** Arena index, built fresh, with the party in it; its first wave after a few seconds. */
	private static void enterArena(int index) {
		ServerLevel level = GrindArenas.level(server);
		if (level == null) return;
		if (arena != null && arena.shop() != null) GrindArenas.removeShop(level, arena.shop(), arena.shopFacing());
		arenaIndex = index;
		wavesHere = 0;
		RandomSource random = server.overworld().getRandom();
		arena = GrindArenas.build(level, index, random.nextLong());
		center = arena.center();
		shopId = arena.shop() != null ? arena.shop().asLong() : 0L;
		countdown = 100;
		Vec3 to = arena.spawn();
		float yaw = (float) Math.toDegrees(Math.atan2(-(center.x - to.x), center.z - to.z));
		for (UUID id : party.keySet()) {
			ServerPlayer o = server.getPlayerList().getPlayer(id);
			if (o == null || !o.isAlive()) continue;
			o.teleportTo(level, to.x, to.y, to.z, Set.of(), yaw, 0f, true);
			o.fallDistance = 0;
			// the arena's name, big, as ULTRAKILL opens a level
			o.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
			o.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(arena.def().layer() + "  -  ARENA " + (index + 1) + " / " + GrindArenas.ARENAS.size())
				.withStyle(ChatFormatting.GRAY)));
			o.connection.send(new ClientboundSetTitleTextPacket(Component.literal(arena.def().title()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
			o.displayClientMessage(Component.literal("Clear its waves to move on. Leave the Cyber Grind from its shop, to the " + GrindArenas.direction(arena)
				+ " (under the column of lights).").withStyle(ChatFormatting.GRAY), false);
		}
		hud("ARENA " + (index + 1) + ": " + arena.def().title());
		// ULTRAKILL's own sky over it
		String sky = "SKY " + GrindArenas.sky(index, random);
		for (UUID id : party.keySet()) {
			ServerPlayer o = server.getPlayerList().getPlayer(id);
			if (o != null) UcNet.send(o, sky);
		}
		org.slf4j.LoggerFactory.getLogger("ultracraft").info("Cyber Grind arena {} ({}), shop at {}", index + 1, arena.def().title(), arena.shop());
	}

	/** Every 10 ticks while V1 is active. */
	static void tick(ServerPlayer sp) {
		if (!running) return;
		if (arenaMode) {
			if (sp.level().dimension() != GrindArenas.DIMENSION) {
				stop(sp, "left the arenas");
				return;
			}
			// off the edge: back onto the arena, as ULTRAKILL puts V1 back after a fall
			for (UUID id : party.keySet()) {
				ServerPlayer o = server.getPlayerList().getPlayer(id);
				if (o != null && o.isAlive() && o.level().dimension() == GrindArenas.DIMENSION && o.getY() < center.y - 24) {
					Vec3 to = arena.spawn();
					o.teleportTo(o.level(), to.x, to.y, to.z, Set.of(), o.getYRot(), o.getXRot(), true);
					o.fallDistance = 0;
				}
			}
		} else if (sp.position().distanceTo(center) > LEAVE || !sp.level().getBlockState(BlockPos.of(shopId)).is(UltracraftCommon.UK_SHOP)) {
			stop(sp, "left");
			return;
		}
		if (countdown >= 0) {
			countdown -= 10;
			if (countdown < 0) {
				if (nextArenaDue) {
					// this arena is done: on to the next
					nextArenaDue = false;
					enterArena(nextArena(true));
				} else {
					startWave(sp);
				}
			}
			return;
		}
		waveTicks += 10;
		if (bossWave) {
			// the boss went away without being beaten (it lost V1, got bored): the wave's over all the same
			if (!UkBosses.busy()) {
				bossWave = false;
				cleared(sp);
			}
			return;
		}
		// stragglers stuck somewhere they can't reach V1: after two minutes the wave is called
		if (left <= 0 || waveTicks > 2400) cleared(sp);
	}

	private static void startWave(ServerPlayer sp) {
		wave++;
		waveTicks = 0;
		ServerLevel level = sp.level();
		RandomSource random = level.getRandom();
		if (wave % BOSS_EVERY == 0 && bossWave(sp, level, random)) return;
		// the budget grows with every wave; the biggest enemies come in later
		int budget = 4 + wave * 3 + wave * wave / 4;
		List<Kind> spawn = new ArrayList<>();
		int guard = 0;
		while (budget > 0 && spawn.size() < 4 + wave * 2 && guard++ < 200) {
			List<Kind> can = new ArrayList<>();
			for (Kind k : KINDS) if (k.from <= wave && k.cost <= budget) can.add(k);
			if (can.isEmpty()) break;
			// the newest enemies are the likeliest
			Kind k = can.get(Math.max(0, can.size() - 1 - (int) Math.floor(Math.abs(random.nextGaussian()) * can.size() / 2.0)));
			spawn.add(k);
			budget -= k.cost;
		}
		left = 0;
		StringBuilder log = new StringBuilder();
		for (Kind k : spawn) {
			Vec3 at = spot(level, random);
			log.append(String.format(Locale.ROOT, " %s@%.0f,%.0f,%.0f", k.type, at.x, at.y, at.z));
			double y = at.y + (k.flies ? 3.0 + random.nextInt(3) : 0.0);
			UcNet.send(sp, String.format(Locale.ROOT, "SPAWNAT %s %.2f %.2f %.2f 2", k.type, at.x, y, at.z));
			left++;
		}
		org.slf4j.LoggerFactory.getLogger("ultracraft").info("Cyber Grind wave {}:{}", wave, log);
		hud("WAVE " + wave);
		sendState();
	}

	/**
	 * Somewhere around the arena an enemy can stand, on the ground: 8 to 24 blocks from the middle round a shop,
	 * anywhere but the very edge in one of the Grind's arenas.
	 */
	private static Vec3 spot(ServerLevel level, RandomSource random) {
		double near = arenaMode ? 4.0 : 8.0, far = arenaMode && arena != null ? Math.max(8.0, arena.r() - 4.0) : 24.0;
		for (int attempt = 0; attempt < 40; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0, dist = near + random.nextDouble() * (far - near);
			int x = (int) Math.floor(center.x + Math.cos(angle) * dist), z = (int) Math.floor(center.z + Math.sin(angle) * dist);
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, (int) Math.floor(center.y) + (arenaMode ? 3 : 6), z);
			for (int y = m.getY(); y > center.y - 10; y--) {
				m.setY(y);
				if (!level.getBlockState(m).isFaceSturdy(level, m, Direction.UP)) continue;
				BlockPos feet = m.above();
				// in the Grind's arenas, only on the floor itself (an enemy put on a wall, pillar, platform or raised
				// square can't get down to V1), with room round it for the big ones
				if (arenaMode && arena != null && !GrindArenas.standable(arena, x, z, y)) break;
				double room = arenaMode ? 1.0 : 0.0;
				if (level.noCollision(new AABB(feet.getX() + 0.1 - room, feet.getY(), feet.getZ() + 0.1 - room, feet.getX() + 0.9 + room, feet.getY() + 3.0, feet.getZ() + 0.9 + room))
					&& level.getFluidState(feet).isEmpty()) {
					return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
				}
				break;
			}
		}
		return center;
	}

	/** Every 15th wave: a boss, harder the further in (tier 1 at wave 15, up to 5), with the arena's theme. */
	private static boolean bossWave(ServerPlayer sp, ServerLevel level, RandomSource random) {
		if (UkBosses.busy()) return false;
		int tier = Math.min(5, wave / BOSS_EVERY);
		List<String> keys = new ArrayList<>();
		for (int t = tier; t >= 1 && keys.isEmpty(); t--) for (UkBosses.Boss b : UkBosses.ROSTER) if (b.tier() == t) keys.add(b.key());
		if (keys.isEmpty()) return false;
		String key = keys.get(random.nextInt(keys.size()));
		Vec3 at = spot(level, random);
		String layer = arenaMode && arena != null ? layerKey(arena.def().layer()) : "prelude";
		if (!UkBosses.arena(sp, key, at, layer, () -> bossBeaten(sp))) return false;
		bossWave = true;
		left = 1;
		UkBosses.Boss b = UkBosses.byKey(key);
		hud("WAVE " + wave + ": " + (b != null ? b.name() : key.toUpperCase(Locale.ROOT)));
		sendState();
		return true;
	}

	/** The layer an arena is after, as Arenas knows it (for its boss theme). */
	private static String layerKey(String layer) {
		String k = layer.toLowerCase(Locale.ROOT);
		if (k.startsWith("prime")) return "prime";
		return Arenas.layer(k) != null ? k : "prelude";
	}

	/** The wave's boss is beaten. */
	private static void bossBeaten(ServerPlayer sp) {
		if (!running || !bossWave) return;
		bossWave = false;
		left = 0;
		ServerPlayer who = runnerPlayer();
		cleared(who != null ? who : sp);
	}

	/** UKDEAD ... 1: one of the wave's enemies died. */
	static void died(ServerPlayer sp) {
		if (!isRunner(sp) || countdown >= 0) return;
		left--;
		if (left <= 0) cleared(sp);
	}

	private static void cleared(ServerPlayer sp) {
		if (countdown >= 0) return;
		// a cleared wave pays out, more the further in: experience, and P on top of the style it took
		ExperienceOrb.award(sp.level(), sp.position(), 5 + wave * 3);
		int prize = 250 * wave;
		// everyone fighting it is paid
		for (ServerPlayer o : sp.level().getServer().getPlayerList().getPlayers()) {
			if (o == sp || (UcNet.isV1(o) && o.level() == sp.level() && o.position().distanceTo(center) < LEAVE + 20)) UkProgress.get(o).award(prize);
		}
		if (wave > UltracraftConfig.grindBest) {
			UltracraftConfig.grindBest = wave;
			UltracraftConfig.save();
		}
		UcNet.send(sp, "GRINDCLEAR");
		countdown = 100;
		if (arenaMode && ++wavesHere >= WAVES_PER_ARENA) {
			// the arena is cleared: the next one, once the party has had a breather
			nextArenaDue = true;
			countdown = 140;
			int next = nextArena(false);
			hud(next < 0 ? String.format(Locale.ROOT, "ARENA CLEARED  +%,d <color=#FF4343>P</color>", prize)
				: String.format(Locale.ROOT, "ARENA CLEARED  +%,d <color=#FF4343>P</color>  -  NEXT: %s", prize, GrindArenas.def(next).title()));
		} else {
			hud(String.format(Locale.ROOT, "WAVE %d CLEARED  +%,d <color=#FF4343>P</color>", wave, prize));
		}
		sendState();
	}

	/** The run is over: V1 died, left, became Steve, or pressed Leave. */
	static void stop(ServerPlayer sp, String why) {
		if (!running) return;
		ServerPlayer owner = sp != null ? sp : runnerPlayer();
		running = false;
		if (bossWave && UkBosses.busy() && owner != null) UkBosses.stop(owner, "the Cyber Grind is over");
		bossWave = false;
		int reached = Math.max(0, countdown >= 0 ? wave : wave - 1);
		if (reached > UltracraftConfig.grindBest) {
			UltracraftConfig.grindBest = reached;
			UltracraftConfig.save();
		}
		if (owner != null) UcNet.send(owner, "GRINDCLEAR");
		hud("THE CYBER GRIND IS OVER: WAVE " + wave);
		if (sp != null) {
			sp.displayClientMessage(Component.literal(String.format(Locale.ROOT, "The Cyber Grind is over (%s): wave %d, best %d", why, wave, UltracraftConfig.grindBest)), false);
		}
		if (arenaMode) leaveArenas();
		sendState();
		runner = null;
	}

	/**
	 * A player joining in the Grind's arenas with no run of theirs going on there (they left mid-run, or the world
	 * closed): out to the world's spawn, not left in an empty arena over the void.
	 */
	static void joined(ServerPlayer sp) {
		if (sp.level().dimension() != GrindArenas.DIMENSION) return;
		if (running && arenaMode && party.containsKey(sp.getUUID())) return;
		var server = sp.level().getServer();
		var spawn = server.getRespawnData();
		ServerLevel to = server.getLevel(spawn.globalPos().dimension());
		if (to == null) to = server.overworld();
		var pos = spawn.globalPos().pos();
		int y = to.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		sp.teleportTo(to, pos.getX() + 0.5, Math.max(y, pos.getY()), pos.getZ() + 0.5, Set.of(), spawn.yaw(), 0f, true);
		sp.fallDistance = 0;
	}

	/** Back out of the arenas: the temporary shop goes, and everyone still in there goes back where they came from. */
	private static void leaveArenas() {
		arenaMode = false;
		nextArenaDue = false;
		if (server == null) return;
		ServerLevel level = GrindArenas.level(server);
		if (arena != null && arena.shop() != null) GrindArenas.removeShop(level, arena.shop(), arena.shopFacing());
		arena = null;
		for (Map.Entry<UUID, Return> e : party.entrySet()) {
			ServerPlayer o = server.getPlayerList().getPlayer(e.getKey());
			if (o != null) UcNet.send(o, "SKY -");
			if (o == null || !o.isAlive() || o.level().dimension() != GrindArenas.DIMENSION) continue;
			Return r = e.getValue();
			ServerLevel back = server.getLevel(r.level());
			if (back == null) back = server.overworld();
			o.teleportTo(back, r.at().x, r.at().y, r.at().z, Set.of(), r.yaw(), r.pitch(), true);
			o.fallDistance = 0;
		}
		party.clear();
	}

	/** Its banner for everyone. */
	private static void hud(String text) {
		if (server != null) UcNet.sendAll(server, "GRINDHUD " + text);
	}

	private static String stateLine() {
		return "GRINDSTATE " + (running ? 1 : 0) + " " + wave + " " + UltracraftConfig.grindBest + " " + (UltracraftConfig.ukSpawns ? 1 : 0);
	}

	/** GRINDSTATE running wave best spawns, for every shop screen. */
	static void sendState() {
		if (server != null) UcNet.sendAll(server, stateLine());
	}

	static void sendState(ServerPlayer sp) {
		UcNet.send(sp, stateLine());
	}

	static void sendStateAll(net.minecraft.server.MinecraftServer s) {
		UcNet.sendAll(s, stateLine());
	}
}
