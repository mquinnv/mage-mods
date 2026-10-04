package net.mage.cubewheel.settings;

import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.config.DefaultConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * What the settings screen shows, as plain data: categories of groups of settings, each bound to one
 * {@link CubeWheelConfig} field. Pure (no Minecraft, Fabric or YACL imports) so it is unit-tested; SettingsScreens
 * turns it into YACL options without any per-setting code. Ranges are the ones ConfigNormalizer clamps to.
 */
public final class SettingsSpec {
	private SettingsSpec() {}

	/** One editable config field. {@code id} is its JSON path ("dailyReward.dailyHours"), stable across versions. */
	public sealed interface Setting permits Toggle, IntRange, Text, TextList {
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

	/** A string field: a text box. */
	public record Text(String id, String label, String tooltip, Function<CubeWheelConfig, String> getter,
			BiConsumer<CubeWheelConfig, String> setter, String defaultValue) implements Setting {}

	/** A list-of-strings field: an editable list, one entry per row. The setter stores a mutable copy. */
	public record TextList(String id, String label, String tooltip, Function<CubeWheelConfig, List<String>> getter,
			BiConsumer<CubeWheelConfig, List<String>> setter, List<String> defaultValue) implements Setting {}

	/** A titled group of settings within a category. */
	public record Group(String name, List<Setting> settings) {}

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

	private static Toggle toggle(CubeWheelConfig defaults, String id, String label, String tooltip,
			Function<CubeWheelConfig, Boolean> get, BiConsumer<CubeWheelConfig, Boolean> set) {
		return new Toggle(id, label, tooltip, get, set, get.apply(defaults));
	}

	private static IntRange intRange(CubeWheelConfig defaults, String id, String label, String tooltip, int min, int max,
			Function<CubeWheelConfig, Integer> get, BiConsumer<CubeWheelConfig, Integer> set) {
		return new IntRange(id, label, tooltip, get, set, get.apply(defaults), min, max);
	}

	private static Text text(CubeWheelConfig defaults, String id, String label, String tooltip,
			Function<CubeWheelConfig, String> get, BiConsumer<CubeWheelConfig, String> set) {
		return new Text(id, label, tooltip, get, set, get.apply(defaults));
	}

	private static TextList textList(CubeWheelConfig defaults, String id, String label, String tooltip,
			Function<CubeWheelConfig, List<String>> get, BiConsumer<CubeWheelConfig, List<String>> set) {
		// Copies both ways: the default must not alias a config's list, and a config keeps a list it may edit.
		return new TextList(id, label, tooltip, get, (c, v) -> set.accept(c, new ArrayList<>(v)),
				List.copyOf(get.apply(defaults)));
	}
}
