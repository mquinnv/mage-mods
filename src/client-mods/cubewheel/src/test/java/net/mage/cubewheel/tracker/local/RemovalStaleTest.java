package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A settled id and a remembered probe go stale after {@link RemovalKills#STALE_MS}: a Void Scuttler's slime hitbox,
 * met during an earlier fight and settled then, was hit 27 times ten minutes later and its removal refused as
 * "already counted or refused" (capture 2026-10-08, 1 of 5 scuttlers counted). The server re-tracks a mob under the
 * same id, so an old id is a fresh entity again.
 */
class RemovalStaleTest {
	static final RemovalKills.Hint TAG = new RemovalKills.Hint("Void Scuttler", RemovalKills.Method.LINKED, "tag text_display #9", 9);

	@Test void aSettledIdIsClaimableAgainAfterThirtySeconds() {
		RemovalKills r = new RemovalKills();
		r.hit(5, 1_000);
		assertTrue(r.claim(5, true, true));
		assertFalse(r.claim(5, true, true)); // the same removal never twice
		assertTrue(r.settled(5));
		assertEquals(0, r.settledAgeMs(5));
		r.tick(1_000 + RemovalKills.STALE_MS);
		assertTrue(r.settled(5)); // exactly at the edge: still settled
		r.hit(5, 1_001 + RemovalKills.STALE_MS); // the same id, hit again much later: a re-tracked mob
		assertFalse(r.settled(5));
		assertEquals(-1, r.settledAgeMs(5));
		assertTrue(r.claim(5, true, true));
	}

	@Test void aSettledIdFromADeathOrStackGoesStaleToo() {
		RemovalKills r = new RemovalKills();
		r.hit(1, 0);
		r.settle(1);
		assertFalse(r.claim(1, true, true));
		assertEquals(List.of(), r.expire(RemovalKills.STALE_MS + 1)); // the tick prunes it
		assertTrue(r.claim(1, true, true));
	}

	@Test void aRememberedProbeIsLookedAtAgainAfterThirtySecondsButItsHintStays() {
		RemovalKills r = new RemovalKills();
		r.hit(7, 0);
		r.remember(7, TAG);
		assertTrue(r.seen(7));
		r.hit(7, RemovalKills.STALE_MS + 1);
		assertFalse(r.seen(7)); // the adapter probes again on this hit
		assertEquals(Optional.of(TAG), r.hint(7)); // until it does, the old hint is still there
		r.remember(7, null);
		assertTrue(r.seen(7));
		assertEquals(Optional.empty(), r.hint(7));
	}

	@Test void aSettledTagIsReusableForALaterKillOnceStale() {
		RemovalKills r = new RemovalKills();
		r.hit(1, 0);
		r.remember(1, TAG);
		r.model(1, List.of(100));
		assertTrue(r.claim(1, true, true, id -> true));
		assertTrue(r.settled(9)); // the tag went with the model
		r.hit(2, 0);
		assertFalse(r.acceptReprobe(2, TAG, List.of(9, 2))); // a neighbour named by the counted tag: no
		r.hit(2, RemovalKills.STALE_MS + 1);
		assertTrue(r.acceptReprobe(2, TAG, List.of(9, 2))); // the server reused the id long after: a new mob
	}
}
