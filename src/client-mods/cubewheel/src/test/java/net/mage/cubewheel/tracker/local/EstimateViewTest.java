package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.tracker.Trackable;
import net.mage.cubewheel.tracker.TrackerRow;
import org.junit.jupiter.api.Test;

class EstimateViewTest {
	static final CounterRule SLAY = new CounterRule(CounterRule.Kind.KILL, 1000, new CounterRule.Group("monster"), new CounterRule.Special());
	static final CounterRule HARVEST = new CounterRule(CounterRule.Kind.BREAK, 10000, new CounterRule.Any(), new CounterRule.NamedWorld("wolfhaven"));

	static Estimate est(long count, double baseline) {
		return new Estimate(count, baseline, 0, 0);
	}

	@Test void ratioEntryAddsTheCount() {
		Trackable t = new Trackable("prestige:Slay", "prestige", "Slay", 377, 1000, 0);
		TrackerRow row = EstimateView.row(t, est(5, 377), SLAY);
		assertTrue(row.estimated());
		assertEquals(382, row.shownCurrent(), 1e-9);
		assertEquals(1000, row.shownMax(), 1e-9);
		assertEquals(0.382, row.fraction(), 1e-9);
	}

	@Test void percentEntryIsShownInObjectiveUnits() {
		Trackable t = new Trackable("pquests:Haven Harvester", "pquests", "Haven Harvester", 64, 100, 0);
		TrackerRow row = EstimateView.row(t, est(37, 64), HARVEST);
		assertEquals(6437, row.shownCurrent(), 1e-9);
		assertEquals(10000, row.shownMax(), 1e-9);
	}

	@Test void percentEntryIsAlwaysShownInObjectiveUnits() {
		Trackable t = new Trackable("pquests:Haven Harvester", "pquests", "Haven Harvester", 67, 100, 0);
		TrackerRow plain = EstimateView.row(t, null, HARVEST);
		assertFalse(plain.estimated());
		assertEquals(6700, plain.shownCurrent(), 1e-9);
		assertEquals(10000, plain.shownMax(), 1e-9);
		assertEquals(0.67, plain.fraction(), 1e-9);
		assertEquals("Haven Harvester  6,700 / 10,000 (67%) · now", net.mage.cubewheel.tracker.TrackerFormat.line(plain, 0));
		TrackerRow counted = EstimateView.row(t, est(112, 67), HARVEST);
		assertEquals("Haven Harvester  ~6,812 / 10,000 (68%) · now", net.mage.cubewheel.tracker.TrackerFormat.line(counted, 0));
		Trackable done = new Trackable("pquests:Haven Harvester", "pquests", "Haven Harvester", 100, 100, 0);
		assertEquals("Haven Harvester  10,000 / 10,000 (100%) · now", net.mage.cubewheel.tracker.TrackerFormat.line(EstimateView.row(done, null, HARVEST), 0));
	}

	@Test void percentEntryWithoutAKnownTotalKeepsThePercent() {
		Trackable t = new Trackable("pquests:Discoverer", "pquests", "Discoverer", 70, 100, 0);
		assertEquals("Discoverer  70 / 100 (70%) · now", net.mage.cubewheel.tracker.TrackerFormat.line(EstimateView.row(t, null, null), 0));
		// A ratio entry is left as it is.
		Trackable slay = new Trackable("prestige:Slay", "prestige", "Slay", 377, 1000, 0);
		assertEquals(377, EstimateView.row(slay, null, SLAY).shownCurrent(), 1e-9);
	}

	@Test void cappedAtMaxAndNeverComplete() {
		Trackable t = new Trackable("prestige:Slay", "prestige", "Slay", 990, 1000, 0);
		TrackerRow row = EstimateView.row(t, est(50, 990), SLAY);
		assertEquals(1000, row.shownCurrent(), 1e-9);
		assertTrue(row.atCap());
		assertFalse(row.complete());
		assertEquals(0.99, row.fraction(), 1e-9);
	}

	@Test void noEstimateIsTheAuthoritativeValue() {
		Trackable t = new Trackable("prestige:Slay", "prestige", "Slay", 1000, 1000, 0);
		TrackerRow row = EstimateView.row(t, null, SLAY);
		assertFalse(row.estimated());
		assertTrue(row.complete());
		assertEquals(1.0, row.fraction(), 1e-9);
	}

	@Test void unitsPerCount() {
		assertEquals(0.01, EstimateView.unitsPerCount(new Trackable("a", "a", "a", 64, 100, 0), HARVEST), 1e-12);
		assertEquals(1, EstimateView.unitsPerCount(new Trackable("a", "a", "a", 5, 1000, 0), SLAY), 1e-12);
		assertEquals(1, EstimateView.unitsPerCount(new Trackable("a", "a", "a", 5, 1000, 0), null), 1e-12);
	}
}
