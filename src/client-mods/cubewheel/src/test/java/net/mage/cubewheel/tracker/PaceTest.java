package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 2026-10-08: "getting a fish every few seconds with a good rod and it is saying 5 hours remaining". */
class PaceTest {
	@Test void aFishEveryFourSecondsIsFifteenAMinuteWhateverCameBefore() {
		Pace p = new Pace();
		p.credit("fish", 1, 0);               // one fish hours ago
		long t = 3 * 60 * 60_000;
		for (int i = 0; i <= 10; i++) p.credit("fish", 1, t + i * 4_000); // 11 fish over 40 s
		assertEquals(15.0, p.perMin("fish"), 1e-9); // 10 units after the first, over 40 s
	}

	@Test void needsThreeUnitsOverFifteenSeconds() {
		Pace p = new Pace();
		p.credit("k", 1, 0);
		p.credit("k", 1, 5_000);
		p.credit("k", 1, 10_000);
		assertEquals(0, p.perMin("k")); // 2 units after the first
		p.credit("k", 1, 12_000);
		assertEquals(0, p.perMin("k")); // 3 units, but only 12 s
		p.credit("k", 1, 20_000);
		assertEquals(4 / (20 / 60.0), p.perMin("k"), 1e-9);
		assertEquals(0, p.perMin("other"));
	}

	@Test void aPauseDoesNotSlowThePaceAndOldHitsDropOut() {
		Pace p = new Pace();
		for (int i = 0; i <= 6; i++) p.credit("k", 1, i * 10_000); // 6 units over 60 s: 6/min
		assertEquals(6.0, p.perMin("k"), 1e-9);
		// A long pause, then more at a different rate: the old hits are gone, only the new run counts.
		long t = 60_000 + 20 * 60_000;
		for (int i = 0; i <= 4; i++) p.credit("k", 2, t + i * 5_000); // 8 units over 20 s: 24/min
		assertEquals(24.0, p.perMin("k"), 1e-9);
	}

	@Test void reversalsAndClearAreIgnoredOrForget() {
		Pace p = new Pace();
		for (int i = 0; i <= 4; i++) p.credit("k", 1, i * 5_000);
		p.credit("k", -3, 30_000);
		assertEquals(4 / (20 / 60.0), p.perMin("k"), 1e-9);
		p.clear();
		assertEquals(0, p.perMin("k"));
	}
}
