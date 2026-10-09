package dev.ultracraft;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * config/ultracraft.properties: the same keys as the Fabric version, so a config tuned for one works in the other.
 * Keys this port doesn't use are kept as they are when the file is written back.
 */
public final class UltracraftConfig {
	/** Height ULTRAKILL renders V1's layer at (0 = the window's full size). Every frame crosses between the two games, so this is the big frame-rate knob. */
	public static int v1Height = 720;
	public static boolean autoV1 = true;
	public static boolean launchUltrakill = true;
	public static boolean sharpShop = true;
	/** ULTRAKILL's frame cap; 0 = match Minecraft's frame limit. */
	public static int ukFps = 0;
	public static boolean lockStep = true;
	public static boolean lowLatency = true;
	/** 2 high, 1 medium, 0 low. */
	public static int effects = 2;
	public static int stainCap = 8192;
	public static boolean extraGore = true;
	public static int terrainRange = 128;
	public static boolean playerBlockDamage = true;
	public static boolean enemyBlockDamage = true;
	public static float impactFrames = 1f;
	public static String fightMusic = "random";
	public static boolean calmMusic = false;
	/** Non-weapon cheats supported by the Forge-side increment. These are world/server-wide config switches. */
	public static boolean cheatNeverHungry = false;
	/** Speed effect applies to Minecraft/Steve movement only; ULTRAKILL owns V1's actual movement. */
	public static boolean cheatSuperSpeed = false;
	public static String ultrakillDir = "";
	/** ULTRAKILL's own settings used while playing (uk.* keys). */
	public static final Map<String, String> ukPrefs = new TreeMap<String, String>();

	private static File configFile;
	private static final Properties RAW = new Properties();

	private UltracraftConfig() {}

	public static void init(File configDir) {
		configFile = new File(configDir, "ultracraft.properties");
		load();
	}

	public static int instance() {
		try {
			return Math.max(1, Integer.parseInt(System.getProperty("ultracraft.instance", "1")));
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	public static String instanceSuffix() {
		return instance() > 1 ? "_" + instance() : "";
	}

	private static boolean bool(String key, boolean def) {
		return Boolean.parseBoolean(RAW.getProperty(key, Boolean.toString(def)).trim());
	}

	private static int integer(String key, int def, int min, int max) {
		try {
			return Math.max(min, Math.min(max, Integer.parseInt(RAW.getProperty(key, Integer.toString(def)).trim())));
		} catch (NumberFormatException e) {
			return def;
		}
	}

	public static void load() {
		try {
			RAW.clear();
			if (configFile != null && configFile.isFile()) {
				try (InputStream in = Files.newInputStream(configFile.toPath())) {
					RAW.load(in);
				}
			}
			v1Height = integer("v1Height", v1Height, 0, 4320);
			autoV1 = bool("autoV1", autoV1);
			launchUltrakill = bool("launchUltrakill", launchUltrakill);
			sharpShop = bool("sharpShop", sharpShop);
			ukFps = integer("ukFpsCap", ukFps, 0, 240);
			if (ukFps != 0) ukFps = Math.max(30, ukFps);
			lockStep = bool("lockStep", lockStep);
			lowLatency = bool("lowLatency", lowLatency);
			effects = integer("effects", effects, 0, 2);
			stainCap = integer("stainCap", stainCap, 0, 8192);
			extraGore = bool("extraGore", extraGore);
			terrainRange = integer("terrainRange", terrainRange, 64, 128);
			playerBlockDamage = bool("playerBlockDamage", playerBlockDamage);
			enemyBlockDamage = bool("enemyBlockDamage", enemyBlockDamage);
			try {
				impactFrames = Math.max(0.1f, Math.min(3f, Float.parseFloat(RAW.getProperty("impactFrames", Float.toString(impactFrames)).trim())));
			} catch (NumberFormatException ignored) {
			}
			fightMusic = RAW.getProperty("fightMusic", fightMusic).trim();
			calmMusic = bool("calmMusic", calmMusic);
			cheatNeverHungry = bool("cheatNeverHungry", cheatNeverHungry);
			cheatSuperSpeed = bool("cheatSuperSpeed", cheatSuperSpeed);
			ultrakillDir = RAW.getProperty("ultrakillDir", ultrakillDir).trim();
			ukPrefs.clear();
			for (String k : RAW.stringPropertyNames()) {
				if (k.startsWith("uk.")) ukPrefs.put(k.substring(3), RAW.getProperty(k).trim());
			}
			save();
		} catch (Exception e) {
			System.err.println("[Ultracraft] config: " + e);
		}
	}

	public static void save() {
		if (configFile == null) return;
		RAW.setProperty("v1Height", Integer.toString(v1Height));
		RAW.setProperty("autoV1", Boolean.toString(autoV1));
		RAW.setProperty("launchUltrakill", Boolean.toString(launchUltrakill));
		RAW.setProperty("sharpShop", Boolean.toString(sharpShop));
		RAW.setProperty("ukFpsCap", Integer.toString(ukFps));
		RAW.setProperty("lockStep", Boolean.toString(lockStep));
		RAW.setProperty("lowLatency", Boolean.toString(lowLatency));
		RAW.setProperty("effects", Integer.toString(effects));
		RAW.setProperty("stainCap", Integer.toString(stainCap));
		RAW.setProperty("extraGore", Boolean.toString(extraGore));
		RAW.setProperty("terrainRange", Integer.toString(terrainRange));
		RAW.setProperty("playerBlockDamage", Boolean.toString(playerBlockDamage));
		RAW.setProperty("enemyBlockDamage", Boolean.toString(enemyBlockDamage));
		RAW.setProperty("impactFrames", String.format(Locale.ROOT, "%.1f", impactFrames));
		RAW.setProperty("fightMusic", fightMusic);
		RAW.setProperty("calmMusic", Boolean.toString(calmMusic));
		RAW.setProperty("cheatNeverHungry", Boolean.toString(cheatNeverHungry));
		RAW.setProperty("cheatSuperSpeed", Boolean.toString(cheatSuperSpeed));
		RAW.setProperty("ultrakillDir", ultrakillDir);
		try {
			configFile.getParentFile().mkdirs();
			try (OutputStream out = Files.newOutputStream(configFile.toPath())) {
				RAW.store(out, "Ultracraft 1.12.2 (same keys as the Fabric version; v1Height = ULTRAKILL render height, lower = faster)");
			}
		} catch (Exception e) {
			System.err.println("[Ultracraft] config save: " + e);
		}
	}

	/** ULTRAKILL's frame rate cap now: the set one, or Minecraft's own limit (see ClientUtil). */
	public static int ukFpsNow() {
		if (ukFps > 0) return ukFps;
		return ClientUtil.minecraftFpsLimit();
	}

	/** What ULTRAKILL needs to know of these (sent on connecting and after every change). */
	public static void sendOpts() {
		UkLink.send(String.format(Locale.ROOT, "OPTS impact=%.2f fps=%d playerBlocks=%d enemyBlocks=%d lowlat=%d fx=%d gore=%d stains=%d", impactFrames, ukFpsNow(),
			playerBlockDamage ? 1 : 0, enemyBlockDamage ? 1 : 0, lowLatency ? 1 : 0, effects, extraGore ? 1 : 0, stainCap > 0 ? 1 : 0));
		for (Map.Entry<String, String> e : ukPrefs.entrySet()) UkLink.send("UKPREF " + e.getKey() + " " + e.getValue());
		UkLink.send("MUSICOPTS calm=" + (calmMusic ? 1 : 0));
		UkLink.send("MUSIC " + fightMusic);
	}
}
