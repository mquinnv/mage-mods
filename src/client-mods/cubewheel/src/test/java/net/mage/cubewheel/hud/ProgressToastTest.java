package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.*;

import net.mage.cubewheel.config.CubeWheelConfig;
import org.junit.jupiter.api.Test;

class ProgressToastTest {
	private static CubeWheelConfig.Toast cfg() {
		return new CubeWheelConfig.Toast(); // enabled, 1.5 s
	}

	@Test void aSingleIncrementShowsInCyanForTheSetTimeThenFadesAndIsGone() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~10/74", false, 1_000, cfg());
		ProgressToast.Shown s = t.current(1_000, cfg()).orElseThrow();
		assertEquals("+1 Mana Wolves  ~10/74", s.text());
		assertEquals(Panel.CYAN, s.color());
		assertEquals(1.0, s.alpha(), 1e-9);
		assertEquals(1.0, t.current(2_499, cfg()).orElseThrow().alpha(), 1e-9);
		double fading = t.current(2_650, cfg()).orElseThrow().alpha();
		assertTrue(fading > 0.4 && fading < 0.6, "half way through the fade: " + fading);
		assertTrue(t.current(2_800, cfg()).isEmpty());
	}

	@Test void incrementsToTheSameEntryMergeAndLiveTheSetTimePastTheLast() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("j:acacia", "Acacia Logs", 1, "~491/630", false, 0, cfg());
		t.onIncrement("j:acacia", "Acacia Logs", 1, "~492/630", false, 1_000, cfg());
		t.onIncrement("j:acacia", "Acacia Logs", 1, "~493/630", false, 1_700, cfg()); // during the fade
		assertEquals("+3 Acacia Logs  ~493/630", t.current(1_700, cfg()).orElseThrow().text());
		assertEquals(1.0, t.current(3_199, cfg()).orElseThrow().alpha(), 1e-9);
		assertTrue(t.current(3_500, cfg()).isEmpty());
	}

	@Test void anIncrementAfterTheToastIsGoneStartsAFreshCount() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("j:acacia", "Acacia Logs", 1, "~491/630", false, 0, cfg());
		t.onIncrement("j:acacia", "Acacia Logs", 1, "~492/630", false, 5_000, cfg());
		assertEquals("+1 Acacia Logs  ~492/630", t.current(5_000, cfg()).orElseThrow().text());
	}

	@Test void anIncrementToAnotherEntryReplacesTheToast() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("j:acacia", "Acacia Logs", 5, "~495/630", false, 0, cfg());
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~11/74", false, 500, cfg());
		assertEquals("+1 Mana Wolves  ~11/74", t.current(500, cfg()).orElseThrow().text());
		assertEquals(1.0, t.current(1_999, cfg()).orElseThrow().alpha(), 1e-9);
	}

	@Test void oneSignalCountingForTwoEntriesShowsTheFirstSoRepeatedSignalsStillAddUp() {
		ProgressToast t = new ProgressToast();
		for (long now = 0; now < 3_000; now += 1_000) {
			// One stone block: the job and the quest both count it, in the same instant.
			t.onIncrement("jobs:stone", "Stone", 1, "~" + (10 + now / 1_000) + "/100", false, now, cfg());
			t.onIncrement("pquests:Miner", "Miner", 1, "~" + (50 + now / 1_000) + "/300", false, now, cfg());
		}
		assertEquals("+3 Stone  ~12/100", t.current(2_000, cfg()).orElseThrow().text());
	}

	@Test void aCompletingIncrementShowsATickInGreen() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~73/74", false, 0, cfg());
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~74/74", true, 100, cfg());
		ProgressToast.Shown s = t.current(100, cfg()).orElseThrow();
		assertEquals("✓ Mana Wolves  ~74/74", s.text());
		assertEquals(Panel.GREEN, s.color());
	}

	@Test void aReversalTakesBackItsUnitsWithoutExtendingTheToast() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("f:fish", "Fish", 2, "~12/100", false, 0, cfg());
		t.onReverse("f:fish", 1, "~11/100", false);
		assertEquals("+1 Fish  ~11/100", t.current(1_000, cfg()).orElseThrow().text());
		assertTrue(t.current(1_900, cfg()).isEmpty(), "still ends 1.5 s + fade after the last increment");
		t.onReverse("other", 1, "~0/1", false); // another entry: ignored
	}

	@Test void aReversalOfEverythingShownHidesIt() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("f:fish", "Fish", 1, "~11/100", false, 0, cfg());
		t.onReverse("f:fish", 1, "~10/100", false);
		assertTrue(t.current(10, cfg()).isEmpty());
		// A re-count right after (the chat catch replacing the bobber's) starts again at +1, not +2.
		t.onIncrement("f:fish", "Fish", 1, "~11/100", false, 20, cfg());
		assertEquals("+1 Fish  ~11/100", t.current(20, cfg()).orElseThrow().text());
	}

	@Test void disabledShowsNothing() {
		ProgressToast t = new ProgressToast();
		CubeWheelConfig.Toast off = cfg();
		off.enabled = false;
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~10/74", false, 0, off);
		assertTrue(t.current(0, cfg()).isEmpty(), "an increment while off is not kept");
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~10/74", false, 0, cfg());
		assertTrue(t.current(0, off).isEmpty());
	}

	@Test void theConfiguredTimeIsUsed() {
		ProgressToast t = new ProgressToast();
		CubeWheelConfig.Toast c = cfg();
		c.seconds = 3.0;
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~10/74", false, 0, c);
		assertEquals(1.0, t.current(2_999, c).orElseThrow().alpha(), 1e-9);
		assertTrue(t.current(3_300, c).isEmpty());
	}

	@Test void aCountlessEntryShowsJustItsName() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("q:x", "Volcano Potion Quest", 1, "", false, 0, cfg());
		assertEquals("+1 Volcano Potion Quest", t.current(0, cfg()).orElseThrow().text());
	}
}
