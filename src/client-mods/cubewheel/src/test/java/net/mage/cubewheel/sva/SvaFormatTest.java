package net.mage.cubewheel.sva;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SvaFormatTest {
	@Test void singleMatch() throws IOException {
		SvaCatalog c = SvaCatalogTest.sample();
		SvaCatalog.Match m = c.match("VALHALLA HELMET", "carved_pumpkin", SvaCatalogTest.VALHALLA_MODEL, List::of);
		assertEquals("✦ SVA · Circulation: 107", SvaFormat.tooltipLine(m, Map.of()));
		assertEquals("✦ SVA · Circulation: 107 · owned", SvaFormat.tooltipLine(m, Map.of("valhallahelmet", 1)));
		assertEquals("✦ SVA · Circulation: 107 · owned ×2", SvaFormat.tooltipLine(m, Map.of("valhallahelmet", 2)));
	}

	@Test void ambiguousMatchListsEachCirculationAndOnlyHedgesOwnership() throws IOException {
		SvaCatalog c = SvaCatalogTest.sample();
		SvaCatalog.Match m = c.match("Deep Ocean Scythe", "diamond_sword", SvaCatalogTest.SCYTHE_MODEL, List::of);
		assertEquals("✦ SVA · Circulation: 27 / 342", SvaFormat.tooltipLine(m, Map.of()));
		assertEquals("✦ SVA · Circulation: 27 / 342 · owned (a variant)", SvaFormat.tooltipLine(m, Map.of("oceanscythe", 1)));
		assertEquals("✦ SVA · Circulation: 27 / 342 · owned (a variant)",
				SvaFormat.tooltipLine(m, Map.of("oceanscythe", 2, "enhanced-oceanscythe", 1)));
	}

	@Test void nameOnlyMatchWarns() throws IOException {
		SvaCatalog c = SvaCatalogTest.sample();
		SvaCatalog.Match m = c.match("Valhalla Helmet", "diamond_sword", "minecraft:diamond_sword", List::of);
		assertEquals(SvaFormat.NAME_ONLY, SvaFormat.tooltipLine(m, Map.of("valhallahelmet", 1)));
		assertEquals("⚠ Name matches an SVA, item doesn't", SvaFormat.NAME_ONLY);
	}

	@Test void noMatchIsNull() throws IOException {
		assertEquals(null, SvaFormat.tooltipLine(new SvaCatalog.Match(List.of()), Map.of()));
	}

	@Test void ages() {
		assertEquals("just now", SvaFormat.age(30_000));
		assertEquals("5 min ago", SvaFormat.age(5 * 60_000 + 10));
		assertEquals("3 h ago", SvaFormat.age(3 * 3_600_000L + 5));
		assertEquals("2 d ago", SvaFormat.age(49 * 3_600_000L));
	}
}
