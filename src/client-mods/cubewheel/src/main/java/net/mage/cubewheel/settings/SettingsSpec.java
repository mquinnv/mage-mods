package net.mage.cubewheel.settings;

import net.mage.cubewheel.config.ConfigStore;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.config.DefaultConfig;
import net.mage.cubewheel.config.Lines;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * What the settings screen shows, as plain data: categories of groups of settings, each bound to one
 * {@link CubeWheelConfig} field. Pure (no Minecraft, Fabric or YACL imports) so it is unit-tested; SettingsScreens
 * turns it into YACL options without any per-setting code. Ranges are the ones ConfigNormalizer clamps to.
 */
public final class SettingsSpec {
	private SettingsSpec() {}

	/** Name of the HUD panels category; the screen adds its "Arrange panels..." button there. */
	public static final String HUD_PANELS = "HUD panels";

	/** One editable config field. {@code id} is its JSON path ("dailyReward.dailyHours"), stable across versions. */
	public sealed interface Setting permits Toggle, IntRange, DoubleRange, Choice, Text, TextList, MapLines, EventLines {
		String id();

		String label();

		String tooltip();
	}

	/** A boolean field: a tick box. */
	public record Toggle(String id, String label, String tooltip, Function<CubeWheelConfig, Boolean> getter,
			BiConsumer<CubeWheelConfig, Boolean> setter, boolean defaultValue) implements Setting {}

	/** An int field limited to {@code min}..{@code max}: a slider. */
	public record IntRange(String id, String label, String tooltip, Function<CubeWheelConfig, Integer> getter,
			BiConsumer<CubeWheelConfig, Integer> setter, int defaultValue, int min, int max) implements Setting {}

	/** A double field limited to {@code min}..{@code max}, moved in steps of {@code step}: a slider. */
	public record DoubleRange(String id, String label, String tooltip, Function<CubeWheelConfig, Double> getter,
			BiConsumer<CubeWheelConfig, Double> setter, double defaultValue, double min, double max, double step)
			implements Setting {}

	/** A string field that takes one of a fixed list of values, each shown under its label: a cycling button. */
	public record Choice(String id, String label, String tooltip, Function<CubeWheelConfig, String> getter,
			BiConsumer<CubeWheelConfig, String> setter, String defaultValue, List<Entry> options) implements Setting {
		/** One allowed value (what the config stores) and what the screen shows for it. */
		public record Entry(String value, String label) {}
	}

	/** A string field: a text box. */
	public record Text(String id, String label, String tooltip, Function<CubeWheelConfig, String> getter,
			BiConsumer<CubeWheelConfig, String> setter, String defaultValue) implements Setting {}

	/** A list-of-strings field: an editable list, one entry per row. The setter stores a mutable copy. */
	public record TextList(String id, String label, String tooltip, Function<CubeWheelConfig, List<String>> getter,
			BiConsumer<CubeWheelConfig, List<String>> setter, List<String> defaultValue) implements Setting {}

	/**
	 * A string-to-string map field edited as text lines, one {@code key -> value} per row (see {@link Lines}). The
	 * getter shows the map as lines; the setter parses lines back and reports each skipped line in {@code problems}.
	 */
	public record MapLines(String id, String label, String tooltip, Function<CubeWheelConfig, List<String>> getter,
			LinesSetter setter, List<String> defaultValue) implements Setting {}

	/**
	 * The event schedule edited as text lines, one {@code name | when [| timezone] [| off]} per row (see {@link Lines}).
	 * Getter and setter work like {@link MapLines}'s.
	 */
	public record EventLines(String id, String label, String tooltip, Function<CubeWheelConfig, List<String>> getter,
			LinesSetter setter, List<String> defaultValue) implements Setting {}

	/**
	 * Stores edited lines into a config; lines it cannot parse are described in {@code problems}, not dropped silently.
	 * Each problem names the setting and the format it expected, so it reads on its own in chat.
	 */
	@FunctionalInterface
	public interface LinesSetter {
		void set(CubeWheelConfig config, List<String> lines, List<String> problems);
	}

	/** A titled group of settings within a category; {@code collapsed} groups start folded. */
	public record Group(String name, List<Setting> settings, boolean collapsed) {
		public Group(String name, List<Setting> settings) {
			this(name, settings, false);
		}
	}

