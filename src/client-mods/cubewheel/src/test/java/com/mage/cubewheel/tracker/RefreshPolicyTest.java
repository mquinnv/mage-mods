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
		assertEquals(FINISHED, p.step(TIMEOUT + GAP + TIMEOUT + 1, View.NONE).kind());
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

	@Test void anUnexpectedMenuBetweenCommandsAborts() {
		RefreshPolicy p = new RefreshPolicy();
		p.start(0, List.of("/a", "/b"));
		p.step(0, View.NONE);
		p.step(10, View.MENU_READY);
		assertEquals(CLOSE_MENU, p.step(10 + SETTLE, View.MENU_READY).kind());
		assertEquals(ABORTED, p.step(10 + SETTLE + 50, View.MENU_READY).kind());
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
