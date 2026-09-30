package com.mage.cubewheel.tracker;

import static com.mage.cubewheel.tracker.RefreshPolicy.Kind.ABORTED;
import static com.mage.cubewheel.tracker.RefreshPolicy.Kind.CLOSE_MENU;
import static com.mage.cubewheel.tracker.RefreshPolicy.Kind.FINISHED;
import static com.mage.cubewheel.tracker.RefreshPolicy.Kind.NONE;
import static com.mage.cubewheel.tracker.RefreshPolicy.Kind.SEND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mage.cubewheel.tracker.RefreshPolicy.Action;
import com.mage.cubewheel.tracker.RefreshPolicy.Outcome;
import com.mage.cubewheel.tracker.RefreshPolicy.View;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefreshPolicyTest {
	static final List<String> CMDS = List.of("/pquests", "/prestige", "/jobs");
	static final long SETTLE = RefreshPolicy.SETTLE_MS;
	static final long GAP = RefreshPolicy.GAP_MS;
	static final long TIMEOUT = RefreshPolicy.MENU_TIMEOUT_MS;

	@Test void fullRunSendsEachCommandOnlyAfterThePreviousMenuWasHandled() {
		RefreshPolicy p = new RefreshPolicy();
		assertEquals(Outcome.STARTED, p.start(0, CMDS).outcome());
		assertTrue(p.running());
		long t = 0;
		List<String> sent = new ArrayList<>();
		for (int i = 0; i < CMDS.size(); i++) {
			Action send = p.step(t, View.NONE);
			assertEquals(SEND, send.kind(), "step " + i);
			sent.add(send.command());
			// While no menu has been handled, nothing else is sent, however long it takes (below the timeout).
			assertEquals(NONE, p.step(t + 50, View.NONE).kind());
			assertEquals(NONE, p.step(t + 100, View.MENU_LOADING).kind()); // menu opened, slots not filled yet
			assertEquals(NONE, p.step(t + 150, View.MENU_READY).kind());   // filled: settle before closing
			assertEquals(NONE, p.step(t + 150 + SETTLE - 1, View.MENU_READY).kind());
			assertEquals(CLOSE_MENU, p.step(t + 150 + SETTLE, View.MENU_READY).kind());
			t = t + 150 + SETTLE;
			if (i < CMDS.size() - 1) {
				assertEquals(NONE, p.step(t + GAP - 1, View.NONE).kind()); // short gap after closing
				t += GAP;
			}
		}
		assertEquals(CMDS, sent);
		assertEquals(FINISHED, p.step(t + 1, View.NONE).kind());
		assertFalse(p.running());
		assertEquals(3, p.handled());
		assertEquals(0, p.timedOut());
	}

	@Test void aMenuThatNeverOpensTimesOutAndTheNextCommandFollows() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, List.of("/a", "/b"));
		assertEquals("/a", p.step(0, View.NONE).command());
		assertEquals(NONE, p.step(TIMEOUT - 1, View.NONE).kind());
		assertEquals(NONE, p.step(TIMEOUT, View.NONE).kind()); // timed out; gap before the next command
		Action next = p.step(TIMEOUT + GAP, View.NONE);
		assertEquals(SEND, next.kind());
		assertEquals("/b", next.command());
		assertEquals(NONE, p.step(TIMEOUT + GAP + TIMEOUT, View.NONE).kind());
		// The last command timed out: a late menu may still arrive during one more gap.
		assertEquals(NONE, p.step(TIMEOUT + GAP + TIMEOUT + GAP - 1, View.NONE).kind());
		assertEquals(FINISHED, p.step(TIMEOUT + GAP + TIMEOUT + GAP, View.NONE).kind());
		assertEquals(0, p.handled());
		assertEquals(2, p.timedOut());
	}

	@Test void aMenuThatStaysEmptyIsClosedAfterTheTimeout() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, List.of("/a"));
		p.step(0, View.NONE);
		assertEquals(NONE, p.step(100, View.MENU_LOADING).kind());
		assertEquals(NONE, p.step(100 + TIMEOUT - 1, View.MENU_LOADING).kind());
		assertEquals(CLOSE_MENU, p.step(100 + TIMEOUT, View.MENU_LOADING).kind());
		assertEquals(FINISHED, p.step(100 + TIMEOUT + 1, View.NONE).kind());
		assertEquals(1, p.timedOut());
	}

	@Test void atMostOneRunPerCooldown() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(1_000, List.of("/a"));
		p.abort();
		RefreshPolicy.Start again = p.start(31_000, List.of("/a"));
		assertEquals(Outcome.COOLDOWN, again.outcome());
		assertEquals(30, again.waitSeconds());
		assertEquals(Outcome.COOLDOWN, p.start(1_000 + RefreshPolicy.COOLDOWN_MS - 1, List.of("/a")).outcome());
		assertEquals(1, p.start(1_000 + RefreshPolicy.COOLDOWN_MS - 1, List.of("/a")).waitSeconds());
		assertEquals(Outcome.STARTED, p.start(1_000 + RefreshPolicy.COOLDOWN_MS, List.of("/a")).outcome());
	}

	@Test void aRunInProgressCannotRestart() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, CMDS);
		p.step(0, View.NONE);
		assertEquals(Outcome.RUNNING, p.start(RefreshPolicy.COOLDOWN_MS * 2, CMDS).outcome());
		assertTrue(p.running());
	}

	@Test void noCommandsDoesNotStartOrConsumeTheCooldown() {
		RefreshPolicy p = new RefreshPolicy();
		assertEquals(Outcome.NO_COMMANDS, p.start(0, List.of()).outcome());
		assertEquals(Outcome.NO_COMMANDS, p.start(0, null).outcome());
		assertEquals(Outcome.NO_COMMANDS, p.start(0, java.util.Arrays.asList(" ", null)).outcome());
		assertFalse(p.running());
		assertEquals(Outcome.STARTED, p.start(1, List.of("/a")).outcome());
	}

	@Test void blankCommandsAreSkipped() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, java.util.Arrays.asList("", "/a", null));
		assertEquals("/a", p.step(0, View.NONE).command());
	}

	@Test void userOpeningAnotherScreenWhileWaitingAborts() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, CMDS);
		p.step(0, View.NONE);
		Action a = p.step(50, View.OTHER); // e.g. chat, inventory, pause menu (Esc)
		assertEquals(ABORTED, a.kind());
		assertFalse(p.running());
		assertEquals(NONE, p.step(100, View.NONE).kind()); // nothing more is ever sent
	}

	@Test void closingTheMenuYourselfAborts() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, CMDS);
		p.step(0, View.NONE);
		p.step(50, View.MENU_READY);
		assertEquals(ABORTED, p.step(100, View.NONE).kind()); // Esc closed the menu before we did
		assertFalse(p.running());
	}

	@Test void switchingToAnotherScreenWhileTheMenuIsOpenAborts() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, CMDS);
		p.step(0, View.NONE);
		p.step(50, View.MENU_LOADING);
		assertEquals(ABORTED, p.step(100, View.OTHER).kind());
	}

	@Test void anUnrecognisedMenuBetweenCommandsAborts() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, List.of("/a", "/b"));
		p.step(0, View.NONE);
		p.step(10, View.MENU_READY);
		assertEquals(CLOSE_MENU, p.step(10 + SETTLE, View.MENU_READY).kind());
		assertEquals(ABORTED, p.step(10 + SETTLE + 50, View.OTHER).kind());
		assertFalse(p.running());
	}

	// --- late menus (a menu that arrives after its command timed out, or in the gap) ---

	@Test void aLateRecognisedMenuInTheGapIsClosedAndTheRunGoesOn() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, List.of("/a", "/b"));
		assertEquals("/a", p.step(0, View.NONE).command());
		assertEquals(NONE, p.step(TIMEOUT, View.NONE).kind()); // /a timed out
		long t = TIMEOUT + 10;
		assertEquals(NONE, p.step(t, View.MENU_LOADING).kind()); // /a's menu shows up late
		assertEquals(NONE, p.step(t + 10, View.MENU_READY).kind());
		assertEquals(CLOSE_MENU, p.step(t + 10 + SETTLE, View.MENU_READY).kind());
		t = t + 10 + SETTLE;
		assertEquals(NONE, p.step(t + GAP - 1, View.NONE).kind());
		Action next = p.step(t + GAP, View.NONE);
		assertEquals(SEND, next.kind());
		assertEquals("/b", next.command());
		assertEquals(1, p.timedOut());
	}

	@Test void aLateMenuAfterTheLastCommandTimedOutIsClosedToo() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, List.of("/a"));
		p.step(0, View.NONE);
		assertEquals(NONE, p.step(TIMEOUT, View.NONE).kind());
		assertEquals(NONE, p.step(TIMEOUT + 20, View.MENU_READY).kind());
		assertEquals(CLOSE_MENU, p.step(TIMEOUT + 20 + SETTLE, View.MENU_READY).kind());
		assertEquals(FINISHED, p.step(TIMEOUT + 20 + SETTLE + 1, View.NONE).kind());
	}

	@Test void aLateUnrecognisedMenuAbortsAndIsLeftAlone() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, List.of("/a", "/b"));
		p.step(0, View.NONE);
		p.step(TIMEOUT, View.NONE);
		assertEquals(ABORTED, p.step(TIMEOUT + 10, View.OTHER).kind());
		assertFalse(p.running());
		assertEquals(NONE, p.step(TIMEOUT + GAP, View.OTHER).kind()); // never closed, nothing sent
	}

	@Test void aLateUnknownMenuAbortsAndIsLeftAlone() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, List.of("/a", "/b"));
		p.step(0, View.NONE);
		p.step(10, View.MENU_READY);
		assertEquals(CLOSE_MENU, p.step(10 + SETTLE, View.MENU_READY).kind());
		assertEquals(ABORTED, p.step(10 + SETTLE + 50, View.MENU_UNKNOWN).kind()); // a chest in the gap
		assertFalse(p.running());
		RefreshPolicy q = new RefreshPolicy();
		q.start(0, List.of("/a"));
		assertEquals(ABORTED, q.step(0, View.MENU_UNKNOWN).kind()); // open before anything was sent
	}

	// --- a run started from the picker (a CubeWheel screen), and its own command's menu ---

	@Test void aRunStartedWithTheModScreenOpenIsNotAborted() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, CMDS);
		Action first = p.step(0, View.OWN); // the picker is still up on the first step
		assertEquals(SEND, first.kind());
		assertEquals("/pquests", first.command());
		assertEquals(NONE, p.step(50, View.OWN).kind());
		assertEquals(NONE, p.step(2000, View.MENU_LOADING).kind()); // the server's menu replaces the picker
		assertEquals(NONE, p.step(2010, View.MENU_READY).kind());
		assertEquals(CLOSE_MENU, p.step(2010 + SETTLE, View.MENU_READY).kind());
		assertTrue(p.running());
		assertEquals(1, p.handled());
	}

	@Test void theCommandsOwnMenuIsWaitedForWhileItIsNotRecognisedYet() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, CMDS);
		p.step(0, View.NONE);
		assertEquals(NONE, p.step(1500, View.MENU_UNKNOWN).kind()); // e.g. filler slots arrive first
		assertEquals(NONE, p.step(1600, View.MENU_READY).kind());
		assertEquals(CLOSE_MENU, p.step(1600 + SETTLE, View.MENU_READY).kind());
		assertEquals(1, p.handled());
		assertTrue(p.running());
	}

	@Test void theCommandsOwnUnrecognisedMenuIsClosedAfterTheTimeout() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, List.of("/a", "/b"));
		p.step(0, View.NONE);
		assertEquals(NONE, p.step(50, View.MENU_UNKNOWN).kind());
		assertEquals(NONE, p.step(50 + TIMEOUT - 1, View.MENU_UNKNOWN).kind());
		assertEquals(CLOSE_MENU, p.step(50 + TIMEOUT, View.MENU_UNKNOWN).kind());
		assertEquals(1, p.timedOut());
		Action next = p.step(50 + TIMEOUT + GAP, View.NONE);
		assertEquals("/b", next.command());
	}

	@Test void aLateMenuThatStaysEmptyAbortsInsteadOfBeingClosed() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, List.of("/a", "/b"));
		p.step(0, View.NONE);
		p.step(TIMEOUT, View.NONE);
		assertEquals(NONE, p.step(TIMEOUT + 10, View.MENU_LOADING).kind());
		Action a = p.step(TIMEOUT + 10 + TIMEOUT, View.MENU_LOADING);
		assertEquals(ABORTED, a.kind()); // never recognised: left open for the user
		assertFalse(p.running());
	}

	// --- the user's own actions ---

	@Test void useOrAttackWhileWaitingAborts() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, CMDS);
		assertEquals(SEND, p.step(0, View.NONE, false).kind());
		Action a = p.step(50, View.NONE, true); // right-click on a chest/NPC, or attack
		assertEquals(ABORTED, a.kind());
		assertFalse(p.running());
		// The menu the user opened is never touched.
		assertEquals(NONE, p.step(100, View.MENU_READY, false).kind());
	}

	@Test void useOrAttackDuringTheGapAborts() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, CMDS);
		p.step(0, View.NONE);
		p.step(10, View.MENU_READY);
		assertEquals(CLOSE_MENU, p.step(10 + SETTLE, View.MENU_READY).kind());
		assertEquals(ABORTED, p.step(10 + SETTLE + 20, View.NONE, true).kind());
		assertEquals(NONE, p.step(10 + SETTLE + GAP, View.NONE, false).kind()); // /prestige is never sent
	}

	@Test void useOrAttackBeforeTheFirstCommandAbortsWithoutSending() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, CMDS);
		assertEquals(ABORTED, p.step(0, View.NONE, true).kind());
		assertFalse(p.running());
	}

	@Test void heldInputWhileOurMenuIsOpenDoesNotAbort() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, List.of("/a"));
		p.step(0, View.NONE);
		assertEquals(NONE, p.step(10, View.MENU_READY, false).kind());
		// Once our menu is open (hidden, input blocked) a stuck key state does not matter.
		assertEquals(NONE, p.step(20, View.MENU_READY, true).kind());
		assertEquals(CLOSE_MENU, p.step(10 + SETTLE, View.MENU_READY, true).kind());
	}

	// --- which menus the run may hide and close ---

	@Test void onlyRecognisedMenusCountAsTheRunsMenu() {
		String glyph = ManaCubeMenusTest.GLYPH_TITLE;
		java.util.Map<String, String> sources = ManaCubeMenusTest.TITLE_SOURCES;
		assertEquals(View.MENU_LOADING, RefreshPolicy.menuView(glyph, List.of(), sources));
		assertEquals(View.MENU_READY, RefreshPolicy.menuView(glyph, ManaCubeMenusTest.JOBS_MAIN, sources));
		assertEquals(View.MENU_READY, RefreshPolicy.menuView(glyph, ManaCubeMenusTest.PRESTIGE_RANKS, sources));
		assertEquals(View.MENU_READY, RefreshPolicy.menuView(glyph, ManaCubeMenusTest.PARTY_QUESTS, sources));
		// Any other menu (warps, a chest) is MENU_UNKNOWN: outside the run's own command it aborts the run
		// and stays visible and usable.
		assertEquals(View.MENU_UNKNOWN, RefreshPolicy.menuView(glyph, ManaCubeMenusTest.WARPS, sources));
		assertEquals(View.MENU_UNKNOWN, RefreshPolicy.menuView("Chest",
				List.of(ManaCubeMenusTest.item(0, "Diamond", "A shiny gem")), sources));
	}

	@Test void finishedMessage() {
		assertEquals("Refreshed 1 tracker", RefreshPolicy.finishedMessage(1, 0));
		assertEquals("Refreshed 7 trackers (1 menu did not load)", RefreshPolicy.finishedMessage(7, 1));
		assertEquals("Refreshed 0 trackers (2 menus did not load)", RefreshPolicy.finishedMessage(0, 2));
	}

	@Test void aScreenAlreadyOpenAtStartAborts() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, CMDS);
		assertEquals(ABORTED, p.step(0, View.OTHER).kind());
	}

	@Test void externalAbortStopsTheRun() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, CMDS);
		p.step(0, View.NONE);
		assertTrue(p.abort());
		assertFalse(p.running());
		assertFalse(p.abort()); // nothing running
		assertEquals(NONE, p.step(10, View.NONE).kind());
	}

	@Test void idlePolicyDoesNothing() {
		RefreshPolicy p = new RefreshPolicy();
		assertEquals(NONE, p.step(0, View.MENU_READY).kind());
		assertFalse(p.running());
	}
}
