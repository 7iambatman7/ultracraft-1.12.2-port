package dev.ultracraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * Minecraft started from the normal launcher brings ULTRAKILL along: through Steam with -ultracraft (its window hidden,
 * the Sandbox loading in the background), and closing Minecraft closes the ULTRAKILL it started. An ULTRAKILL already
 * running is used as it is. (ULTRAKILL needs BepInEx 5 and the UltraBridge plugin; see the README.)
 */
final class UkLauncher {
	private static boolean started;

	private UkLauncher() {}

	static boolean running() {
		try {
			Process p = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq ULTRAKILL.exe", "/NH").redirectErrorStream(true).start();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				String line;
				while ((line = r.readLine()) != null) {
					if (line.toLowerCase().contains("ultrakill.exe")) return true;
				}
			}
		} catch (Exception ignored) {
		}
		return false;
	}

	static void launch() {
		if (!UltracraftConfig.launchUltrakill || System.getProperty("ultracraft.noLaunch") != null) return;
		if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
		if (UkLink.connected || running()) {
			System.out.println("[Ultracraft] ULTRAKILL is already running");
			return;
		}
		String steam = steamExe();
		if (steam == null) {
			System.out.println("[Ultracraft] Steam not found: start ULTRAKILL yourself (with the UltraBridge plugin)");
			return;
		}
		try {
			new ProcessBuilder(steam, "-applaunch", "1229490", "-ultracraft", "-screen-fullscreen", "0", "-screen-width", "1280", "-screen-height", "720").start();
			started = true;
			System.out.println("[Ultracraft] starting ULTRAKILL through " + steam);
		} catch (Exception e) {
			System.out.println("[Ultracraft] couldn't start ULTRAKILL: " + e);
		}
	}

	/** On exit: ULTRAKILL quits itself when asked. */
	static void close() {
		if (!started) return;
		if (UkLink.connected) UkLink.send("QUIT");
	}

	/** Steam's own record of where it is (HKCU\Software\Valve\Steam SteamExe), or the usual place. */
	private static String steamExe() {
		try {
			Process p = new ProcessBuilder("reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamExe").redirectErrorStream(true).start();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				String line;
				while ((line = r.readLine()) != null) {
					int i = line.indexOf("REG_SZ");
					if (line.contains("SteamExe") && i >= 0) {
						String path = line.substring(i + 6).trim().replace('/', '\\');
						if (new File(path).isFile()) return path;
					}
				}
			}
		} catch (Exception ignored) {
		}
		String fallback = "C:\\Program Files (x86)\\Steam\\steam.exe";
		return new File(fallback).isFile() ? fallback : null;
	}
}
