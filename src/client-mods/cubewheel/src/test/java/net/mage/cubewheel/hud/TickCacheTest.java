package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** HUD sources built once per client tick (PanelsHud.perTick), and placed at their live position every frame. */
class TickCacheTest {
	static Panel panel(String title) {
		return new Panel(title, List.of(new Panel.Line(title, Panel.WHITE)), HudLayout.Corner.TOP_LEFT, 4, 4);
	}

	@Test void buildsOncePerTick() {
		TickCache<Optional<Panel>> cache = new TickCache<>();
		AtomicInteger builds = new AtomicInteger();
		Optional<Panel> first = cache.get(7, () -> Optional.of(panel("Jobs " + builds.incrementAndGet())));
		// Every frame of the same tick gets the same panel, without building it again.
		for (int frame = 0; frame < 6; frame++) assertSame(first, cache.get(7, () -> Optional.of(panel("Jobs " + builds.incrementAndGet()))));
		assertEquals(1, builds.get());
		assertEquals("Jobs 1", first.get().title());
	}

	@Test void rebuildsWhenTheTickMoves() {
		TickCache<String> cache = new TickCache<>();
		AtomicInteger builds = new AtomicInteger();
		assertEquals("1", cache.get(1, () -> String.valueOf(builds.incrementAndGet())));
		assertEquals("2", cache.get(2, () -> String.valueOf(builds.incrementAndGet())));
		assertEquals("2", cache.get(2, () -> String.valueOf(builds.incrementAndGet())));
		assertEquals("3", cache.get(3, () -> String.valueOf(builds.incrementAndGet())));
		cache.clear();
		assertEquals("4", cache.get(3, () -> String.valueOf(builds.incrementAndGet())));
	}

	@Test void emptyIsCachedToo() {
		TickCache<Optional<Panel>> cache = new TickCache<>();
		AtomicInteger builds = new AtomicInteger();
		cache.get(0, () -> { builds.incrementAndGet(); return Optional.empty(); });
		assertEquals(Optional.empty(), cache.get(0, () -> { builds.incrementAndGet(); return Optional.of(panel("x")); }));
		assertEquals(1, builds.get());
	}

	@Test void aFailedBuildIsTriedAgain() {
		TickCache<String> cache = new TickCache<>();
		assertThrows(IllegalStateException.class, () -> cache.get(5, () -> { throw new IllegalStateException("boom"); }));
		assertEquals("ok", cache.get(5, () -> "ok"));
	}

	@Test void panelAtKeepsItselfWhenAlreadyThere() {
		Panel p = panel("Tracker");
		assertSame(p, p.at(HudLayout.Corner.TOP_LEFT, 4, 4));
		Panel moved = p.at(HudLayout.Corner.CUSTOM, 120, 40);
		assertEquals(new Panel("Tracker", p.lines(), HudLayout.Corner.CUSTOM, 120, 40, null), moved);
	}

	@Test void cornerParseIsStableWhenCached() {
		for (int i = 0; i < 3; i++) {
			assertEquals(HudLayout.Corner.TOP_RIGHT, HudLayout.Corner.parse("Top-Right"));
			assertEquals(HudLayout.Corner.BOTTOM_CENTER, HudLayout.Corner.parse("bottom center"));
			assertEquals(HudLayout.Corner.TOP_LEFT, HudLayout.Corner.parse("nowhere"));
			assertEquals(HudLayout.Corner.TOP_LEFT, HudLayout.Corner.parse(null));
		}
	}
}
