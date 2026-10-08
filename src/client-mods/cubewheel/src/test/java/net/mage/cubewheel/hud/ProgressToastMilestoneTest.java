package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.config.CubeWheelConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pies, quarter milestones and the end countdown on the progress popup (Michael 2026-10-08). */
class ProgressToastMilestoneTest {
	private static CubeWheelConfig.Toast cfg() {
		return new CubeWheelConfig.Toast(); // enabled, 1.5 s
	}

	private static ProgressToast.Shown one(ProgressToast t, long now) {
		List<ProgressToast.Shown> lines = t.lines(now, cfg());
		assertEquals(1, lines.size(), "lines: " + lines);
		return lines.get(0);
	}

	@Test void everyProgressLineCarriesItsFraction() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~10/74", false, 10, 74, 0, cfg());
		ProgressToast.Shown s = one(t, 0);
		assertEquals("+1 Mana Wolves  ~10/74", s.text());
		assertEquals(Panel.CYAN, s.color());
		assertEquals(10 / 74.0, s.fraction(), 1e-9);
		t.onIncrement("q:none", "Skill Level", 1, "", false, 1_000, cfg()); // no numbers: no pie
		ProgressToast.Shown level = t.lines(1_000, cfg()).get(0);
		assertEquals("+1 Skill Level", level.text());
		assertEquals(-1, level.fraction(), 1e-9);
	}

	@Test void crossingAQuarterIsAGoldMilestoneHeldTwiceAsLong() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~36/74", false, 36, 74, 0, cfg());
		assertEquals(Panel.CYAN, one(t, 0).color());
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~37/74", false, 37, 74, 100, cfg()); // 37/74 = 50 %
		ProgressToast.Shown s = one(t, 100);
		assertEquals("50% · Mana Wolves  ~37/74", s.text());
		assertEquals(ProgressToast.GOLD, s.color());
		assertEquals(1, t.lines(100 + 2_900, cfg()).size()); // still up at 2.9 s (plain lines are gone by 1.8 s)
		assertTrue(t.lines(100 + 3_300, cfg()).isEmpty());
		// One more unit keeps the quarter it crossed; the next line after it is gone starts plain.
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~38/74", false, 38, 74, 200, cfg());
		assertEquals("50% · Mana Wolves  ~38/74", one(t, 200).text());
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~39/74", false, 39, 74, 10_000, cfg());
		assertEquals("+1 Mana Wolves  ~39/74", one(t, 10_000).text());
	}

	@Test void milestonesAreTheQuartersAndNeverTheTarget() {
		assertEquals(25, ProgressToast.milestone(18, 19, 74)); // 18.5 is the quarter
		assertEquals(0, ProgressToast.milestone(19, 20, 74));
		assertEquals(75, ProgressToast.milestone(55, 56, 74));
		assertEquals(75, ProgressToast.milestone(0, 60, 74)); // a big jump reports the highest crossed
		assertEquals(0, ProgressToast.milestone(73, 74, 74)); // done, not a quarter
		assertEquals(50, ProgressToast.milestone(1, 2, 4));
		assertEquals(0, ProgressToast.milestone(0, 1, 0));
		assertEquals(0, ProgressToast.milestone(-1, 5, -1));
	}

	@Test void theLastFewUnitsCountDownInYellow() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~69/74", false, 69, 74, 0, cfg());
		ProgressToast.Shown s = one(t, 0);
		assertEquals("+1 Mana Wolves  5 to go", s.text());
		assertEquals(Panel.YELLOW, s.color());
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~73/74", false, 73, 74, 10, cfg());
		assertEquals("+2 Mana Wolves  1 to go", one(t, 10).text());
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~74/74", true, 74, 74, 20, cfg());
		assertEquals("✓ Mana Wolves  ~74/74", one(t, 20).text());
		assertEquals(Panel.GREEN, one(t, 20).color());
	}

	@Test void theCountdownWindowShrinksForSmallTargets() {
		assertEquals(5, ProgressToast.countdown(69, 74));
		assertEquals(0, ProgressToast.countdown(68, 74));
		assertEquals(1, ProgressToast.countdown(9, 10)); // a tenth of 10: the last one
		assertEquals(0, ProgressToast.countdown(8, 10));
		assertEquals(2, ProgressToast.countdown(18, 20));
		assertEquals(5, ProgressToast.countdown(995, 1000)); // never more than five
		assertEquals(0, ProgressToast.countdown(74, 74)); // done
		assertEquals(0, ProgressToast.countdown(3, -1));
	}

	@Test void aReversalBelowTheQuarterDropsTheMilestone() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("q:wolves", "Mana Wolves", 2, "~37/74", false, 37, 74, 0, cfg());
		assertEquals(ProgressToast.GOLD, one(t, 0).color());
		t.onReverse("q:wolves", 1, "~36/74", false, 36, 74);
		assertEquals("+1 Mana Wolves  ~36/74", one(t, 0).text());
		assertEquals(Panel.CYAN, one(t, 0).color());
	}
}
