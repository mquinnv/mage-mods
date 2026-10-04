package net.mage.cubewheel.config;

import net.mage.cubewheel.events.EventSchedule;
import net.mage.cubewheel.hud.HudLayout;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Validates and repairs a {@link CubeWheelConfig} in memory (no I/O). Pure: no Minecraft/Fabric imports. */
public final class ConfigNormalizer {
	private ConfigNormalizer() {}

	/**
	 * Validates and repairs {@code c} in place: clamps numbers, fills missing sections with defaults, drops invalid
	 * entries (reported in {@code warnings}). No file access, so the settings screen can run it on a draft.
	 */
	public static void normalize(CubeWheelConfig c, List<String> warnings) {
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
		if (c.charms == null) c.charms = new CubeWheelConfig.Charms();
		c.charms.position = normalizePosition(c.charms.position, DefaultConfig.charmsPosition());
		if (c.status == null) c.status = new CubeWheelConfig.Status();
		c.status.position = normalizePosition(c.status.position, DefaultConfig.statusPosition());
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

	/** Trimmed, blank entries dropped, leading "/" added, capped at ConfigStore.MAX_REFRESH_COMMANDS. */
	private static List<String> normalizeCommands(List<String> in) {
		List<String> out = new ArrayList<>();
		for (String cmd : in) {
			if (cmd == null || cmd.isBlank()) continue;
			if (out.size() == ConfigStore.MAX_REFRESH_COMMANDS) break;
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

	static List<WheelNode> normalizeNodes(List<WheelNode> in) {
		List<WheelNode> out = new ArrayList<>();
		for (WheelNode n : in) {
			if (n == null || n.label == null || n.label.isBlank()) continue;
			boolean hasCommand = n.command != null && !n.command.isBlank();
			if (n.dynamic != null && WheelNode.SLICE_SOURCES.contains(n.dynamic)) {
				if (hasCommand) continue;
				n.command = null;
				n.children = null; // a live slice never opens a ring
				n.outer = normalizeOuter(n.outer, WheelNode.MAX_OUTER);
				n.arc = normalizeArc(n.arc);
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
					// client actions (cubewheel:...) are never server commands, so they keep no slash
					n.command = cmd.startsWith("/") || net.mage.cubewheel.ClientActions.is(cmd) ? cmd : "/" + cmd;
				} else {
					n.command = null;
					n.children = normalizeNodes(n.children);
				}
			}
			n.outer = normalizeOuter(n.outer, WheelNode.MAX_OUTER);
			n.arc = normalizeArc(n.arc);
			out.add(n);
		}
		return out;
	}

	/** A slice's arc entries: valid plain commands only (no rings, live sources, tiers or arcs of their own). */
	private static List<WheelNode> normalizeArc(List<WheelNode> arc) {
		if (arc == null) return null;
		List<WheelNode> ok = new ArrayList<>();
		for (WheelNode a : normalizeNodes(arc)) {
			if (a.command == null) continue;
			a.outer = null;
			a.arc = null;
			ok.add(a);
		}
		return ok.isEmpty() ? null : ok;
	}

	/**
	 * A slice's chain of outer entries, each validated like any node; an invalid one ends the chain, and it is
	 * cut after {@code depth} entries.
	 */
	private static WheelNode normalizeOuter(WheelNode outer, int depth) {
		if (outer == null || depth <= 0) return null;
		WheelNode next = outer.outer;
		outer.outer = null;
		List<WheelNode> one = new ArrayList<>();
		one.add(outer);
		List<WheelNode> ok = normalizeNodes(one);
		if (ok.isEmpty()) return null;
		ok.get(0).outer = normalizeOuter(next, depth - 1);
		return ok.get(0);
	}
}
