package net.mage.cubewheel.config;

import java.util.List;
import java.util.Map;

public final class CubeWheelConfig {
	/**
	 * Format version of this file; 0 (absent) = written before versioning. Loading migrates older files
	 * once and writes them back, see ConfigStore.
	 */
	public int configVersion;
	public boolean enabled = true;
	public List<String> serverHosts;
	public int vaultCount = 3;
	public int listThreshold = 8;
	public Tracker tracker = new Tracker();
	/** Booster countdowns parsed from chat. */
	public Boosters boosters = new Boosters();
	/** Scheduled Survival events with countdowns and "starts soon" alerts. */
	public Events events = new Events();
	/** Countdowns for custom-item abilities whose lore has a cooldown. */
	public Cooldowns cooldowns = new Cooldowns();
	/** SVA catalog screen and tooltip line, from ManaCube's public API. */
	public Svas svas = new Svas();
	public List<WheelNode> wheel;

	public static final class Svas {
		/** Master switch: when false nothing is fetched and the catalog key does nothing. */
		public boolean enabled = true;
		/** Append "✦ SVA · Circulation: N" to tooltips of items whose name is an SVA's. */
		public boolean tooltip = true;
	}

	/**
	 * Where a HUD panel sits: a screen corner ("top_left", "top_right", "bottom_left", "bottom_right") plus
	 * an offset in GUI pixels from that corner. Panels in the same corner stack instead of overlapping.
	 */
	public static final class Position {
		public String corner = "top_left";
		public int x = 4;
		public int y = 4;

		public Position() {}

		public Position(String corner, int x, int y) {
			this.corner = corner;
			this.x = x;
			this.y = y;
		}
	}

	public static final class Events {
		/** Master switch for the panel and the alerts. */
		public boolean enabled = true;
		/** Panel on/off (saved by the "Toggle event HUD" key); alerts do not depend on it. */
		public boolean hudVisible = true;
		/** How many upcoming events the panel lists, 1-10. */
		public int show = 3;
		/** Chat alert this many minutes before a start, 0-60; 0 = no alerts. */
		public int alertMinutes = 5;
		/** Time zone for entries without their own; the wiki's "EST" times are New York wall-clock times. */
		public String timezone = DefaultConfig.EVENTS_TIMEZONE;
		/** The schedule; null = the defaults from ManaCube's wiki. */
		public List<EventDef> schedule;
		public Position position = DefaultConfig.eventsPosition();
	}

	/** One scheduled event: {@code when} is "at 08:00, 13:00" or "every 3h from 00:15" (see EventSchedule). */
	public static final class EventDef {
		public String name;
		public String when;
		/** Optional; falls back to {@code events.timezone}. */
		public String timezone;
		public boolean enabled = true;

		public EventDef() {}

		public EventDef(String name, String when) {
			this.name = name;
			this.when = when;
		}
	}

	public static final class Cooldowns {
		/** Start countdowns when you use such an item, and show the panel. */
		public boolean enabled = true;
		/** Also show the held item's "Uses: N" lore value. */
		public boolean showUses = true;
		/** Also track mcMMO super-ability cooldowns (Super Breaker, Tree Feller, ...) from their messages. */
		public boolean mcmmo = true;
		public Position position = DefaultConfig.cooldownsPosition();
	}

	public static final class Boosters {
		/** Parse booster chat messages and show the countdown panel. */
		public boolean enabled = true;
		public Position position = DefaultConfig.boostersPosition();
	}

	public static final class Tracker {
		public double nearThreshold = 0.8;
		public int hudMaxLines = DefaultConfig.HUD_MAX_LINES;
		public boolean hudVisible = true;
		public Map<String, String> sources;
		/** Sent one at a time by the "Refresh trackers" key; each should open a progress menu. */
		public List<String> refreshCommands;
		/** Sidebar key ("Skills") -> regex on trackable names whose current value follows it live. */
		public Map<String, String> sidebarLinks;
		/**
		 * Regex on the sidebar title: menu scanning, refresh runs and local counting only run while it
		 * matches (ManaCube hosts other gamemodes on the same address). "" switches this check off.
		 */
		public String survivalSidebarPattern = DefaultConfig.SURVIVAL_SIDEBAR;
		/**
		 * HUD entries for the world you are in: "sort" (default) lists entries naming the current world first
		 * (after pinned ones) and other worlds' last, "hide" drops other worlds' unpinned entries, "off".
		 */
		public String worldFilter = "sort";
		/** Local counting: live "~" estimates between menu reads. */
		public Local local = new Local();
	}

	public static final class Local {
		/** Master switch; when false nothing is counted and stored estimates are not shown (not deleted). */
		public boolean enabled = true;
		public boolean blocks = true;
		public boolean kills = true;
		public boolean fish = true;
		/** Count shears used on sheep (and other shearables) once the server confirms the shear. */
		public boolean shear = true;
		/** World names recognised at the start of an objective's noun ("Wolfhaven Resources"). */
		public List<String> worlds;
		/** Worlds that count as "special worlds (/worlds)". */
		public List<String> specialWorlds;
	}
}