	/** The corners a panel can sit in; the values are exactly the ids {@code HudLayout.Corner.parse} accepts. */
	private static final List<Choice.Entry> CORNERS = List.of(
			new Choice.Entry("top_left", "Top left"), new Choice.Entry("top_right", "Top right"),
			new Choice.Entry("bottom_left", "Bottom left"), new Choice.Entry("bottom_right", "Bottom right"),
			new Choice.Entry("bottom_center", "Bottom centre"), new Choice.Entry("custom", "Custom (dragged)"));

	/** The HUD panels' bounds on a position offset, as ConfigNormalizer clamps them. */
	private static final int OFFSET_MAX = 4000;

	/** One tab of the settings screen. */
	public record Category(String name, List<Group> groups) {}

	/** All categories, in screen order. Defaults are read from a fresh {@link DefaultConfig#create()}. */
	public static List<Category> categories() {
		CubeWheelConfig d = DefaultConfig.create();
		return List.of(
				new Category("General", List.of(
						new Group("CubeWheel", List.of(
								toggle(d, "enabled", "Enabled",
										"Master switch for the command wheel: when off, the wheel key does nothing.",
										c -> c.enabled, (c, v) -> c.enabled = v),
								textList(d, "serverHosts", "Server hosts",
										"CubeWheel only acts on these servers. A host also matches its subdomains"
												+ " (manacube.com matches play.manacube.com).",
										c -> c.serverHosts, (c, v) -> c.serverHosts = v),
								intRange(d, "vaultCount", "Vault count",
										"How many /pv vaults the Vaults ring lists until a /pv page has told"
												+ " CubeWheel your real count.",
										0, 54, c -> c.vaultCount, (c, v) -> c.vaultCount = v),
								intRange(d, "listThreshold", "List threshold",
										"A ring with more entries than this opens as a scrolling list instead.",
										3, 16, c -> c.listThreshold, (c, v) -> c.listThreshold = v))))),
				new Category(HUD_PANELS, List.of(
						new Group("Status", List.of(
								toggle(d, "status.enabled", "Enabled",
										"Show the Status panel: armor set, coordinates, biome, light, FPS, speed and time.",
										c -> c.status.enabled, (c, v) -> c.status.enabled = v),
								toggle(d, "status.armor", "Armor set",
										"Show the gear row: armor and held items (a bar on a piece wearing out), the worn set (\"Phoenix 4/4\") and its"
												+ " set bonus.",
										c -> c.status.armor, (c, v) -> c.status.armor = v))),
						new Group("Events", List.of(
								toggle(d, "events.hudVisible", "Show panel",
										"Panel on/off (also the \"Toggle event HUD\" key). Alerts do not depend on it;"
												+ " their settings are under Events.",
										c -> c.events.hudVisible, (c, v) -> c.events.hudVisible = v))),
						new Group("Jobs", List.of(
								toggle(d, "tracker.jobsPanel.enabled", "Show panel",
										"Panel on/off (also the \"Toggle jobs panel\" key). While on, job entries leave"
												+ " the Tracker panel.",
										c -> c.tracker.jobsPanel.enabled, (c, v) -> c.tracker.jobsPanel.enabled = v))),
						new Group("Tracker", List.of(
								toggle(d, "tracker.hudVisible", "Show panel",
										"Show the Tracker panel with your pinned and nearly finished progress entries.",
										c -> c.tracker.hudVisible, (c, v) -> c.tracker.hudVisible = v),
								intRange(d, "tracker.hudMaxLines", "Max lines",
										"How many entries the Tracker panel lists at most.",
										1, 20, c -> c.tracker.hudMaxLines, (c, v) -> c.tracker.hudMaxLines = v),
								doubleRange(d, "tracker.nearThreshold", "Near threshold",
										"An entry counts as nearly finished from this fraction of its goal"
												+ " (0.8 = 80%) and is listed on the panel.",
										0.0, 1.0, 0.05, c -> c.tracker.nearThreshold, (c, v) -> c.tracker.nearThreshold = v),
								choice(d, "tracker.worldFilter", "World filter",
										"Entries for the world you are in: \"Sort\" lists entries naming the current world"
												+ " first and other worlds' last, \"Hide\" drops other worlds' unpinned"
												+ " entries, \"Off\" leaves them alone.",
										List.of(new Choice.Entry("sort", "Sort by world"),
												new Choice.Entry("hide", "Hide other worlds"),
												new Choice.Entry("off", "Off")),
										c -> c.tracker.worldFilter, (c, v) -> c.tracker.worldFilter = v))),
						new Group("Boosters", List.of(
								toggle(d, "boosters.enabled", "Enabled",
										"Parse booster chat messages and show the countdown panel.",
										c -> c.boosters.enabled, (c, v) -> c.boosters.enabled = v))),
						new Group("Cooldowns", List.of(
								toggle(d, "cooldowns.enabled", "Enabled",
										"Start countdowns when you use such an item, and show the panel.",
										c -> c.cooldowns.enabled, (c, v) -> c.cooldowns.enabled = v),
								toggle(d, "cooldowns.showUses", "Show uses",
										"Also show the held item's \"Uses: N\" lore value.",
										c -> c.cooldowns.showUses, (c, v) -> c.cooldowns.showUses = v),
								toggle(d, "cooldowns.mcmmo", "mcMMO abilities",
										"Also track mcMMO super-ability cooldowns (Super Breaker, Tree Feller, ...)"
												+ " from their messages.",
										c -> c.cooldowns.mcmmo, (c, v) -> c.cooldowns.mcmmo = v))),
						new Group("Charms", List.of(
								toggle(d, "charms.enabled", "Enabled",
										"Show the Charms panel: inventory fill, the worn amulet and carried talismans,"
												+ " vault fill.",
										c -> c.charms.enabled, (c, v) -> c.charms.enabled = v))),
						new Group("Progress popup", List.of(
								toggle(d, "toast.enabled", "Enabled",
										"A short line under the crosshair when local counting adds progress to a tracked"
												+ " entry (\"+1 Mana Wolves  ~10/74\"); green with a \"✓\" when it completes it. Also one-off"
												+ " notices: inventory full, set bonus lost or back, a vault nearly full or full.",
										c -> c.toast.enabled, (c, v) -> c.toast.enabled = v),
								doubleRange(d, "toast.seconds", "Seconds shown",
										"How long the popup stays after the last increment before it fades. Further"
												+ " increments to the same entry add up in it and restart this time.",
										DefaultConfig.TOAST_SECONDS_MIN, DefaultConfig.TOAST_SECONDS_MAX, 0.5,
										c -> c.toast.seconds, (c, v) -> c.toast.seconds = v))),
						new Group("Exact positions", positions(d), true))),
				new Category("Tracker", List.of(
						new Group("Sources & refresh", List.of(
								mapLines(d, "tracker.sources", "Menu sources", "source id -> regex",
										"Which tracker source a menu belongs to. One line per source:"
												+ " \"source id -> regex\", e.g. \"jobs -> (?i)jobs\". A menu whose title"
												+ " matches the regex is read as that source; the first match wins. Menus are also"
												+ " recognised by their items, so a title regex is often not needed.",
										c -> c.tracker.sources, (c, m) -> c.tracker.sources = m),
								textList(d, "tracker.refreshCommands", "Refresh commands",
										"Sent one after another by the \"Refresh trackers\" key; each should open a progress"
												+ " menu. A missing \"/\" is added and at most "
												+ ConfigStore.MAX_REFRESH_COMMANDS + " are kept.",
										c -> c.tracker.refreshCommands, (c, v) -> c.tracker.refreshCommands = v),
								mapLines(d, "tracker.sidebarLinks", "Sidebar links", "sidebar key -> regex",
										"Follows a sidebar value live. One line per link: \"sidebar key -> regex\", e.g."
												+ " \"Skills -> (?i)reach [\\d,]+ skill level\". The entry whose name matches the"
												+ " regex shows that sidebar line's current value.",
										c -> c.tracker.sidebarLinks, (c, m) -> c.tracker.sidebarLinks = m),
								text(d, "tracker.survivalSidebarPattern", "Survival sidebar pattern",
										"Regex on the sidebar title: menu scanning, refresh runs and local counting only run"
												+ " while it matches (ManaCube hosts other gamemodes on the same address)."
												+ " Empty switches this check off.",
										c -> c.tracker.survivalSidebarPattern,
										(c, v) -> c.tracker.survivalSidebarPattern = v))),
						new Group("Local counting", List.of(
								toggle(d, "tracker.local.enabled", "Enabled",
										"Master switch: when off nothing is counted and stored estimates are not shown"
												+ " (they are not deleted).",
										c -> c.tracker.local.enabled, (c, v) -> c.tracker.local.enabled = v),
								toggle(d, "tracker.local.blocks", "Blocks",
										"Count blocks you break.",
										c -> c.tracker.local.blocks, (c, v) -> c.tracker.local.blocks = v),
								toggle(d, "tracker.local.kills", "Kills",
										"Count mobs you kill.",
										c -> c.tracker.local.kills, (c, v) -> c.tracker.local.kills = v),
								toggle(d, "tracker.local.fish", "Fish",
										"Count fish you catch.",
										c -> c.tracker.local.fish, (c, v) -> c.tracker.local.fish = v),
								toggle(d, "tracker.local.shear", "Shearing",
										"Count shears used on sheep (and other shearables) once the server confirms the shear.",
										c -> c.tracker.local.shear, (c, v) -> c.tracker.local.shear = v),
								toggle(d, "tracker.local.milk", "Milking",
										"Count empty buckets used on cows (and other milkable mobs) once the server keeps"
												+ " the milk bucket.",
										c -> c.tracker.local.milk, (c, v) -> c.tracker.local.milk = v),
								toggle(d, "tracker.local.areaBreaks", "Area breaks",
										"Count blocks the server breaks for you (mcMMO Tree Feller, harvester and hammer"
												+ " area tools). Needs Blocks.",
										c -> c.tracker.local.areaBreaks, (c, v) -> c.tracker.local.areaBreaks = v),
								textList(d, "tracker.local.worlds", "Worlds",
										"World names recognised at the start of an objective's noun (\"Wolfhaven Resources\").",
										c -> c.tracker.local.worlds, (c, v) -> c.tracker.local.worlds = v),
								textList(d, "tracker.local.specialWorlds", "Special worlds",
										"Worlds that count as \"special worlds (/worlds)\".",
										c -> c.tracker.local.specialWorlds, (c, v) -> c.tracker.local.specialWorlds = v))))),
				new Category("Events", List.of(
						new Group("Panel & alerts", List.of(
								toggle(d, "events.enabled", "Enabled",
										"Master switch for the panel and the alerts.",
										c -> c.events.enabled, (c, v) -> c.events.enabled = v),
								intRange(d, "events.show", "Events shown",
										"How many upcoming events the panel lists.",
										1, 10, c -> c.events.show, (c, v) -> c.events.show = v),
								intRange(d, "events.alertMinutes", "Alert minutes",
										"Chat alert this many minutes before an event starts; 0 = no alerts.",
										0, 60, c -> c.events.alertMinutes, (c, v) -> c.events.alertMinutes = v),
								text(d, "events.timezone", "Time zone",
										"Time zone for entries without their own; the wiki's \"EST\" times are New York"
												+ " wall-clock times. An unknown zone falls back to the default.",
										c -> c.events.timezone, (c, v) -> c.events.timezone = v))),
						new Group("Schedule", List.of(
								eventLines(d, "events.schedule", "Schedule",
										"One event per line: \"name | when [| timezone] [| off]\". \"when\" is \"at 08:00, 13:00\""
												+ " or \"every 3h from 00:15\"; the optional zone overrides the Time zone above and"
												+ " \"off\" keeps the line but disables it. Examples: \"KOTH | at 00:30, 02:30\" and"
												+ " \"Boss | every 2h from 01:30 | Europe/London | off\"."))),
						new Group("Boss event slice", List.of(
								intRange(d, "events.bossMinutes", "Boss minutes",
										"The \"Boss event\" slice shows a spawn this many minutes after its last sign of life"
												+ " (the spawn, or a kill it made).",
										1, 180, c -> c.events.bossMinutes, (c, v) -> c.events.bossMinutes = v),
								mapLines(d, "events.bossWarps", "Boss warps", "location regex -> command",
										"What the slice sends for a boss spawn. One line per rule: \"location regex -> command\","
												+ " e.g. \"(?i)cursed witch -> /warp cursedwitch\". The regex is matched against"
												+ " \"Boss · Location\"; the first match wins.",
										c -> c.events.bossWarps, (c, m) -> c.events.bossWarps = m))))),
				new Category("Daily reward", List.of(
						new Group("Daily reward (/cow)", List.of(
								toggle(d, "dailyReward.enabled", "Badge",
										"Show when each /cow reward is ready on the \"Daily reward\" slice. When off,"
												+ " the slice is a plain /cow entry.",
										c -> c.dailyReward.enabled, (c, v) -> c.dailyReward.enabled = v),
								intRange(d, "dailyReward.dailyHours", "Daily reset (hours)",
										"The daily reward counts as available again this many hours after you claim"
												+ " it, unless the /cow menu stated an exact time.",
										1, 168, c -> c.dailyReward.dailyHours, (c, v) -> c.dailyReward.dailyHours = v),
								intRange(d, "dailyReward.weeklyDays", "Weekly reset (days)",
										"The weekly reward counts as available again this many days after you claim"
												+ " it, unless the /cow menu stated an exact time.",
										1, 60, c -> c.dailyReward.weeklyDays, (c, v) -> c.dailyReward.weeklyDays = v),
								intRange(d, "dailyReward.monthlyDays", "Monthly reset (days)",
										"The monthly reward counts as available again this many days after you claim"
												+ " it, unless the /cow menu stated an exact time.",
										1, 60, c -> c.dailyReward.monthlyDays, (c, v) -> c.dailyReward.monthlyDays = v),
								text(d, "dailyReward.menuTitlePattern", "Menu title pattern",
										"Regex on a menu title: such menus are read for \"Available in 13h 2m\" lore."
												+ " Empty = only after /cow. An invalid regex falls back to the default.",
										c -> c.dailyReward.menuTitlePattern,
										(c, v) -> c.dailyReward.menuTitlePattern = v))))),
				new Category("SVA", List.of(
						new Group("SVA catalog", List.of(
								toggle(d, "svas.enabled", "Enabled",
										"Master switch: when off nothing is fetched from ManaCube's API and the"
												+ " catalog key does nothing.",
										c -> c.svas.enabled, (c, v) -> c.svas.enabled = v),
								toggle(d, "svas.tooltip", "Tooltip line",
										"Add \"✦ SVA · Circulation: N\" to the tooltip of items named like an SVA.",
										c -> c.svas.tooltip, (c, v) -> c.svas.tooltip = v))))));
	}

