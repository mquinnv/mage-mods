package com.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mage.cubewheel.tracker.Trackable;
import com.mage.cubewheel.tracker.TrackerRow;
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
