package com.mage.cubewheel.sva;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SvaFormatTest {
	@Test void singleMatch() throws IOException {
		SvaCatalog c = SvaCatalogTest.sample();
		SvaCatalog.Match m = c.match("VALHALLA HELMET", null, null, List::of);
		assertEquals("✦ SVA · Circulation: 107", SvaFormat.tooltipLine(m, Map.of()));
		assertEquals("✦ SVA · Circulation: 107 · owned", SvaFormat.tooltipLine(m, Map.of("valhallahelmet", 1)));
		assertEquals("✦ SVA · Circulation: 107 · owned ×2", SvaFormat.tooltipLine(m, Map.of("valhallahelmet", 2)));
	}

	@Test void ambiguousMatchListsEachCirculation() throws IOException {
		SvaCatalog c = SvaCatalogTest.sample();
		SvaCatalog.Match m = c.match("Deep Ocean Scythe", null, null, List::of);
		assertEquals("✦ SVA · Circulation: 27 / 342", SvaFormat.tooltipLine(m, Map.of()));
		assertEquals("✦ SVA · Circulation: 27 / 342 · owned", SvaFormat.tooltipLine(m, Map.of("oceanscythe", 1)));
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
