package net.mage.cubewheel.settings;

import net.mage.cubewheel.config.ConfigNormalizer;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.config.DefaultConfig;
import net.mage.cubewheel.settings.SettingsSpec.Category;
import net.mage.cubewheel.settings.SettingsSpec.IntRange;
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
		assertEquals(List.of("General", "Daily reward", "SVA"), cats.stream().map(Category::name).toList());
		assertEquals(List.of("enabled", "serverHosts", "vaultCount", "listThreshold"), ids(cats.get(0)));
		assertEquals(List.of("dailyReward.enabled", "dailyReward.dailyHours", "dailyReward.weeklyDays",
				"dailyReward.monthlyDays", "dailyReward.menuTitlePattern"), ids(cats.get(1)));
		assertEquals(List.of("svas.enabled", "svas.tooltip"), ids(cats.get(2)));
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
				case Text t -> { assertEquals(t.defaultValue(), t.getter().apply(fresh), s.id()); yield t.defaultValue(); }
				case TextList l -> { assertEquals(l.defaultValue(), l.getter().apply(fresh), s.id()); yield l.defaultValue(); }
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
}
