package dev.ultracraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * Which of ULTRAKILL's songs plays in fights: off, a random one each fight, or one picked from its soundtrack (the
 * list ULTRAKILL's Cyber Grind jukebox has, sent over as SONGS), grouped by where it plays in ULTRAKILL.
 */
final class UcMusicScreen extends OptionsSubScreen {
	/** ULTRAKILL's soundtrack: song keys ("Levels/Act 1/Limbo/Versus.asset"), as its catalog has them (SONGS). */
	static final List<String> SONGS = new ArrayList<>();

	UcMusicScreen(Screen last) {
		super(last, Minecraft.getInstance().options, Component.literal("Fight Music"));
	}

	/** SONGS key;key;...: ULTRAKILL's soundtrack. */
	static void songs(String data) {
		SONGS.clear();
		for (String k : data.split(";")) if (!k.isBlank()) SONGS.add(k.trim());
	}

	/** A song's name from its key ("Levels/Act 1/Limbo/Versus.asset": "Versus"). */
	static String name(String key) {
		if (key.equals("off")) return "Off";
		if (key.equals("random")) return "Random";
		String n = key.substring(key.lastIndexOf('/') + 1);
		return n.endsWith(".asset") ? n.substring(0, n.length() - 6) : n;
	}

	/** Where it plays ("Levels/Act 1/Limbo/Versus.asset": "Act 1: Limbo"). */
	static String group(String key) {
		String[] parts = key.split("/");
		if (parts.length >= 4 && parts[0].equals("Levels")) return parts[1] + ": " + parts[2];
		return parts.length >= 2 ? parts[parts.length - 2] : "Other";
	}

	@Override
	protected void addOptions() {
		list.addSmall(choice("off", "No ULTRAKILL music in fights."), choice("random", "A different ULTRAKILL song each fight."));
		if (SONGS.isEmpty()) {
			list.addHeader(Component.literal("ULTRAKILL hasn't listed its soundtrack yet: start it (or open a world) and come back."));
			return;
		}
		Map<String, List<String>> groups = new LinkedHashMap<>();
		for (String k : SONGS) groups.computeIfAbsent(group(k), g -> new ArrayList<>()).add(k);
		for (var e : groups.entrySet()) {
			list.addHeader(Component.literal(e.getKey()));
			List<AbstractWidget> row = new ArrayList<>();
			for (String k : e.getValue()) row.add(choice(k, null));
			list.addSmall(row);
		}
	}

	private Button choice(String key, String tooltip) {
		boolean picked = UltracraftConfig.fightMusic.equals(key);
		Button b = Button.builder(Component.literal((picked ? "> " : "") + name(key) + (picked ? " <" : "")), btn -> {
			UltracraftConfig.fightMusic = key;
			UltracraftConfig.sendMusic();
			UltracraftConfig.save();
			// the list again, with the new pick marked
			minecraft.setScreen(new UcMusicScreen(lastScreen));
		}).build();
		if (tooltip != null) b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tooltip)));
		return b;
	}

	/** A short label for the settings page ("Random", "Versus"). */
	static String current() {
		return name(UltracraftConfig.fightMusic);
	}
}
