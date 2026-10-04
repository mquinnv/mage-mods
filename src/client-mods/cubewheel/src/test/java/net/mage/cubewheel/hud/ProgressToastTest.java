package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.*;

import net.mage.cubewheel.config.CubeWheelConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProgressToastTest {
	private static CubeWheelConfig.Toast cfg() {
		return new CubeWheelConfig.Toast(); // enabled, 1.5 s
	}

	/** The only line shown at {@code now}. */
	private static ProgressToast.Shown one(ProgressToast t, long now) {
		List<ProgressToast.Shown> lines = t.lines(now, cfg());
		assertEquals(1, lines.size(), "lines: " + lines);
		return lines.get(0);
	}

	private static List<String> texts(ProgressToast t, long now) {
		return t.lines(now, cfg()).stream().map(ProgressToast.Shown::text).toList();
	}

	@Test void aSingleIncrementShowsInCyanForTheSetTimeThenFadesAndIsGone() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~10/74", false, 1_000, cfg());
		ProgressToast.Shown s = one(t, 1_000);
		assertEquals("+1 Mana Wolves  ~10/74", s.text());
		assertEquals(Panel.CYAN, s.color());
		assertEquals(1.0, s.alpha(), 1e-9);
		assertEquals(1.0, one(t, 2_499).alpha(), 1e-9);
		double fading = one(t, 2_650).alpha();
		assertTrue(fading > 0.4 && fading < 0.6, "half way through the fade: " + fading);
		assertTrue(t.lines(2_800, cfg()).isEmpty());
	}

	@Test void incrementsToTheSameEntryMergeAndLiveTheSetTimePastTheLast() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("j:acacia", "Acacia Logs", 1, "~491/630", false, 0, cfg());
		t.onIncrement("j:acacia", "Acacia Logs", 1, "~492/630", false, 1_000, cfg());
		t.onIncrement("j:acacia", "Acacia Logs", 1, "~493/630", false, 1_700, cfg()); // during the fade
		assertEquals("+3 Acacia Logs  ~493/630", one(t, 1_700).text());
		assertEquals(1.0, one(t, 3_199).alpha(), 1e-9);
		assertTrue(t.lines(3_500, cfg()).isEmpty());
	}

	@Test void anIncrementAfterTheToastIsGoneStartsAFreshCount() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("j:acacia", "Acacia Logs", 1, "~491/630", false, 0, cfg());
		t.onIncrement("j:acacia", "Acacia Logs", 1, "~492/630", false, 5_000, cfg());
		assertEquals("+1 Acacia Logs  ~492/630", one(t, 5_000).text());
	}

	@Test void anIncrementToAnotherEntryStartsANewLineAboveWhileTheOldOneKeepsItsOwnTime() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("j:acacia", "Acacia Logs", 5, "~495/630", false, 0, cfg());
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~11/74", false, 500, cfg());
		assertEquals(List.of("+1 Mana Wolves  ~11/74", "+5 Acacia Logs  ~495/630"), texts(t, 500));
		List<ProgressToast.Shown> fading = t.lines(1_650, cfg());
		assertEquals(1.0, fading.get(0).alpha(), 1e-9);
		assertTrue(fading.get(1).alpha() > 0.4 && fading.get(1).alpha() < 0.6, "the older line fades on its own timer");
		assertEquals(List.of("+1 Mana Wolves  ~11/74"), texts(t, 1_800));
		assertEquals(1.0, one(t, 1_999).alpha(), 1e-9);
	}

	@Test void oneSignalCountingForTwoEntriesShowsBothAndEachAddsUp() {
		ProgressToast t = new ProgressToast();
		for (long now = 0; now < 3_000; now += 1_000) {
			// One stone block: the job and the quest both count it, in the same instant.
			t.onIncrement("jobs:stone", "Stone", 1, "~" + (10 + now / 1_000) + "/100", false, now, cfg());
			t.onIncrement("pquests:Miner", "Miner", 1, "~" + (50 + now / 1_000) + "/300", false, now, cfg());
		}
		assertEquals(List.of("+3 Stone  ~12/100", "+3 Miner  ~52/300"), texts(t, 2_000));
	}

	@Test void aSandaraKillStacksItsThreeEntriesMostSpecificFirstAndEachMergesOnItsOwn() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("snakes", "Rattle Snakes", 1, "~30/58", false, 0, cfg());
		t.onIncrement("monsters", "Monsters in Sandara", 1, "~331/670", false, 0, cfg());
		t.onIncrement("slayer", "Sandara Slayer", 1, "~371/1,000", false, 1, cfg());
		assertEquals(List.of("+1 Rattle Snakes  ~30/58", "+1 Monsters in Sandara  ~331/670", "+1 Sandara Slayer  ~371/1,000"),
				texts(t, 1));
		t.onIncrement("snakes", "Rattle Snakes", 1, "~31/58", false, 1_000, cfg());
		t.onIncrement("monsters", "Monsters in Sandara", 1, "~332/670", false, 1_000, cfg());
		t.onIncrement("slayer", "Sandara Slayer", 1, "~372/1,000", false, 1_000, cfg());
		assertEquals(List.of("+2 Rattle Snakes  ~31/58", "+2 Monsters in Sandara  ~332/670", "+2 Sandara Slayer  ~372/1,000"),
				texts(t, 1_000));
		// A Viper (no snake line): the generic two merge and move up, the snake line keeps its own time below.
		t.onIncrement("monsters", "Monsters in Sandara", 1, "~333/670", false, 2_000, cfg());
		t.onIncrement("slayer", "Sandara Slayer", 1, "~373/1,000", false, 2_000, cfg());
		assertEquals(List.of("+3 Monsters in Sandara  ~333/670", "+3 Sandara Slayer  ~373/1,000", "+2 Rattle Snakes  ~31/58"),
				texts(t, 2_000));
		assertEquals(List.of("+3 Monsters in Sandara  ~333/670", "+3 Sandara Slayer  ~373/1,000"), texts(t, 2_900));
	}

	@Test void atMostThreeLinesTheOldestIsDropped() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("a", "A", 1, "", false, 0, cfg());
		t.onIncrement("b", "B", 1, "", false, 0, cfg());
		t.onIncrement("c", "C", 1, "", false, 0, cfg());
		t.onIncrement("d", "D", 1, "", false, 500, cfg());
		assertEquals(List.of("+1 D", "+1 A", "+1 B"), texts(t, 500));
		t.onIncrement("e", "E", 1, "", false, 600, cfg());
		assertEquals(List.of("+1 E", "+1 D", "+1 A"), texts(t, 600));
		// Four in one signal: the last one does not fit.
		ProgressToast u = new ProgressToast();
		for (String id : List.of("w", "x", "y", "z")) u.onIncrement(id, id.toUpperCase(), 1, "", false, 0, cfg());
		assertEquals(List.of("+1 W", "+1 X", "+1 Y"), texts(u, 0));
	}

	@Test void eachLineKeepsItsOwnTick() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("monsters", "Monsters in Sandara", 1, "~669/670", false, 0, cfg());
		t.onIncrement("slayer", "Sandara Slayer", 1, "~372/1,000", false, 0, cfg());
		t.onIncrement("monsters", "Monsters in Sandara", 1, "~670/670", true, 1_000, cfg());
		t.onIncrement("slayer", "Sandara Slayer", 1, "~373/1,000", false, 1_000, cfg());
		List<ProgressToast.Shown> lines = t.lines(1_000, cfg());
		assertEquals("✓ Monsters in Sandara  ~670/670", lines.get(0).text());
		assertEquals(Panel.GREEN, lines.get(0).color());
		assertEquals("+2 Sandara Slayer  ~373/1,000", lines.get(1).text());
		assertEquals(Panel.CYAN, lines.get(1).color());
	}

	@Test void aCompletingIncrementShowsATickInGreen() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~73/74", false, 0, cfg());
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~74/74", true, 100, cfg());
		ProgressToast.Shown s = one(t, 100);
		assertEquals("✓ Mana Wolves  ~74/74", s.text());
		assertEquals(Panel.GREEN, s.color());
	}

	@Test void aReversalTakesBackItsUnitsWithoutExtendingTheToast() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("f:fish", "Fish", 2, "~12/100", false, 0, cfg());
		t.onReverse("f:fish", 1, "~11/100", false);
		assertEquals("+1 Fish  ~11/100", one(t, 1_000).text());
		assertTrue(t.lines(1_900, cfg()).isEmpty(), "still ends 1.5 s + fade after the last increment");
		t.onReverse("other", 1, "~0/1", false); // another entry: ignored
		assertEquals("+1 Fish  ~11/100", one(t, 1_000).text());
	}

	@Test void aReversalOnlyTouchesItsOwnLine() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("jobs:stone", "Stone", 1, "~10/100", false, 0, cfg());
		t.onIncrement("pquests:Miner", "Miner", 1, "~50/300", false, 0, cfg());
		t.onReverse("jobs:stone", 1, "~9/100", false);
		assertEquals(List.of("+1 Miner  ~50/300"), texts(t, 0));
	}

	@Test void aReversalOfEverythingShownHidesIt() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("f:fish", "Fish", 1, "~11/100", false, 0, cfg());
		t.onReverse("f:fish", 1, "~10/100", false);
		assertTrue(t.lines(10, cfg()).isEmpty());
		// A re-count right after (the chat catch replacing the bobber's) starts again at +1, not +2.
		t.onIncrement("f:fish", "Fish", 1, "~11/100", false, 20, cfg());
		assertEquals("+1 Fish  ~11/100", one(t, 20).text());
	}

	@Test void disabledShowsNothing() {
		ProgressToast t = new ProgressToast();
		CubeWheelConfig.Toast off = cfg();
		off.enabled = false;
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~10/74", false, 0, off);
		assertTrue(t.lines(0, cfg()).isEmpty(), "an increment while off is not kept");
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~10/74", false, 0, cfg());
		assertTrue(t.lines(0, off).isEmpty());
	}

	@Test void theConfiguredTimeIsUsed() {
		ProgressToast t = new ProgressToast();
		CubeWheelConfig.Toast c = cfg();
		c.seconds = 3.0;
		t.onIncrement("q:wolves", "Mana Wolves", 1, "~10/74", false, 0, c);
		assertEquals(1.0, t.lines(2_999, c).get(0).alpha(), 1e-9);
		assertTrue(t.lines(3_300, c).isEmpty());
	}

	@Test void aCountlessEntryShowsJustItsName() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("q:x", "Volcano Potion Quest", 1, "", false, 0, cfg());
		assertEquals("+1 Volcano Potion Quest", one(t, 0).text());
	}

	@Test void aNoticeShowsItsTextInItsColourAndFadesLikeTheRest() {
		ProgressToast t = new ProgressToast();
		t.onNotice("Inventory full", 0xFFFF5555, 1_000, cfg());
		ProgressToast.Shown s = one(t, 1_000);
		assertEquals("Inventory full", s.text());
		assertEquals(0xFFFF5555, s.color());
		assertEquals(1.0, one(t, 2_499).alpha(), 1e-9);
		assertTrue(t.lines(2_800, cfg()).isEmpty());
	}

	@Test void theSameNoticeAgainRestartsItsLine() {
		ProgressToast t = new ProgressToast();
		t.onNotice("Inventory full", 0xFFFF5555, 0, cfg());
		t.onNotice("Inventory full", 0xFFFF5555, 1_000, cfg());
		assertEquals(1.0, one(t, 2_000).alpha(), 1e-9);
	}

	@Test void noticesStackWithProgressAndShareTheLineLimit() {
		ProgressToast t = new ProgressToast();
		t.onIncrement("j:stone", "Stone", 1, "~10/100", false, 0, cfg());
		t.onNotice("Inventory full", 0xFFFF5555, 10, cfg());
		assertEquals(List.of("Inventory full", "+1 Stone  ~10/100"), texts(t, 10));
		t.onNotice("PV 2 full", 0xFFFF5555, 100, cfg());
		t.onNotice("Phoenix set bonus lost (3/4)", Panel.YELLOW, 200, cfg());
		assertEquals(List.of("Phoenix set bonus lost (3/4)", "PV 2 full", "Inventory full"), texts(t, 200));
	}

	@Test void noNoticeWhileThePopupIsOff() {
		ProgressToast t = new ProgressToast();
		CubeWheelConfig.Toast off = cfg();
		off.enabled = false;
		t.onNotice("Inventory full", 0xFFFF5555, 0, off);
		assertTrue(t.lines(0, cfg()).isEmpty());
	}
}
