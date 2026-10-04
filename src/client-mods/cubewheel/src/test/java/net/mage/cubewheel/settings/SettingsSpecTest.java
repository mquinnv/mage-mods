package net.mage.cubewheel.settings;

import net.mage.cubewheel.config.ConfigNormalizer;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.config.DefaultConfig;
import net.mage.cubewheel.hud.HudLayout;
import net.mage.cubewheel.settings.SettingsSpec.Category;
import net.mage.cubewheel.settings.SettingsSpec.Choice;
import net.mage.cubewheel.settings.SettingsSpec.DoubleRange;
import net.mage.cubewheel.settings.SettingsSpec.Group;
import net.mage.cubewheel.settings.SettingsSpec.IntRange;
import net.mage.cubewheel.settings.SettingsSpec.MapLines;
import net.mage.cubewheel.settings.SettingsSpec.Setting;
import net.mage.cubewheel.settings.SettingsSpec.Text;
import net.mage.cubewheel.settings.SettingsSpec.TextList;
import net.mage.cubewheel.settings.SettingsSpec.Toggle;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SettingsSpecTest {
	private static List<Setting> all() {
		List<Setting> out = new ArrayList<>();
		for (Category c : SettingsSpec.categories()) c.groups().forEach(g -> out.addAll(g.settings()));
		return out;
	}

	private static List<String> ids(Category c) {
		List<String> out = new ArrayList<>();
		c.groups().forEach(g -> g.settings().forEach(s -> out.add(s.id())));
		return out;
	}

	@Test
	void categoriesInOrderWithTheirSettings() {
		List<Category> cats = SettingsSpec.categories();
		assertEquals(List.of("General", "HUD panels", "Tracker", "Daily reward", "SVA"),
				cats.stream().map(Category::name).toList());
		assertEquals(List.of("enabled", "serverHosts", "vaultCount", "listThreshold"), ids(cats.get(0)));
		assertEquals(List.of("dailyReward.enabled", "dailyReward.dailyHours", "dailyReward.weeklyDays",
				"dailyReward.monthlyDays", "dailyReward.menuTitlePattern"), ids(cats.get(3)));
		assertEquals(List.of("svas.enabled", "svas.tooltip"), ids(cats.get(4)));
	}

	@Test
	void hudPanelsCategoryHasOneGroupPerPanelThenCollapsedExactPositions() {
		Category hud = SettingsSpec.categories().get(1);
		assertEquals(List.of("Status", "Events", "Jobs", "Tracker", "Boosters", "Cooldowns", "Charms", "Exact positions"),
				hud.groups().stream().map(Group::name).toList());
		assertEquals(List.of("status.enabled", "status.armor"), ids(hud.groups().get(0)));
		assertEquals(List.of("events.hudVisible"), ids(hud.groups().get(1)));
		assertEquals(List.of("tracker.jobsPanel.enabled"), ids(hud.groups().get(2)));
		assertEquals(List.of("tracker.hudVisible", "tracker.hudMaxLines", "tracker.nearThreshold", "tracker.worldFilter"),
				ids(hud.groups().get(3)));
		assertEquals(List.of("boosters.enabled"), ids(hud.groups().get(4)));
		assertEquals(List.of("cooldowns.enabled", "cooldowns.showUses", "cooldowns.mcmmo"), ids(hud.groups().get(5)));
		assertEquals(List.of("charms.enabled"), ids(hud.groups().get(6)));
		for (int i = 0; i < 7; i++) assertFalse(hud.groups().get(i).collapsed(), hud.groups().get(i).name());
		Group exact = hud.groups().get(7);
		assertTrue(exact.collapsed());
		List<String> expected = new ArrayList<>();
		for (String p : List.of("status", "events", "tracker.jobsPanel", "tracker", "boosters", "cooldowns", "charms")) {
			expected.addAll(List.of(p + ".position.corner", p + ".position.x", p + ".position.y"));
		}
		assertEquals(expected, ids(exact));
	}

	private static List<String> ids(Group g) {
		return g.settings().stream().map(Setting::id).toList();
	}

	@Test
	void choicesDefaultToAnAllowedValueAndRoundTripEveryValue() {
		List<Choice> choices = all().stream().filter(s -> s instanceof Choice).map(s -> (Choice) s).toList();
		assertEquals(8, choices.size()); // worldFilter + seven corners
		for (Choice ch : choices) {
			List<String> values = ch.options().stream().map(Choice.Entry::value).toList();
			assertTrue(values.contains(ch.defaultValue()), ch.id());
			assertEquals(values.size(), new HashSet<>(values).size(), ch.id());
			ch.options().forEach(e -> assertFalse(e.label().isBlank(), ch.id()));
			for (String v : values) {
				CubeWheelConfig c = DefaultConfig.create();
				ch.setter().accept(c, v);
				assertEquals(v, ch.getter().apply(c), ch.id());
			}
		}
	}

	@Test
	void cornerChoicesOfferExactlyTheCornersHudLayoutParses() {
		Choice corner = (Choice) all().stream().filter(s -> s.id().equals("status.position.corner")).findFirst().orElseThrow();
		assertEquals(List.of("top_left", "top_right", "bottom_left", "bottom_right", "bottom_center", "custom"),
				corner.options().stream().map(Choice.Entry::value).toList());
		assertEquals(List.of("Top left", "Top right", "Bottom left", "Bottom right", "Bottom centre", "Custom (dragged)"),
				corner.options().stream().map(Choice.Entry::label).toList());
		for (Choice.Entry e : corner.options()) {
			HudLayout.Corner parsed = HudLayout.Corner.parse(e.value());
			assertEquals(e.value(), parsed.id());
			if (!e.value().equals("top_left")) assertNotEquals(HudLayout.Corner.TOP_LEFT, parsed, e.value());
		}
	}

	@Test
	void worldFilterChoiceValuesSurviveTheNormalizer() {
		Choice wf = (Choice) all().stream().filter(s -> s.id().equals("tracker.worldFilter")).findFirst().orElseThrow();
		assertEquals(List.of("sort", "hide", "off"), wf.options().stream().map(Choice.Entry::value).toList());
		for (Choice.Entry e : wf.options()) {
			CubeWheelConfig c = DefaultConfig.create();
			wf.setter().accept(c, e.value());
			ConfigNormalizer.normalize(c, new ArrayList<>());
			assertEquals(e.value(), wf.getter().apply(c));
		}
	}

	@Test
	void doubleRangesHoldTheirDefaultAndMatchTheNormalizerClamps() {
		List<DoubleRange> ranges = all().stream().filter(s -> s instanceof DoubleRange).map(s -> (DoubleRange) s).toList();
		assertEquals(1, ranges.size());
		for (DoubleRange r : ranges) {
			assertTrue(r.min() <= r.defaultValue() && r.defaultValue() <= r.max(), r.id());
			assertTrue(r.step() > 0, r.id());
			CubeWheelConfig low = DefaultConfig.create();
			r.setter().accept(low, r.min() - 1000);
			ConfigNormalizer.normalize(low, new ArrayList<>());
			assertEquals(r.min(), r.getter().apply(low), r.id() + " min");
			CubeWheelConfig high = DefaultConfig.create();
			r.setter().accept(high, r.max() + 1000);
			ConfigNormalizer.normalize(high, new ArrayList<>());
			assertEquals(r.max(), r.getter().apply(high), r.id() + " max");
		}
	}

	@Test
	void everySettingHasALabelAndATooltip() {
		for (Setting s : all()) {
			assertFalse(s.label().isBlank(), s.id());
			assertFalse(s.tooltip().isBlank(), s.id());
		}
	}

	@Test
	void setterThenGetterRoundTripsAValueOtherThanTheDefault() {
		for (Setting s : all()) {
			CubeWheelConfig c = new CubeWheelConfig();
			switch (s) {
				case Toggle t -> {
					t.setter().accept(c, !t.defaultValue());
					assertEquals(!t.defaultValue(), t.getter().apply(c), s.id());
				}
				case IntRange r -> {
					int v = r.defaultValue() < r.max() ? r.defaultValue() + 1 : r.defaultValue() - 1;
					r.setter().accept(c, v);
					assertEquals(v, r.getter().apply(c), s.id());
				}
				case DoubleRange r -> {
					double v = r.defaultValue() < r.max() ? r.defaultValue() + r.step() : r.defaultValue() - r.step();
					r.setter().accept(c, v);
					assertEquals(v, r.getter().apply(c), s.id());
				}
				case Choice ch -> {
					String v = ch.options().stream().map(Choice.Entry::value).filter(x -> !x.equals(ch.defaultValue()))
							.findFirst().orElseThrow();
					ch.setter().accept(c, v);
					assertEquals(v, ch.getter().apply(c), s.id());
				}
				case Text t -> {
					String v = t.defaultValue() + "x";
					t.setter().accept(c, v);
					assertEquals(v, t.getter().apply(c), s.id());
				}
				case TextList l -> {
					List<String> v = List.of("example.org", "two");
					assertNotEquals(v, l.defaultValue(), s.id());
					l.setter().accept(c, v);
					assertEquals(v, l.getter().apply(c), s.id());
				}
				case MapLines m -> {
					List<String> v = List.of("zeta -> (?i)z", "alpha -> a+", "mid -> m");
					assertNotEquals(v, m.defaultValue(), s.id());
					List<String> problems = new ArrayList<>();
					m.setter().set(c, v, problems);
					assertEquals(v, m.getter().apply(c), s.id() + " keeps order");
					assertTrue(problems.isEmpty(), s.id());
				}
			}
		}
	}

	@Test
	void defaultsMatchAFreshInstall() {
		CubeWheelConfig fresh = DefaultConfig.create();
		for (Setting s : all()) {
			Object def = switch (s) {
				case Toggle t -> { assertEquals(t.defaultValue(), t.getter().apply(fresh), s.id()); yield t.defaultValue(); }
				case IntRange r -> { assertEquals(r.defaultValue(), r.getter().apply(fresh), s.id()); yield r.defaultValue(); }
				case DoubleRange r -> { assertEquals(r.defaultValue(), r.getter().apply(fresh), s.id()); yield r.defaultValue(); }
				case Choice ch -> { assertEquals(ch.defaultValue(), ch.getter().apply(fresh), s.id()); yield ch.defaultValue(); }
				case Text t -> { assertEquals(t.defaultValue(), t.getter().apply(fresh), s.id()); yield t.defaultValue(); }
				case TextList l -> { assertEquals(l.defaultValue(), l.getter().apply(fresh), s.id()); yield l.defaultValue(); }
				case MapLines m -> { assertEquals(m.defaultValue(), m.getter().apply(fresh), s.id()); yield m.defaultValue(); }
			};
			assertNotNull(def, s.id());
		}
	}

	@Test
	void intRangesHoldTheirDefaultAndMatchTheNormalizerClamps() {
		List<IntRange> ranges = all().stream().filter(s -> s instanceof IntRange).map(s -> (IntRange) s).toList();
		assertFalse(ranges.isEmpty());
		for (IntRange r : ranges) {
			assertTrue(r.min() <= r.defaultValue() && r.defaultValue() <= r.max(), r.id());

			CubeWheelConfig low = DefaultConfig.create();
			r.setter().accept(low, r.min() - 1000);
			ConfigNormalizer.normalize(low, new ArrayList<>());
			assertEquals(r.min(), r.getter().apply(low), r.id() + " min");

			CubeWheelConfig high = DefaultConfig.create();
			r.setter().accept(high, r.max() + 1000);
			ConfigNormalizer.normalize(high, new ArrayList<>());
			assertEquals(r.max(), r.getter().apply(high), r.id() + " max");
		}
	}

	@Test
	void textListSetterKeepsAMutableCopy() {
		TextList hosts = (TextList) all().stream().filter(s -> s.id().equals("serverHosts")).findFirst().orElseThrow();
		CubeWheelConfig c = new CubeWheelConfig();
		hosts.setter().accept(c, List.of("a.example"));
		c.serverHosts.add("b.example"); // the config's own list stays editable, as Gson-loaded ones are
		assertEquals(List.of("a.example", "b.example"), hosts.getter().apply(c));
	}

	@Test
	void idsAreUnique() {
		Set<String> seen = new HashSet<>();
		for (Setting s : all()) assertTrue(seen.add(s.id()), "duplicate id " + s.id());
	}

	private static Setting byId(String id) {
		return all().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
	}

	/** A config with every defaultable map and list left null, as an old or hand-edited file may have it. */
	private static CubeWheelConfig nulled() {
		CubeWheelConfig c = new CubeWheelConfig();
		c.serverHosts = null;
		c.tracker.sources = null;
		c.tracker.sidebarLinks = null;
		c.tracker.refreshCommands = null;
		c.tracker.local.worlds = null;
		c.tracker.local.specialWorlds = null;
		return c;
	}

	private static List<String> shown(Setting s, CubeWheelConfig c) {
		return s instanceof MapLines m ? m.getter().apply(c) : ((TextList) s).getter().apply(c);
	}

	@Test
	void trackerCategoryFollowsHudPanelsWithItsTwoGroups() {
		Category t = SettingsSpec.categories().get(2);
		assertEquals("Tracker", t.name());
		assertEquals(List.of("Sources & refresh", "Local counting"), t.groups().stream().map(Group::name).toList());
		assertEquals(List.of("tracker.sources", "tracker.refreshCommands", "tracker.sidebarLinks",
				"tracker.survivalSidebarPattern"), ids(t.groups().get(0)));
		assertEquals(List.of("tracker.local.enabled", "tracker.local.blocks", "tracker.local.kills", "tracker.local.fish",
				"tracker.local.shear", "tracker.local.milk", "tracker.local.areaBreaks", "tracker.local.worlds",
				"tracker.local.specialWorlds"), ids(t.groups().get(1)));
		assertTrue(byId("tracker.survivalSidebarPattern").tooltip().contains("Empty"));
		assertTrue(byId("tracker.refreshCommands").tooltip().contains("8"));
	}

	@Test
	void nullMapsAndListsShowTheDefaultsAndSavingThemUnchangedKeepsBehaviour() {
		for (String id : List.of("tracker.sources", "tracker.sidebarLinks", "tracker.refreshCommands",
				"tracker.local.worlds", "tracker.local.specialWorlds", "serverHosts")) {
			Setting s = byId(id);
			List<String> shown = shown(s, nulled());
			assertEquals(s instanceof MapLines m ? m.defaultValue() : ((TextList) s).defaultValue(), shown, id);

			// Writing the shown value back and normalising gives what normalising the null would have.
			CubeWheelConfig saved = nulled();
			if (s instanceof MapLines m) m.setter().set(saved, shown, new ArrayList<>());
			else ((TextList) s).setter().accept(saved, shown);
			ConfigNormalizer.normalize(saved, new ArrayList<>());
			CubeWheelConfig plain = nulled();
			ConfigNormalizer.normalize(plain, new ArrayList<>());
			assertEquals(shown(s, plain), shown(s, saved), id);
		}
	}

	@Test
	void defaultMapsRoundTripThroughLinesUnchanged() {
		CubeWheelConfig c = DefaultConfig.create();
		for (String id : List.of("tracker.sources", "tracker.sidebarLinks")) {
			MapLines m = (MapLines) byId(id);
			List<String> problems = new ArrayList<>();
			m.setter().set(c, m.getter().apply(c), problems);
			assertEquals(m.defaultValue(), m.getter().apply(c), id);
			assertTrue(problems.isEmpty(), id);
		}
		assertEquals(DefaultConfig.trackerSources(), c.tracker.sources);
		assertEquals(DefaultConfig.sidebarLinks(), c.tracker.sidebarLinks);
	}

	@Test
	void aBadMapLineSurfacesAsAProblemAndIsNotStored() {
		MapLines m = (MapLines) byId("tracker.sources");
		CubeWheelConfig c = DefaultConfig.create();
		List<String> problems = new ArrayList<>();
		m.setter().set(c, List.of("jobs -> (?i)jobs", "no arrow here", "", "empty ->  "), problems);
		assertEquals(List.of("jobs -> (?i)jobs"), m.getter().apply(c));
		assertEquals(2, problems.size());
		assertTrue(problems.get(0).contains("no arrow here"), problems.toString());
	}

	@Test
	void refreshCommandsAreCappedByTheNormalizerAsTheTooltipSays() {
		TextList l = (TextList) byId("tracker.refreshCommands");
		CubeWheelConfig c = DefaultConfig.create();
		List<String> many = new ArrayList<>();
		for (int i = 0; i < 12; i++) many.add("cmd" + i);
		l.setter().accept(c, many);
		ConfigNormalizer.normalize(c, new ArrayList<>());
		assertEquals(8, l.getter().apply(c).size());
		assertEquals("/cmd0", l.getter().apply(c).get(0));
	}
}
