package net.mage.cubewheel.wheel;

import static org.junit.jupiter.api.Assertions.*;

import net.mage.cubewheel.config.WheelNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SliceViewsTest {
	@AfterEach void reset() {
		SliceViews.clear();
	}

	@Test void flyLabelAndColour() {
		WheelNode fly = WheelNode.leaf("Fly", null, " /FLY ");
		assertEquals("Fly: on", SliceViews.fly(fly, true).label());
		assertEquals(SliceViews.GREEN, SliceViews.fly(fly, true).colour());
		assertEquals("Fly: off", SliceViews.fly(fly, false).label());
		assertEquals(SliceViews.RED, SliceViews.fly(fly, false).colour());
		assertNull(SliceViews.fly(WheelNode.leaf("Fly", null, "/fly speed"), true));
	}

	@Test void firstProviderWinsAndAThrowingOneIsSkipped() {
		SliceViews.register((n, now) -> { throw new IllegalStateException("boom"); });
		SliceViews.register((n, now) -> SliceViews.fly(n, true));
		SliceViews.register((n, now) -> new SliceViews.View("other", null, null, false));
		assertEquals("Fly: on", SliceViews.view(WheelNode.leaf("Fly", null, "/fly"), 0).label());
		assertEquals("other", SliceViews.view(WheelNode.leaf("Spawn", null, "/spawn"), 0).label());
		assertNull(SliceViews.view(null, 0));
	}

	@Test void commandsAndOpening() {
		WheelNode leaf = WheelNode.leaf("Spawn", null, "/spawn");
		WheelNode ring = WheelNode.ring("R", null, leaf);
		WheelNode homes = WheelNode.dynamic("Homes", null, "homes");
		WheelNode boss = WheelNode.slice("Boss event", null, "boss");
		WheelNode loading = WheelNode.leaf("Loading…", null, null);
		assertEquals("/spawn", SliceViews.command(leaf, null));
		assertNull(SliceViews.command(leaf, new SliceViews.View("x", null, null, true))); // inert wins
		assertEquals("/warp boss", SliceViews.command(boss, new SliceViews.View("B", null, "/warp boss", false)));
		assertNull(SliceViews.command(boss, null));
		assertTrue(SliceViews.opens(ring));
		assertTrue(SliceViews.opens(homes));
		assertFalse(SliceViews.opens(boss));
		assertFalse(SliceViews.opens(leaf));
		assertFalse(SliceViews.opens(loading));
		assertEquals("Spawn", SliceViews.label(leaf, null));
	}
}
