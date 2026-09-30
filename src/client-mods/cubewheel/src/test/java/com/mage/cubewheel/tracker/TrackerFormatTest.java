package com.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TrackerFormatTest {
	@Test void line() {
		Trackable t = new Trackable("jobs:Carrot Grind", "jobs", "Carrot Grind", 1234, 1500, 0);
		assertEquals("Carrot Grind  1,234 / 1,500 (82%) \u00b7 12m", TrackerFormat.line(t, 12 * 60_000));
	}
	@Test void incompleteNeverShows100() {
		Trackable t = new Trackable("j:A", "j", "A", 1499, 1500, 0);
		assertEquals("A  1,499 / 1,500 (99%) \u00b7 now", TrackerFormat.line(t, 0));
		Trackable done = new Trackable("j:A", "j", "A", 1500, 1500, 0);
		assertEquals("A  1,500 / 1,500 (100%) \u00b7 now", TrackerFormat.line(done, 0));
	}
	@Test void ages() {
		assertEquals("now", TrackerFormat.age(59_000));
		assertEquals("3h", TrackerFormat.age(3 * 3_600_000L + 5));
		assertEquals("2d", TrackerFormat.age(2 * 86_400_000L));
	}
}
