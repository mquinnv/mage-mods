package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.tracker.local.ActionBarFeed.Source;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Each action-bar message reaches the loot matcher once, whichever of the three sources sees it. */
class ActionBarFeedTest {
	static final String CUTE = "+1  Cute Firefly";

	@Test void pollAloneSeesANewMessage() {
		ActionBarFeed f = new ActionBarFeed();
		assertFalse(f.poll(null, 0, 0));
		assertTrue(f.poll(CUTE, 59, 50));
		assertFalse(f.poll(CUTE, 58, 100)); // same message, counting down
		assertFalse(f.poll(CUTE, 57, 150));
	}

	@Test void pollSeesTheSameTextSetAgainByTheRisingTimer() {
		ActionBarFeed f = new ActionBarFeed();
		assertTrue(f.poll(CUTE, 59, 0));
		assertFalse(f.poll(CUTE, 30, 1500));
		assertTrue(f.poll(CUTE, 59, 1550)); // "+1  Cute Firefly" again: a second catch
		assertFalse(f.poll(CUTE, 58, 1600));
	}

	@Test void pollIgnoresTextNoLongerShown() {
		ActionBarFeed f = new ActionBarFeed();
		assertFalse(f.poll(CUTE, 0, 0)); // a stale message left in the field
		assertFalse(f.poll(CUTE, 0, 50));
	}

	@Test void pollSeesEveryChange() {
		ActionBarFeed f = new ActionBarFeed();
		assertTrue(f.poll("+5 Mana | +2  Tiger Hide", 59, 0));
		assertTrue(f.poll(CUTE, 59, 50));
		assertTrue(f.poll("", 59, 100));
		assertTrue(f.poll(CUTE, 59, 150));
	}

	@Test void packetThenHudThenPollIsOneMessage() {
		ActionBarFeed f = new ActionBarFeed();
		assertTrue(f.event(Source.PACKET, CUTE, 1000));
		assertFalse(f.event(Source.HUD, CUTE, 1000));
		assertFalse(f.poll(CUTE, 59, 1010));
		assertFalse(f.poll(CUTE, 58, 1060));
	}

	@Test void hudAloneThenPollIsOneMessage() {
		ActionBarFeed f = new ActionBarFeed();
		assertTrue(f.event(Source.HUD, CUTE, 1000));
		assertFalse(f.poll(CUTE, 59, 1010));
	}

	@Test void theSameSourceTwiceIsTwoMessages() {
		ActionBarFeed f = new ActionBarFeed();
		assertTrue(f.event(Source.PACKET, CUTE, 1000));
		assertFalse(f.event(Source.HUD, CUTE, 1000));
		assertTrue(f.event(Source.PACKET, CUTE, 1050));
		assertFalse(f.event(Source.HUD, CUTE, 1050));
		assertFalse(f.poll(CUTE, 59, 1060));
	}

	@Test void identicalMessagesSecondsApartAreFedAgainByTheHooks() {
		ActionBarFeed f = new ActionBarFeed();
		assertTrue(f.event(Source.HUD, CUTE, 1000));
		assertFalse(f.poll(CUTE, 59, 1010));
		assertTrue(f.event(Source.PACKET, CUTE, 5000)); // another source, but long after
		assertFalse(f.poll(CUTE, 59, 5010));
	}

	@Test void pollFeedsWhatTheHooksMissed() {
		ActionBarFeed f = new ActionBarFeed();
		assertTrue(f.event(Source.PACKET, "+5 Mana", 1000));
		assertTrue(f.poll(CUTE, 59, 1010)); // set later in the tick by a path no hook saw
		assertFalse(f.poll(CUTE, 58, 1060));
	}

	@Test void aHookAfterThePollFedItIsANewMessage() {
		ActionBarFeed f = new ActionBarFeed();
		assertTrue(f.poll(CUTE, 59, 1000));
		assertTrue(f.event(Source.HUD, CUTE, 1040)); // set again, seen by a hook this time
		assertFalse(f.poll(CUTE, 59, 1050));
	}

	@Test void nullEventsAreIgnored() {
		ActionBarFeed f = new ActionBarFeed();
		assertFalse(f.event(Source.PACKET, null, 0));
		assertTrue(f.poll(CUTE, 59, 10));
	}

	/** End to end with RemovalKills: the poll's loot line names the firefly removal waiting for it, once. */
	@Test void polledLootLineNamesAWaitingRemovalOnce() {
		ActionBarFeed f = new ActionBarFeed();
		RemovalKills removals = new RemovalKills();
		removals.hit(7, 0);
		assertTrue(removals.claim(7, true, true));
		RemovalKills.Removed r = CatchRemovalTest.interaction(7);
		assertTrue(removals.awaitLoot(r, 100).isEmpty());
		assertTrue(removals.awaitingLoot(7));
		assertFalse(removals.awaitingLoot(8));

		List<RemovalKills.Resolved> resolved = List.of();
		if (f.poll(CUTE, 59, 150)) resolved = removals.onActionBar(CUTE, 150);
		assertEquals(List.of(new RemovalKills.Resolved(r, List.of("Cute Firefly"))), resolved);
		assertFalse(removals.awaitingLoot(7));
		assertFalse(f.poll(CUTE, 58, 200));
		assertTrue(removals.settled(7));
	}
}