	/** Corner, x and y for each of the seven panels, in the panels' on-screen order. */
	private static List<Setting> positions(CubeWheelConfig d) {
		List<Setting> out = new ArrayList<>();
		position(out, d, "status", "Status", c -> c.status.position);
		position(out, d, "events", "Events", c -> c.events.position);
		position(out, d, "tracker.jobsPanel", "Jobs", c -> c.tracker.jobsPanel.position);
		position(out, d, "tracker", "Tracker", c -> c.tracker.position);
		position(out, d, "boosters", "Boosters", c -> c.boosters.position);
		position(out, d, "cooldowns", "Cooldowns", c -> c.cooldowns.position);
		position(out, d, "charms", "Charms", c -> c.charms.position);
		return out;
	}

	private static void position(List<Setting> out, CubeWheelConfig d, String path, String panel,
			Function<CubeWheelConfig, CubeWheelConfig.Position> pos) {
		String id = path + ".position.";
		out.add(choice(d, id + "corner", panel + " corner",
				"Which corner the " + panel + " panel is placed from. \"Custom (dragged)\" uses x and y as its exact"
						+ " top-left; drag panels in Arrange panels... instead of typing numbers.",
				CORNERS, c -> pos.apply(c).corner, (c, v) -> pos.apply(c).corner = v));
		out.add(intRange(d, id + "x", panel + " x",
				"Distance in GUI pixels from the corner's side edge (from the left edge for Custom; for Bottom centre"
						+ " it nudges the panel right).",
				0, OFFSET_MAX, c -> pos.apply(c).x, (c, v) -> pos.apply(c).x = v));
		out.add(intRange(d, id + "y", panel + " y",
				"Distance in GUI pixels from the corner's top or bottom edge (from the top for Custom).",
				0, OFFSET_MAX, c -> pos.apply(c).y, (c, v) -> pos.apply(c).y = v));
	}

