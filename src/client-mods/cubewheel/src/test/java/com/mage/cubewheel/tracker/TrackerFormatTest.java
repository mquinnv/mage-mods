package com.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TrackerFormatTest {
	@Test void line() {
		Trackable t = new Trackable("jobs:Carrot Grind", "jobs", "Carrot Grind", 1234, 1500, 0);
		assertEquals("Carrot Grind  1,234 / 1,500 (82%) · 12m", TrackerFormat.line(t, 12 * 60_000));
	}
	@Test void ages() {
		assertEquals("now", TrackerFormat.age(59_000));
		assertEquals("3h", TrackerFormat.age(3 * 3_600_000L + 5));
		assertEquals("2d", TrackerFormat.age(2 * 86_400_000L));
	}
}
