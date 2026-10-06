package dev.ultracraft;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Cyber Grind's own arenas: 50 big floating arenas, each after one of ULTRAKILL's levels or layers, in a void
 * dimension of their own (ultracraft:cybergrind) so they never touch the player's world. Each is built fresh (any
 * damage from the last visit swept away) when the run gets to it, from its recipe: a shape, a size, a layer's blocks,
 * and what stands on it (pillars, walls, a moat, terraces, floating platforms, a roof). Each also gets a temporary
 * shop, tucked away near its edge: its screen is the way out. Server thread.
 */
final class GrindArenas {
	static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("ultracraft", "cybergrind"));

	/** Floor height, and how far apart the arenas are. */
	private static final int Y = 100, SPACING = 640;

	/** A layer's blocks: floor, its pattern, walls, wall tops, pillars, lights, underside. */
	record Palette(Block floor, Block accent, Block wall, Block top, Block pillar, Block light, Block base) {}

	static final Palette CYBER = new Palette(Blocks.BLACK_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.CYAN_CONCRETE,
		Blocks.LIGHT_GRAY_CONCRETE, Blocks.SEA_LANTERN, Blocks.BLACK_CONCRETE);
	static final Palette LIMBO = new Palette(Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS,
		Blocks.QUARTZ_PILLAR, Blocks.SEA_LANTERN, Blocks.STONE);
	static final Palette LUST = new Palette(Blocks.RED_NETHER_BRICKS, Blocks.CRYING_OBSIDIAN, Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS,
		Blocks.PURPUR_PILLAR, Blocks.PEARLESCENT_FROGLIGHT, Blocks.NETHER_BRICKS);
	static final Palette GLUTTONY = new Palette(Blocks.NETHER_WART_BLOCK, Blocks.BONE_BLOCK, Blocks.RED_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM,
		Blocks.BONE_BLOCK, Blocks.SHROOMLIGHT, Blocks.NETHER_WART_BLOCK);
	static final Palette GREED = new Palette(Blocks.CUT_SANDSTONE, Blocks.ORANGE_TERRACOTTA, Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_SANDSTONE,
		Blocks.SANDSTONE, Blocks.OCHRE_FROGLIGHT, Blocks.SANDSTONE);
	static final Palette WRATH = new Palette(Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE, Blocks.DARK_PRISMARINE,
		Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.PRISMARINE);
	static final Palette HERESY = new Palette(Blocks.POLISHED_BLACKSTONE, Blocks.GILDED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS,
		Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.POLISHED_BASALT, Blocks.CRYING_OBSIDIAN, Blocks.BLACKSTONE);
	static final Palette VIOLENCE = new Palette(Blocks.POLISHED_DEEPSLATE, Blocks.WAXED_CUT_COPPER, Blocks.DEEPSLATE_TILES, Blocks.WAXED_CUT_COPPER,
		Blocks.IRON_BLOCK, Blocks.OCHRE_FROGLIGHT, Blocks.COBBLED_DEEPSLATE);
	static final Palette FRAUD = new Palette(Blocks.WHITE_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.QUARTZ_BRICKS, Blocks.SMOOTH_QUARTZ,
		Blocks.QUARTZ_PILLAR, Blocks.SEA_LANTERN, Blocks.SMOOTH_QUARTZ);
	static final Palette TREACHERY = new Palette(Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.DEEPSLATE_BRICKS, Blocks.CHISELED_DEEPSLATE,
		Blocks.POLISHED_DEEPSLATE, Blocks.VERDANT_FROGLIGHT, Blocks.DEEPSLATE);
	static final Palette PRELUDE = new Palette(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.MAGMA_BLOCK, Blocks.BLACKSTONE, Blocks.CHISELED_POLISHED_BLACKSTONE,
		Blocks.POLISHED_BASALT, Blocks.SHROOMLIGHT, Blocks.BLACKSTONE);
	static final Palette PRIME = new Palette(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.OBSIDIAN, Blocks.GOLD_BLOCK,
		Blocks.PURPUR_PILLAR, Blocks.PEARLESCENT_FROGLIGHT, Blocks.END_STONE_BRICKS);
	static final Palette HEAVEN = new Palette(Blocks.SMOOTH_QUARTZ, Blocks.GOLD_BLOCK, Blocks.QUARTZ_BRICKS, Blocks.GOLD_BLOCK,
		Blocks.QUARTZ_PILLAR, Blocks.GLOWSTONE, Blocks.CALCITE);
	static final Palette FLESH = new Palette(Blocks.RED_CONCRETE, Blocks.BONE_BLOCK, Blocks.RED_TERRACOTTA, Blocks.BONE_BLOCK,
		Blocks.NETHER_WART_BLOCK, Blocks.SHROOMLIGHT, Blocks.CRIMSON_HYPHAE);

	enum Shape { ROUND, SQUARE, OCTAGON, CROSS, RING, ISLANDS, GRID }

	enum Hazard { NONE, WATER, LAVA }

	/**
	 * An arena: its title (and the layer it's after), blocks, shape, radius, pillars, wall height (0: none), its
	 * hazard moat, terraces (positive: rising to the middle, negative: a bowl), floating platforms, a roof.
	 */
	record Def(String title, String layer, Palette p, Shape shape, int r, int pillars, int walls, Hazard hazard, int tiers, int platforms, boolean roof) {}

	static final List<Def> ARENAS = List.of(
		new Def("THE GRID", "CYBER GRIND", CYBER, Shape.GRID, 26, 0, 0, Hazard.NONE, 0, 0, false),
		new Def("0-1: INTO THE FIRE", "PRELUDE", PRELUDE, Shape.ROUND, 24, 4, 3, Hazard.NONE, 0, 0, false),
		new Def("0-2: THE MEATGRINDER", "PRELUDE", PRELUDE, Shape.SQUARE, 26, 6, 4, Hazard.LAVA, 0, 0, false),
		new Def("0-3: DOUBLE DOWN", "PRELUDE", PRELUDE, Shape.CROSS, 28, 4, 0, Hazard.NONE, 1, 2, false),
		new Def("0-4: A ONE-MACHINE ARMY", "PRELUDE", PRELUDE, Shape.OCTAGON, 28, 8, 4, Hazard.NONE, 0, 0, false),
		new Def("0-5: CERBERUS", "PRELUDE", PRELUDE, Shape.ROUND, 30, 6, 5, Hazard.NONE, -2, 0, false),
		new Def("1-1: HEART OF THE SUNRISE", "LIMBO", LIMBO, Shape.SQUARE, 26, 4, 4, Hazard.WATER, 0, 0, false),
		new Def("1-2: THE BURNING WORLD", "LIMBO", LIMBO, Shape.ROUND, 28, 8, 0, Hazard.NONE, 2, 2, false),
		new Def("1-3: HALLS OF SACRED REMAINS", "LIMBO", LIMBO, Shape.OCTAGON, 26, 8, 5, Hazard.NONE, 0, 0, true),
		new Def("1-4: CLAIR DE LUNE", "LIMBO", LIMBO, Shape.RING, 32, 6, 0, Hazard.NONE, 0, 3, false),
		new Def("2-1: BRIDGEBURNER", "LUST", LUST, Shape.ISLANDS, 34, 0, 0, Hazard.NONE, 0, 2, false),
		new Def("2-2: DEATH AT 20,000 VOLTS", "LUST", LUST, Shape.SQUARE, 28, 6, 4, Hazard.WATER, 0, 0, false),
		new Def("2-3: SHEER HEART ATTACK", "LUST", LUST, Shape.ROUND, 28, 4, 3, Hazard.NONE, 2, 2, false),
		new Def("2-4: COURT OF THE CORPSE KING", "LUST", LUST, Shape.OCTAGON, 30, 8, 6, Hazard.NONE, -1, 0, true),
		new Def("3-1: BELLY OF THE BEAST", "GLUTTONY", GLUTTONY, Shape.ROUND, 26, 6, 5, Hazard.NONE, -2, 0, true),
		new Def("3-2: IN THE FLESH", "GLUTTONY", FLESH, Shape.ROUND, 28, 4, 4, Hazard.LAVA, 0, 0, false),
		new Def("4-1: SLAVES TO POWER", "GREED", GREED, Shape.SQUARE, 30, 8, 4, Hazard.NONE, 1, 0, false),
		new Def("4-2: GOD DAMN THE SUN", "GREED", GREED, Shape.CROSS, 32, 4, 0, Hazard.NONE, 2, 3, false),
		new Def("4-3: A SHOT IN THE DARK", "GREED", GREED, Shape.OCTAGON, 28, 8, 6, Hazard.NONE, 0, 0, true),
		new Def("4-4: CLAIR DE SOLEIL", "GREED", GREED, Shape.ROUND, 30, 6, 0, Hazard.LAVA, -1, 2, false),
		new Def("5-1: IN THE WAKE OF POSEIDON", "WRATH", WRATH, Shape.RING, 30, 4, 0, Hazard.WATER, 0, 2, false),
		new Def("5-2: WAVES OF THE STARLESS SEA", "WRATH", WRATH, Shape.ISLANDS, 34, 0, 0, Hazard.WATER, 0, 3, false),
		new Def("5-3: SHIP OF FOOLS", "WRATH", WRATH, Shape.SQUARE, 26, 4, 3, Hazard.WATER, 1, 0, false),
		new Def("5-4: LEVIATHAN", "WRATH", WRATH, Shape.ROUND, 34, 6, 0, Hazard.WATER, 0, 4, false),
		new Def("6-1: CRY FOR THE WEEPER", "HERESY", HERESY, Shape.OCTAGON, 28, 8, 5, Hazard.NONE, 0, 0, true),
		new Def("6-2: AESTHETICS OF HATE", "HERESY", HERESY, Shape.ROUND, 32, 6, 4, Hazard.LAVA, 2, 2, false),
		new Def("7-1: GARDEN OF FORKING PATHS", "VIOLENCE", VIOLENCE, Shape.CROSS, 32, 8, 0, Hazard.NONE, 1, 2, false),
		new Def("7-2: LIGHT UP THE NIGHT", "VIOLENCE", VIOLENCE, Shape.GRID, 28, 4, 0, Hazard.NONE, 0, 0, false),
		new Def("7-3: NO SOUND, NO MEMORY", "VIOLENCE", VIOLENCE, Shape.SQUARE, 30, 8, 6, Hazard.NONE, -1, 0, true),
		new Def("7-4: ...LIKE ANTENNAS TO HEAVEN", "VIOLENCE", VIOLENCE, Shape.ROUND, 34, 8, 0, Hazard.LAVA, 0, 4, false),
		new Def("P-1: SOUL SURVIVOR", "PRIME SANCTUM", PRIME, Shape.ROUND, 30, 8, 5, Hazard.NONE, 0, 0, false),
		new Def("P-2: WAIT OF THE WORLD", "PRIME SANCTUM", PRIME, Shape.OCTAGON, 32, 8, 0, Hazard.LAVA, 2, 3, false),
		new Def("HOLLOW HEAVEN", "HEAVEN", HEAVEN, Shape.RING, 32, 8, 0, Hazard.NONE, 0, 4, false),
		new Def("THE GOLDEN GATE", "HEAVEN", HEAVEN, Shape.SQUARE, 30, 6, 5, Hazard.NONE, 1, 0, false),
		new Def("FROZEN BETRAYAL", "TREACHERY", TREACHERY, Shape.ROUND, 30, 6, 4, Hazard.WATER, -1, 0, false),
		new Def("THE NINTH CIRCLE", "TREACHERY", TREACHERY, Shape.OCTAGON, 32, 8, 0, Hazard.NONE, 2, 3, false),
		new Def("COCYTUS", "TREACHERY", TREACHERY, Shape.ISLANDS, 34, 0, 0, Hazard.WATER, 0, 2, false),
		new Def("FALSE IDOLS", "FRAUD", FRAUD, Shape.SQUARE, 28, 8, 4, Hazard.NONE, 0, 2, false),
		new Def("THE MIRROR HALL", "FRAUD", FRAUD, Shape.CROSS, 30, 6, 5, Hazard.NONE, 0, 0, true),
		new Def("CHECKMATE", "FRAUD", FRAUD, Shape.GRID, 30, 0, 0, Hazard.NONE, 0, 0, false),
		new Def("THE SCRAPYARD", "VIOLENCE", VIOLENCE, Shape.ISLANDS, 32, 0, 0, Hazard.LAVA, 0, 3, false),
		new Def("GORE PIT", "GLUTTONY", FLESH, Shape.RING, 30, 4, 4, Hazard.LAVA, -2, 0, false),
		new Def("STOMACH ACID", "GLUTTONY", GLUTTONY, Shape.CROSS, 30, 4, 0, Hazard.LAVA, 0, 2, false),
		new Def("THE VAULT", "GREED", GREED, Shape.SQUARE, 26, 4, 6, Hazard.NONE, 0, 0, true),
		new Def("STORM FRONT", "WRATH", WRATH, Shape.OCTAGON, 30, 6, 0, Hazard.WATER, 1, 2, false),
		new Def("THE CATHEDRAL", "HERESY", HERESY, Shape.CROSS, 32, 8, 6, Hazard.NONE, 0, 0, true),
		new Def("FIRST CIRCLE", "LIMBO", LIMBO, Shape.GRID, 28, 4, 0, Hazard.NONE, 0, 2, false),
		new Def("THE NEON SEA", "CYBER GRIND", CYBER, Shape.ISLANDS, 34, 0, 0, Hazard.WATER, 0, 3, false),
		new Def("OVERCLOCK", "CYBER GRIND", CYBER, Shape.RING, 32, 8, 0, Hazard.NONE, 2, 4, false),
		new Def("THE FINAL GRIND", "CYBER GRIND", CYBER, Shape.GRID, 34, 8, 4, Hazard.LAVA, 0, 4, false));

	/** ULTRAKILL's own skies (its Addressables' materials), by the layer an arena is after; a layer with several
	 * takes them in turn. */
	private static final java.util.Map<String, String[]> SKIES = java.util.Map.ofEntries(
		java.util.Map.entry("CYBER GRIND", new String[] {"EndlessSky"}),
		java.util.Map.entry("PRELUDE", new String[] {"RedSky", "RedSky3", "DuskSky"}),
		java.util.Map.entry("LIMBO", new String[] {"SkyBlue", "DaySky", "DaySky 2", "EveningSky"}),
		java.util.Map.entry("LUST", new String[] {"../LustSky", "../LustSky 1", "../LustSky 2"}),
		java.util.Map.entry("GLUTTONY", new String[] {"RedSky2", "RedSky"}),
		java.util.Map.entry("GREED", new String[] {"GreedSky", "GreedSky2", "GreedSky3", "GreedSky4", "GreedSky5"}),
		java.util.Map.entry("WRATH", new String[] {"../Environment/Layer 5/OvercastSky", "FogCoveredSky", "NightSky2"}),
		java.util.Map.entry("HERESY", new String[] {"NightSky1", "NightSky3"}),
		java.util.Map.entry("VIOLENCE", new String[] {"ViolenceSky", "ViolenceSky3", "ViolenceSky4", "ViolenceSky 2"}),
		java.util.Map.entry("FRAUD", new String[] {"FraudCity_SkyMat_Night", "FraudCity_SkyMat"}),
		java.util.Map.entry("TREACHERY", new String[] {"SpaceSky", "NightSky4"}),
		java.util.Map.entry("PRIME SANCTUM", new String[] {"NightSky5", "SpaceSky"}),
		java.util.Map.entry("HEAVEN", new String[] {"DawnSky", "EveningSky 3"}));

	/** A sky for arena index: the address of one of its layer's ULTRAKILL skybox materials, picked at random. */
	static String sky(int index, RandomSource random) {
		Def d = def(index);
		String[] all = SKIES.getOrDefault(d.layer, new String[] {"DuskSky"});
		String name = all[random.nextInt(all.length)];
		return name.startsWith("../") ? "Assets/Materials/" + name.substring(3) + ".mat" : "Assets/Materials/Skyboxes/" + name + ".mat";
	}

	/** Where an arena was built: its middle (on the floor), its radius, where V1 lands, and its shop. */
	record Built(Def def, Vec3 center, int r, Vec3 spawn, BlockPos shop, Direction shopFacing) {}

	/** The arena standing now varies its raised squares by this (each build rolls its own). */
	private static int salt;

	private GrindArenas() {}

	static ServerLevel level(MinecraftServer server) {
		return server.getLevel(DIMENSION);
	}

	static Def def(int index) {
		return ARENAS.get(Math.floorMod(index, ARENAS.size()));
	}

	/**
	 * Builds arena index (any damage from a last visit swept away first), with its temporary shop. Seed rolls this
	 * build's own take on it: where its pillars stand and how tall, its platforms, raised squares, floor and shop.
	 */
	static Built build(ServerLevel level, int index, long seed) {
		Def d = def(index);
		int ox = 1024 + Math.floorMod(index, ARENAS.size()) * SPACING, oz = 0;
		RandomSource rnd = RandomSource.create(seed);
		salt = rnd.nextInt();
		int R = d.r;
		clear(level, ox, oz, R + 8);
		// the floor, with its underside tapering away into the void
		for (int dx = -R - 2; dx <= R + 2; dx++) {
			for (int dz = -R - 2; dz <= R + 2; dz++) {
				if (!inside(d, dx, dz)) continue;
				int top = top(d, dx, dz);
				double dist = Math.sqrt(dx * dx + dz * dz);
				int depth = 2 + (int) Math.max(0, (R - dist) / 4);
				for (int y = Y - depth; y < top; y++) set(level, ox + dx, y, oz + dz, d.p.base);
				set(level, ox + dx, top, oz + dz, floorBlock(d, dx, dz, rnd));
			}
		}
		moat(level, d, ox, oz);
		walls(level, d, ox, oz);
		pillars(level, d, ox, oz, rnd);
		platforms(level, d, ox, oz, rnd);
		if (d.roof) roof(level, d, ox, oz);
		// the shop: out towards the edge on one side, its screen facing the middle, with a column of lights over it
		// so it can be found from anywhere in the arena. Its spot is cleared for it, so there always is one.
		BlockPos shop = null;
		Direction facing = Direction.NORTH;
		int start = rnd.nextInt(4);
		for (int k = 0; k < 12 && shop == null; k++) {
			Direction f = Direction.from2DDataValue((start + k) % 4);
			// it stands on the far side from the middle, its screen facing in; nearer the middle on later tries
			int dist = Math.max(4, R - (d.walls > 0 ? 4 : 5) - (k / 4) * (R / 4));
			int sx = -f.getStepX() * dist, sz = -f.getStepZ() * dist;
			if (!inside(d, sx, sz) || !inside(d, sx + f.getStepX() * 2, sz + f.getStepZ() * 2) || !inside(d, sx + 1, sz + 1) || !inside(d, sx - 1, sz - 1)) continue;
			BlockPos at = new BlockPos(ox + sx, top(d, sx, sz) + 1, oz + sz);
			placeShop(level, at, f, d);
			shop = at;
			facing = f;
		}
		if (shop == null) {
			// nowhere out there: by the middle
			facing = Direction.SOUTH;
			shop = new BlockPos(ox, top(d, 0, -3) + 1, oz - 3);
			placeShop(level, shop, facing, d);
		}
		Vec3 center = new Vec3(ox + 0.5, top(d, 0, 0) + 1, oz + 0.5);
		int sz = Math.max(4, R / 3);
		if (!inside(d, 0, sz)) sz = 0;
		Vec3 spawn = new Vec3(ox + 0.5, top(d, 0, sz) + 1, oz + sz + 0.5);
		if (d.shape == Shape.RING) spawn = new Vec3(ox + 0.5, top(d, 0, (int) (R * 0.7)) + 1, oz + (int) (R * 0.7) + 0.5);
		return new Built(d, center, R, spawn, shop, facing);
	}

	/**
	 * Whether an enemy can stand at block column x z of arena b with its ground block at groundY: on the floor
	 * itself (not a wall, pillar, platform or a raised square it couldn't climb down from), with floor all round
	 * it, out of the moat and away from the edge, so it can walk everywhere V1 can.
	 */
	static boolean standable(Built b, int x, int z, int groundY) {
		Def d = b.def();
		int dx = x - (int) Math.floor(b.center().x), dz = z - (int) Math.floor(b.center().z);
		if (groundY != top(d, dx, dz) || raise(d, dx, dz) >= 2) return false;
		for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) if (!inside(d, dx + i, dz + j)) return false;
		if (d.hazard != Hazard.NONE) {
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist > d.r * 0.5 - 2 && dist < d.r * 0.58 + 2) return false;
		}
		return true;
	}

	/** The temporary shop goes when the run leaves its arena. */
	static void removeShop(ServerLevel level, BlockPos anchor, Direction facing) {
		if (level == null || anchor == null) return;
		for (int y = 4; y <= 16; y += 2) level.setBlock(anchor.above(y), Blocks.AIR.defaultBlockState(), 2 | 16);
		for (int part = 0; part < UkShopBlock.PARTS; part++) {
			BlockPos at = anchor.offset(UkShopBlock.offset(facing, part));
			if (level.getBlockState(at).is(UltracraftCommon.UK_SHOP)) level.setBlock(at, Blocks.AIR.defaultBlockState(), 2 | 16);
		}
	}

	// ------------------------------------------------------------------ shapes

	private static boolean inside(Def d, int dx, int dz) {
		int R = d.r;
		double dist = Math.sqrt(dx * dx + dz * dz);
		int ax = Math.abs(dx), az = Math.abs(dz);
		return switch (d.shape) {
			case ROUND -> dist <= R;
			case SQUARE, GRID -> Math.max(ax, az) <= R * 0.85;
			case OCTAGON -> ax + az <= R * 1.25 && Math.max(ax, az) <= R * 0.9;
			case CROSS -> (ax <= R / 3 || az <= R / 3) && Math.max(ax, az) <= R;
			case RING -> dist <= R && dist >= R * 0.38;
			case ISLANDS -> island(d, dx, dz);
		};
	}

	/** A middle island and four around it, joined by three-wide bridges. */
	private static boolean island(Def d, int dx, int dz) {
		int R = d.r;
		double small = R * 0.3, ring = R * 0.68;
		if (Math.sqrt(dx * dx + dz * dz) <= R * 0.36) return true;
		for (int i = 0; i < 4; i++) {
			double a = Math.PI / 4 + i * Math.PI / 2;
			double cx = Math.cos(a) * ring, cz = Math.sin(a) * ring;
			if (Math.hypot(dx - cx, dz - cz) <= small) return true;
			// the bridge to the middle
			double t = (dx * cx + dz * cz) / (ring * ring);
			if (t > 0 && t < 1 && Math.hypot(dx - cx * t, dz - cz * t) <= 1.5) return true;
		}
		return false;
	}

	/** The floor's height here: terraces step one block at a time (up to the middle, or down into a bowl). */
	private static int top(Def d, int dx, int dz) {
		int y = Y;
		if (d.tiers != 0) {
			double dist = Math.sqrt(dx * dx + dz * dz);
			int n = Math.abs(d.tiers);
			int band = (int) Math.floor((d.r - dist) / (d.r / (double) (n + 2)));
			band = Math.max(0, Math.min(n, band));
			y += d.tiers > 0 ? band : -band;
		}
		return y + raise(d, dx, dz);
	}

	/** The Cyber Grind's raised squares, four by four, a few of them up a block or two (which, this build's roll). */
	private static int raise(Def d, int dx, int dz) {
		if (d.shape != Shape.GRID || (Math.abs(dx) <= 4 && Math.abs(dz) <= 4)) return 0;
		int cx = Math.floorDiv(dx, 4), cz = Math.floorDiv(dz, 4);
		int h = Math.floorMod(cx * 73856093 ^ cz * 19349663 ^ salt, 11);
		return h >= 9 ? 2 : h >= 7 ? 1 : 0;
	}

	private static BlockState floorBlock(Def d, int dx, int dz, RandomSource rnd) {
		Palette p = d.p;
		// lights set into the floor every few blocks
		if (Math.floorMod(dx, 7) == 3 && Math.floorMod(dz, 7) == 3) return p.light.defaultBlockState();
		if (d.shape == Shape.GRID || p == CYBER) {
			return (Math.floorMod(dx, 4) == 0 || Math.floorMod(dz, 4) == 0 ? p.accent : p.floor).defaultBlockState();
		}
		if (p == FRAUD) return ((Math.floorDiv(dx, 3) + Math.floorDiv(dz, 3)) % 2 == 0 ? p.floor : p.accent).defaultBlockState();
		double dist = Math.sqrt(dx * dx + dz * dz);
		// rings, as ULTRAKILL's arenas have
		if (Math.abs(dist - d.r * 0.25) < 0.6 || Math.abs(dist - d.r * 0.6) < 0.6) return p.accent.defaultBlockState();
		return (rnd.nextInt(12) == 0 ? p.accent : p.floor).defaultBlockState();
	}

	// ------------------------------------------------------------------ what stands on it

	private static void moat(ServerLevel level, Def d, int ox, int oz) {
		if (d.hazard == Hazard.NONE) return;
		BlockState fluid = (d.hazard == Hazard.LAVA ? Blocks.LAVA : Blocks.WATER).defaultBlockState();
		int R = d.r;
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				if (!inside(d, dx, dz)) continue;
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist < R * 0.5 || dist > R * 0.58) continue;
				// four bridges across it
				if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) continue;
				int top = top(d, dx, dz);
				set(level, ox + dx, top, oz + dz, fluid.getBlock());
				set(level, ox + dx, top - 1, oz + dz, d.p.base);
			}
		}
	}

	private static void walls(ServerLevel level, Def d, int ox, int oz) {
		if (d.walls <= 0) return;
		int R = d.r;
		for (int dx = -R - 1; dx <= R + 1; dx++) {
			for (int dz = -R - 1; dz <= R + 1; dz++) {
				if (!inside(d, dx, dz)) continue;
				boolean edge = !inside(d, dx + 1, dz) || !inside(d, dx - 1, dz) || !inside(d, dx, dz + 1) || !inside(d, dx, dz - 1);
				// the inner edge of a ring stays open (its hole is the void)
				if (!edge || (d.shape == Shape.RING && Math.sqrt(dx * dx + dz * dz) < d.r * 0.6)) continue;
				int top = top(d, dx, dz);
				for (int h = 1; h <= d.walls; h++) {
					boolean lamp = h == d.walls / 2 + 1 && Math.floorMod(dx + dz, 6) == 0;
					set(level, ox + dx, top + h, oz + dz, h == d.walls ? d.p.top : lamp ? d.p.light : d.p.wall);
				}
			}
		}
	}

	private static void pillars(ServerLevel level, Def d, int ox, int oz, RandomSource rnd) {
		int R = d.r;
		// the ring of pillars turned and spread by the roll
		double turn = rnd.nextDouble() * Math.PI * 2 / Math.max(1, d.pillars);
		double ring = d.shape == Shape.RING ? R * (0.66 + rnd.nextDouble() * 0.08) : R * (0.36 + rnd.nextDouble() * 0.11);
		for (int i = 0; i < d.pillars; i++) {
			double a = i * Math.PI * 2 / d.pillars + turn;
			int cx = (int) Math.round(Math.cos(a) * ring), cz = (int) Math.round(Math.sin(a) * ring);
			if (!inside(d, cx, cz)) continue;
			int h = 6 + rnd.nextInt(5) + (d.roof ? 6 : 0);
			int top = top(d, cx, cz);
			for (int x = 0; x < 2; x++) {
				for (int z = 0; z < 2; z++) {
					for (int y = 1; y <= h; y++) set(level, ox + cx + x, top + y, oz + cz + z, y == h ? d.p.light : d.p.pillar);
				}
			}
		}
	}

	private static void platforms(ServerLevel level, Def d, int ox, int oz, RandomSource rnd) {
		int R = d.r;
		for (int i = 0; i < d.platforms; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, dist = R * (0.25 + rnd.nextDouble() * 0.45);
			int cx = (int) (Math.cos(a) * dist), cz = (int) (Math.sin(a) * dist);
			int y = top(d, cx, cz) + 4 + rnd.nextInt(3);
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) set(level, ox + cx + x, y, oz + cz + z, x == 0 && z == 0 ? d.p.light : d.p.top);
			}
		}
	}

	/** A roof on the walls (with the walls' height plus some), lit from below. */
	private static void roof(ServerLevel level, Def d, int ox, int oz) {
		int R = d.r, h = Y + Math.max(d.walls, 6) + 8;
		for (int dx = -R - 1; dx <= R + 1; dx++) {
			for (int dz = -R - 1; dz <= R + 1; dz++) {
				if (!inside(d, dx, dz)) continue;
				boolean lamp = Math.floorMod(dx, 6) == 0 && Math.floorMod(dz, 6) == 0;
				set(level, ox + dx, h, oz + dz, lamp ? d.p.light : d.p.wall);
			}
		}
		// the walls up to it
		if (d.walls > 0) {
			for (int dx = -R - 1; dx <= R + 1; dx++) {
				for (int dz = -R - 1; dz <= R + 1; dz++) {
					if (!inside(d, dx, dz)) continue;
					boolean edge = !inside(d, dx + 1, dz) || !inside(d, dx - 1, dz) || !inside(d, dx, dz + 1) || !inside(d, dx, dz - 1);
					if (!edge) continue;
					for (int y = top(d, dx, dz) + d.walls + 1; y < h; y++) set(level, ox + dx, y, oz + dz, Math.floorMod(dx + dz, 5) == 0 ? Blocks.TINTED_GLASS : d.p.wall);
				}
			}
		}
	}

	private static void placeShop(ServerLevel level, BlockPos anchor, Direction facing, Def d) {
		// room for it and in front of it, and floor under it
		Direction right = UkShopBlock.right(facing);
		for (int a = -1; a <= 2; a++) {
			for (int f = 0; f <= 2; f++) {
				for (int y = 0; y < 4; y++) {
					BlockPos at = anchor.relative(right, a).relative(facing, f).above(y);
					if (!level.getBlockState(at).isAir()) level.setBlock(at, Blocks.AIR.defaultBlockState(), 2 | 16);
				}
				BlockPos under = anchor.relative(right, a).relative(facing, f).below();
				if (level.getBlockState(under).isAir()) level.setBlock(under, d.p.floor.defaultBlockState(), 2 | 16);
			}
		}
		BlockState state = UltracraftCommon.UK_SHOP.defaultBlockState().setValue(UkShopBlock.FACING, facing);
		for (int part = 0; part < UkShopBlock.PARTS; part++) {
			level.setBlock(anchor.offset(UkShopBlock.offset(facing, part)), state.setValue(UkShopBlock.PART, part), 3);
		}
		// its marker: a column of lights rising over it
		for (int y = 4; y <= 16; y += 2) level.setBlock(anchor.above(y), d.p.light.defaultBlockState(), 2 | 16);
	}

	/** Which way the shop is from the middle, in words ("north", "south-east"...). */
	static String direction(Built b) {
		if (b.shop() == null) return "nearby";
		double dx = b.shop().getX() + 0.5 - b.center().x, dz = b.shop().getZ() + 0.5 - b.center().z;
		String ns = dz < -2 ? "north" : dz > 2 ? "south" : "", ew = dx > 2 ? "east" : dx < -2 ? "west" : "";
		return ns.isEmpty() ? (ew.isEmpty() ? "in the middle" : ew) : ew.isEmpty() ? ns : ns + "-" + ew;
	}

	// ------------------------------------------------------------------ blocks

	/** Everything in the arena's box goes (what's already air is left alone). */
	private static void clear(ServerLevel level, int ox, int oz, int r) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = ox - r; x <= ox + r; x++) {
			for (int z = oz - r; z <= oz + r; z++) {
				for (int y = Y - 14; y <= Y + 30; y++) {
					m.set(x, y, z);
					if (!level.getBlockState(m).isAir()) level.setBlock(m, air, 2 | 16);
				}
			}
		}
	}

	private static void set(ServerLevel level, int x, int y, int z, Block b) {
		level.setBlock(new BlockPos(x, y, z), b.defaultBlockState(), 2 | 16);
	}

	private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
		level.setBlock(new BlockPos(x, y, z), s, 2 | 16);
	}
}