	private static Toggle toggle(CubeWheelConfig defaults, String id, String label, String tooltip,
			Function<CubeWheelConfig, Boolean> get, BiConsumer<CubeWheelConfig, Boolean> set) {
		return new Toggle(id, label, tooltip, get, set, get.apply(defaults));
	}

	private static IntRange intRange(CubeWheelConfig defaults, String id, String label, String tooltip, int min, int max,
			Function<CubeWheelConfig, Integer> get, BiConsumer<CubeWheelConfig, Integer> set) {
		return new IntRange(id, label, tooltip, get, set, get.apply(defaults), min, max);
	}

	private static DoubleRange doubleRange(CubeWheelConfig defaults, String id, String label, String tooltip, double min,
			double max, double step, Function<CubeWheelConfig, Double> get, BiConsumer<CubeWheelConfig, Double> set) {
		return new DoubleRange(id, label, tooltip, get, set, get.apply(defaults), min, max, step);
	}

	private static Choice choice(CubeWheelConfig defaults, String id, String label, String tooltip,
			List<Choice.Entry> options, Function<CubeWheelConfig, String> get, BiConsumer<CubeWheelConfig, String> set) {
		return new Choice(id, label, tooltip, get, set, get.apply(defaults), options);
	}

	private static Text text(CubeWheelConfig defaults, String id, String label, String tooltip,
			Function<CubeWheelConfig, String> get, BiConsumer<CubeWheelConfig, String> set) {
		return new Text(id, label, tooltip, get, set, get.apply(defaults));
	}

