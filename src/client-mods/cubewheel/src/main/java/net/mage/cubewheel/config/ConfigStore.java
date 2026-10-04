package net.mage.cubewheel.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.mage.cubewheel.hud.HudLayout;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads, validates and saves cubewheel.json. Pure: no Minecraft/Fabric imports. */
public final class ConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	/** A refresh run sends at most this many commands. */
	public static final int MAX_REFRESH_COMMANDS = 8;

	private final Path file;
	private CubeWheelConfig current;
	private boolean lastLoadOk = true;
	private List<String> warnings = List.of();

	public ConfigStore(Path file) {
		this.file = file;
	}

	public CubeWheelConfig current() {
		if (current == null) current = DefaultConfig.create();
		return current;
	}

	/**
	 * False when the most recent reload() returned an error, i.e. the file on disk is not what is in
	 * memory; callers must then not save(), or they would overwrite the user's (broken) file.
	 */
	public boolean lastLoadOk() {
		return lastLoadOk;
	}

	/** Non-fatal problems of the last successful load (e.g. an event entry that was ignored). */
	public List<String> warnings() {
		return warnings;
	}

	/** Returns null on success, otherwise an error message (previous config is kept). */
	public String reload() {
		String err = load();
		lastLoadOk = err == null;
		return err;
	}

	private String load() {
		if (!Files.exists(file)) {
			current = DefaultConfig.create();
			try {
				save();
			} catch (IOException | RuntimeException e) {
				return "cubewheel.json: could not write defaults: " + e.getMessage();
			}
			return null;
		}
		CubeWheelConfig parsed;
		try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonReader r = new JsonReader(in);
			r.setStrictness(Strictness.STRICT);
			parsed = GSON.fromJson(r, CubeWheelConfig.class);
			if (r.peek() != JsonToken.END_DOCUMENT) throw new JsonParseException("trailing content");
		} catch (JsonParseException | IOException | IllegalStateException e) {
			if (current == null) current = DefaultConfig.create();
			return "cubewheel.json: " + e.getMessage();
		}
		if (parsed == null) parsed = DefaultConfig.create();
		List<String> problems = new ArrayList<>();
		boolean migrated = migrate(parsed, problems);
		ConfigNormalizer.normalize(parsed, problems);
		warnings = List.copyOf(problems);
		current = parsed;
		if (migrated) {
			// Write the upgrade back so it happens once: a value the user sets afterwards is kept.
			try {
				save();
			} catch (IOException | RuntimeException e) {
				// Not fatal: the upgrade is applied in memory and simply repeats on the next load.
			}
		}
		return null;
	}

	/**
	 * One-time upgrades of files older than {@link DefaultConfig#CONFIG_VERSION}; returns true if the file
	 * should be written back. Version 2: an untouched old HUD default (6, later 8) becomes 10. Version 3: the
	 * wheel is replaced by the new default layout; leaves you added are kept under More › Custom (reported in
	 * {@code notes}, which end up in {@link #warnings()} and the log). Version 4: the new Jobs panel takes the
	 * offset of your top-left panels (the lowest one), so it stacks below them instead of sitting above them.
	 * Version 5: the tracker leaves the top right for a panel that takes the Jobs panel's corner and offset, so it
	 * stacks under it. Version 6: chains of outer tiers become arcs.
	 */
	private static boolean migrate(CubeWheelConfig c, List<String> notes) {
		if (c.configVersion >= DefaultConfig.CONFIG_VERSION) return false;
		if (c.configVersion < 2 && c.tracker != null && (c.tracker.hudMaxLines == 6 || c.tracker.hudMaxLines == 8)) {
			c.tracker.hudMaxLines = DefaultConfig.HUD_MAX_LINES;
		}
		if (c.configVersion < 3 && c.wheel != null) {
			WheelUpgrade.Result r = WheelUpgrade.upgrade(ConfigNormalizer.normalizeNodes(c.wheel));
			c.wheel = r.wheel();
			notes.add(r.moved().isEmpty()
					? "wheel upgraded to the new default layout"
					: "wheel upgraded to the new default layout; your entries were moved to " + DefaultConfig.MORE + " › "
							+ WheelUpgrade.CUSTOM + ": " + String.join(", ", r.moved()));
		}
		if (c.configVersion < 4 && c.tracker != null) {
			if (c.tracker.jobsPanel == null) c.tracker.jobsPanel = new CubeWheelConfig.JobsPanel();
			c.tracker.jobsPanel.position = jobsPanelPosition(c);
		}
		if (c.configVersion < 5 && c.tracker != null) {
			CubeWheelConfig.Position j = c.tracker.jobsPanel == null ? null : c.tracker.jobsPanel.position;
			c.tracker.position = j == null ? DefaultConfig.trackerPosition() : new CubeWheelConfig.Position(j.corner, j.x, j.y);
		}
		if (c.configVersion < 6 && c.wheel != null && WheelUpgrade.chainsToArcs(c.wheel)) {
			notes.add("wheel: entries beyond a slice now fan out around it (arcs) instead of sticking out");
		}
		if (c.configVersion < 7 && c.events != null && c.events.bossWarps != null) {
			// The rule new in version 7 (the Cursed Witch's own warp) goes first, ahead of the world rules; rules you
			// removed on purpose stay removed.
			Map<String, String> merged = new LinkedHashMap<>();
			String witch = "(?i)cursed witch";
			if (!c.events.bossWarps.containsKey(witch)) merged.put(witch, DefaultConfig.bossWarps().get(witch));
			merged.putAll(c.events.bossWarps);
			c.events.bossWarps = merged;
		}
		c.configVersion = DefaultConfig.CONFIG_VERSION;
		return true;
	}

	/** The top-left offset of the lowest existing top-left panel (events, boosters, cooldowns), else the default. */
	static CubeWheelConfig.Position jobsPanelPosition(CubeWheelConfig c) {
		CubeWheelConfig.Position best = null;
		List<CubeWheelConfig.Position> existing = new ArrayList<>();
		if (c.events != null) existing.add(c.events.position);
		if (c.boosters != null) existing.add(c.boosters.position);
		if (c.cooldowns != null) existing.add(c.cooldowns.position);
		for (CubeWheelConfig.Position p : existing) {
			if (p == null || HudLayout.Corner.parse(p.corner) != HudLayout.Corner.TOP_LEFT) continue;
			if (best == null || p.y > best.y) best = p;
		}
		return best == null ? DefaultConfig.jobsPanelPosition()
				: new CubeWheelConfig.Position(HudLayout.Corner.TOP_LEFT.id(), best.x, best.y);
	}

	public void save() throws IOException {
		write(current());
	}

	private void write(CubeWheelConfig c) throws IOException {
		Path parent = file.getParent();
		if (parent != null) Files.createDirectories(parent);
		try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(c, w);
		}
	}

	/** A deep copy (a Gson round-trip, as the file is read and written) that shares no mutable objects with {@code c}. */
	public static CubeWheelConfig copyOf(CubeWheelConfig c) {
		return GSON.fromJson(GSON.toJson(c), CubeWheelConfig.class);
	}

	/**
	 * Validates {@code draft} (see {@link ConfigNormalizer}), makes it the live config and writes it to the file;
	 * returns the normalisation warnings. Works even after a failed load, overwriting the broken file (the settings
	 * screen's own save), unlike the keybind toggles. If the write fails the exception propagates and neither
	 * {@link #current()} nor {@link #lastLoadOk()} changes.
	 */
	public List<String> apply(CubeWheelConfig draft) throws IOException {
		draft.configVersion = DefaultConfig.CONFIG_VERSION;
		List<String> problems = new ArrayList<>();
		ConfigNormalizer.normalize(draft, problems);
		write(draft); // before the swap, so an IOException leaves the live state untouched
		current = draft;
		lastLoadOk = true;
		warnings = List.copyOf(problems);
		return warnings;
	}
}
