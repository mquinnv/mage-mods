package net.mage.cubewheel.tracker;

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
	@Test void estimatedRowsCarryATilde() {
		Trackable t = new Trackable("pquests:Haven Harvester", "pquests", "Haven Harvester", 64, 100, 0);
		TrackerRow est = new TrackerRow(t, 6437, 10000, true, 37);
		assertEquals("Haven Harvester  ~6,437 / 10,000 (64%) · 5m", TrackerFormat.line(est, 5 * 60_000));
		assertEquals("Haven Harvester  ~6,437 / 10,000 (64%) · 5m  +37~", TrackerFormat.pickerLine(est, 5 * 60_000));
	}
	@Test void estimateAtTheCapIsUnconfirmed() {
		Trackable t = new Trackable("pquests:Miner", "pquests", "Miner", 290, 300, 0);
		TrackerRow capped = new TrackerRow(t, 300, 300, true, 12);
		assertEquals("Miner  ~300 / 300 (99%) ✓? · now", TrackerFormat.line(capped, 0));
	}
	@Test void plainRowsHaveNoTilde() {
		Trackable t = new Trackable("j:A", "j", "A", 5, 10, 0);
		assertEquals("A  5 / 10 (50%) · now", TrackerFormat.line(new TrackerRow(t, 5, 10, false, 0), 0));
		assertEquals("A  5 / 10 (50%) · now", TrackerFormat.pickerLine(new TrackerRow(t, 5, 10, false, 0), 0));
	}
	@Test void estimateTooltip() {
		Trackable t = new Trackable("j:A", "j", "A", 5, 10, 0);
		TrackerRow row = new TrackerRow(t, 6, 10, true, 1);
		assertEquals(java.util.List.of("Estimated from what you did since this menu was last read (5m ago)."),
				TrackerFormat.estimateTooltip(row, null, 5 * 60_000));
		assertEquals(java.util.List.of("Estimated from what you did since this menu was last read (now).",
				"Last check: counted 212, actual 220."),
				TrackerFormat.estimateTooltip(row, new net.mage.cubewheel.tracker.local.Accuracy(212, 220), 0));
	}
	@Test void ages() {
		assertEquals("now", TrackerFormat.age(59_000));
		assertEquals("3h", TrackerFormat.age(3 * 3_600_000L + 5));
		assertEquals("2d", TrackerFormat.age(2 * 86_400_000L));
	}
}
