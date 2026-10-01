package net.mage.cubewheel.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.mage.cubewheel.events.EventSchedule;
import net.mage.cubewheel.hud.HudLayout;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
		normalize(parsed, problems);
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
	 * stacks under it.
	 */
	private static boolean migrate(CubeWheelConfig c, List<String> notes) {
		if (c.configVersion >= DefaultConfig.CONFIG_VERSION) return false;
		if (c.configVersion < 2 && c.tracker != null && (c.tracker.hudMaxLines == 6 || c.tracker.hudMaxLines == 8)) {
			c.tracker.hudMaxLines = DefaultConfig.HUD_MAX_LINES;
		}
		if (c.configVersion < 3 && c.wheel != null) {
			WheelUpgrade.Result r = WheelUpgrade.upgrade(normalizeNodes(c.wheel));
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
		Path parent = file.getParent();
		if (parent != null) Files.createDirectories(parent);
		try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(current(), w);
		}
	}

	private static void normalize(CubeWheelConfig c, List<String> warnings) {
		if (c.serverHosts == null) c.serverHosts = DefaultConfig.serverHosts();
		if (c.tracker == null) c.tracker = new CubeWheelConfig.Tracker();
		if (c.tracker.sources == null) c.tracker.sources = DefaultConfig.trackerSources();
		c.tracker.refreshCommands = c.tracker.refreshCommands == null
				? DefaultConfig.refreshCommands() : normalizeCommands(c.tracker.refreshCommands);
		if (c.tracker.sidebarLinks == null) c.tracker.sidebarLinks = DefaultConfig.sidebarLinks();
		else if (DefaultConfig.OLD_SKILLS_LINK.equals(c.tracker.sidebarLinks.get("Skills"))) {
			c.tracker.sidebarLinks.put("Skills", DefaultConfig.SKILLS_LINK);
		}
		if (c.tracker.survivalSidebarPattern == null) c.tracker.survivalSidebarPattern = DefaultConfig.SURVIVAL_SIDEBAR;
		c.tracker.worldFilter = net.mage.cubewheel.tracker.local.WorldScope.Mode.parse(c.tracker.worldFilter).name()
				.toLowerCase(java.util.Locale.ROOT);
		if (c.tracker.local == null) c.tracker.local = DefaultConfig.local();
		if (c.tracker.jobsPanel == null) c.tracker.jobsPanel = new CubeWheelConfig.JobsPanel();
		c.tracker.jobsPanel.position = normalizePosition(c.tracker.jobsPanel.position, DefaultConfig.jobsPanelPosition());
		c.tracker.position = normalizePosition(c.tracker.position, DefaultConfig.trackerPosition());
		c.tracker.local.worlds = c.tracker.local.worlds == null ? DefaultConfig.manaWorlds() : normalizeWords(c.tracker.local.worlds);
		c.tracker.local.specialWorlds = c.tracker.local.specialWorlds == null
				? DefaultConfig.manaWorlds() : normalizeWords(c.tracker.local.specialWorlds);
		if (c.boosters == null) c.boosters = new CubeWheelConfig.Boosters();
		c.boosters.position = normalizePosition(c.boosters.position, DefaultConfig.boostersPosition());
		normalizeEvents(c, warnings);
		if (c.cooldowns == null) c.cooldowns = new CubeWheelConfig.Cooldowns();
		c.cooldowns.position = normalizePosition(c.cooldowns.position, DefaultConfig.cooldownsPosition());
		if (c.svas == null) c.svas = new CubeWheelConfig.Svas();
		normalizeDailyReward(c, warnings);
		c.wheel = c.wheel == null ? DefaultConfig.wheel() : normalizeNodes(c.wheel);
		c.vaultCount = Math.max(0, Math.min(54, c.vaultCount));
		c.listThreshold = Math.max(3, Math.min(16, c.listThreshold));
		c.tracker.nearThreshold = Math.max(0.0, Math.min(1.0, c.tracker.nearThreshold));
		c.tracker.hudMaxLines = Math.max(1, Math.min(20, c.tracker.hudMaxLines));
	}

	private static void normalizeDailyReward(CubeWheelConfig c, List<String> warnings) {
		if (c.dailyReward == null) c.dailyReward = new CubeWheelConfig.DailyReward();
		CubeWheelConfig.DailyReward d = c.dailyReward;
		d.dailyHours = Math.max(1, Math.min(168, d.dailyHours));
		d.weeklyDays = Math.max(1, Math.min(60, d.weeklyDays));
		d.monthlyDays = Math.max(1, Math.min(60, d.monthlyDays));
		if (d.menuTitlePattern == null) d.menuTitlePattern = DefaultConfig.COW_MENU_TITLE;
		else if (!validRegex(d.menuTitlePattern)) {
			warnings.add("dailyReward.menuTitlePattern is not a valid regex, using the default");
			d.menuTitlePattern = DefaultConfig.COW_MENU_TITLE;
		}
	}

	/** Invalid regexes and blank commands are dropped with a warning; commands get a leading "/". */
	private static Map<String, String> normalizeBossWarps(Map<String, String> in, List<String> warnings) {
		if (in == null) return DefaultConfig.bossWarps();
		Map<String, String> out = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : in.entrySet()) {
			String cmd = e.getValue() == null ? "" : e.getValue().trim();
			if (e.getKey() == null || !validRegex(e.getKey()) || cmd.isEmpty()) {
				warnings.add("events.bossWarps \"" + e.getKey() + "\" ignored: needs a valid regex and a command");
				continue;
			}
			out.put(e.getKey(), cmd.startsWith("/") ? cmd : "/" + cmd);
		}
		return out;
	}

	private static boolean validRegex(String regex) {
		try {
			Pattern.compile(regex);
			return true;
		} catch (PatternSyntaxException ex) {
			return false;
		}
	}

	private static void normalizeEvents(CubeWheelConfig c, List<String> warnings) {
		if (c.events == null) c.events = new CubeWheelConfig.Events();
		CubeWheelConfig.Events e = c.events;
		e.bossWarps = normalizeBossWarps(e.bossWarps, warnings);
		e.bossMinutes = Math.max(1, Math.min(180, e.bossMinutes));
		e.position = normalizePosition(e.position, DefaultConfig.eventsPosition());
		e.show = Math.max(1, Math.min(10, e.show));
		e.alertMinutes = Math.max(0, Math.min(60, e.alertMinutes));
		if (e.timezone == null || e.timezone.isBlank() || zone(e.timezone) == null) {
			if (e.timezone != null && !e.timezone.isBlank()) {
				warnings.add("events.timezone \"" + e.timezone + "\" is not a time zone, using " + DefaultConfig.EVENTS_TIMEZONE);
			}
			e.timezone = DefaultConfig.EVENTS_TIMEZONE;
		}
		if (e.schedule == null) {
			e.schedule = DefaultConfig.events();
			return;
		}
		List<CubeWheelConfig.EventDef> out = new ArrayList<>();
		for (CubeWheelConfig.EventDef d : e.schedule) {
			if (d == null) continue;
			String name = d.name == null ? "" : d.name.trim();
			if (name.isEmpty()) {
				warnings.add("events.schedule: an entry without a name was ignored");
				continue;
			}
			try {
				EventSchedule.parse(d.when);
			} catch (IllegalArgumentException ex) {
				warnings.add("events.schedule \"" + name + "\" ignored: " + ex.getMessage());
				continue;
			}
			if (d.timezone != null && !d.timezone.isBlank() && zone(d.timezone) == null) {
				warnings.add("events.schedule \"" + name + "\" ignored: unknown time zone \"" + d.timezone + "\"");
				continue;
			}
			d.name = name;
			d.when = d.when.trim();
			out.add(d);
		}
		e.schedule = out;
	}

	/** The zone, or null if the id is null or unknown. */
	public static ZoneId zone(String id) {
		if (id == null) return null;
		try {
			return ZoneId.of(id.trim());
		} catch (DateTimeException ex) {
			return null;
		}
	}

	/** Missing position -> the default; unknown corner -> "top_left"; offsets clamped to 0..4000. */
	static CubeWheelConfig.Position normalizePosition(CubeWheelConfig.Position p, CubeWheelConfig.Position def) {
		if (p == null) return def;
		p.corner = HudLayout.Corner.parse(p.corner).id();
		p.x = Math.max(0, Math.min(4000, p.x));
		p.y = Math.max(0, Math.min(4000, p.y));
		return p;
	}

	/** Trimmed, blank entries dropped, leading "/" added, capped at MAX_REFRESH_COMMANDS. */
	private static List<String> normalizeCommands(List<String> in) {
		List<String> out = new ArrayList<>();
		for (String cmd : in) {
			if (cmd == null || cmd.isBlank()) continue;
			if (out.size() == MAX_REFRESH_COMMANDS) break;
			String c = cmd.trim();
			out.add(c.startsWith("/") ? c : "/" + c);
		}
		return out;
	}

	/** Trimmed, blank and null entries dropped. */
	private static List<String> normalizeWords(List<String> in) {
		List<String> out = new ArrayList<>();
		for (String w : in) {
			if (w != null && !w.isBlank()) out.add(w.trim());
		}
		return out;
	}

	private static List<WheelNode> normalizeNodes(List<WheelNode> in) {
		List<WheelNode> out = new ArrayList<>();
		for (WheelNode n : in) {
			if (n == null || n.label == null || n.label.isBlank()) continue;
			boolean hasCommand = n.command != null && !n.command.isBlank();
			if (n.dynamic != null && WheelNode.SLICE_SOURCES.contains(n.dynamic)) {
				if (hasCommand) continue;
				n.command = null;
				n.children = null; // a live slice never opens a ring
				out.add(n);
				continue;
			}
			boolean hasDynamic = n.dynamic != null && WheelNode.RING_SOURCES.contains(n.dynamic);
			if (hasDynamic) {
				if (hasCommand) continue;
				n.command = null;
				n.children = n.children == null ? new ArrayList<>() : normalizeNodes(n.children);
			} else {
				if (n.dynamic != null) continue; // unknown dynamic source
				if (hasCommand == (n.children != null)) continue; // need exactly one
				if (hasCommand) {
					String cmd = n.command.trim();
					n.command = cmd.startsWith("/") ? cmd : "/" + cmd;
				} else {
					n.command = null;
					n.children = normalizeNodes(n.children);
				}
			}
			out.add(n);
		}
		return out;
	}
}