	private static TextList textList(CubeWheelConfig defaults, String id, String label, String tooltip,
			Function<CubeWheelConfig, List<String>> get, BiConsumer<CubeWheelConfig, List<String>> set) {
		List<String> def = List.copyOf(get.apply(defaults));
		// Copies both ways: the default must not alias a config's list, and a config keeps a list it may edit.
		// A null list means "the defaults" (ConfigNormalizer fills it), so that is what the editor shows.
		return new TextList(id, label, tooltip, c -> get.apply(c) == null ? def : get.apply(c),
				(c, v) -> set.accept(c, new ArrayList<>(v)), def);
	}

	private static MapLines mapLines(CubeWheelConfig defaults, String id, String label, String format, String tooltip,
			Function<CubeWheelConfig, Map<String, String>> get, BiConsumer<CubeWheelConfig, Map<String, String>> set) {
		List<String> def = List.copyOf(Lines.mapLines(get.apply(defaults)));
		// A null map means "the defaults" (ConfigNormalizer fills it), so that is what the editor shows.
		return new MapLines(id, label, tooltip, c -> get.apply(c) == null ? def : Lines.mapLines(get.apply(c)),
				(c, lines, problems) -> set.accept(c, Lines.parseMap(lines, named(label, problems), format)), def);
	}

	private static EventLines eventLines(CubeWheelConfig defaults, String id, String label, String tooltip) {
		List<String> def = List.copyOf(Lines.eventLines(defaults.events.schedule == null
				? DefaultConfig.events() : defaults.events.schedule));
		// A null schedule means "the defaults" (ConfigNormalizer fills it), so that is what the editor shows.
		return new EventLines(id, label, tooltip,
				c -> c.events.schedule == null ? def : Lines.eventLines(c.events.schedule),
				(c, lines, problems) -> c.events.schedule = Lines.parseEvents(lines, named(label, problems)), def);
	}

	/** A problem list that prefixes each entry with the setting's label ({@code Sources: "foo" ignored: ...}). */
	private static List<String> named(String label, List<String> problems) {
		return new ArrayList<>() {
			@Override
			public boolean add(String problem) {
				return problems.add(label + ": " + problem);
			}
		};
	}
}
