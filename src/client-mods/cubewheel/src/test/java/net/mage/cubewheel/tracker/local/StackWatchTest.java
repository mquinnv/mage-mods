package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Stacked mobs: a kill lowers the stack count in the name instead of killing the entity. */
class StackWatchTest {
	@Test void stackDecreaseIsAKillPerStep() {
		StackWatch w = new StackWatch();
		w.watch(10, 10, "5x Tiger 20⺛");
		assertTrue(w.watching(10));
		assertEquals(Optional.of(new StackWatch.Change(10, 10, "5x Tiger 20⺛", "5x Tiger 12⺛", 0)), w.onName(10, "5x Tiger 12⺛"));
		assertEquals(Optional.of(new StackWatch.Change(10, 10, "5x Tiger 12⺛", "4x Tiger 20⺛", 1)), w.onName(10, "4x Tiger 20⺛"));
		assertEquals(Optional.empty(), w.onName(10, "4x Tiger 20⺛")); // unchanged
		assertEquals(0, w.onName(10, "Tiger 20⺛").orElseThrow().killed()); // lost its count: not "4 -> 1"
		assertEquals(Optional.of(new StackWatch.Change(10, 10, "Tiger 20⺛", "4x Tiger 20⺛", 0)), w.onName(10, "4x Tiger 20⺛"));
		assertEquals(1, w.onName(10, "3x Tiger").orElseThrow().killed());
		assertEquals(1, w.onName(10, "Tiger x2").orElseThrow().killed()); // suffix form still has a count
		assertEquals(1, w.onName(10, "Tiger (x1)").orElseThrow().killed());
	}

	@Test void countFlippingOffAndOnIsNotAKill() {
		StackWatch w = new StackWatch();
		w.watch(10, 10, "5x Tiger");
		assertEquals(0, w.onName(10, "Tiger").orElseThrow().killed());
		assertEquals(0, w.onName(10, "5x Tiger").orElseThrow().killed());
		assertEquals(0, w.onName(10, "Tiger 20⺛").orElseThrow().killed());
		assertEquals(0, w.onName(10, "5x Tiger").orElseThrow().killed());
	}

	@Test void bigDropsNeedAFreshLocalHit() {
		StackWatch w = new StackWatch();
		w.watch(10, 10, "10x Tiger"); // watching starts with a hit
		assertEquals(6, w.onName(10, "4x Tiger").orElseThrow().killed()); // hit since the last change: full drop
		assertEquals(StackWatch.MAX_UNHIT_DROP, w.onName(10, "1x Tiger").orElseThrow().killed()); // no hit since: capped
		w.watch(20, 20, "8x Wolf");
		w.onName(20, "8x Wolf 12⺛"); // health change uses up the hit
		w.watch(20, 20, "8x Wolf 12⺛"); // a local hit or damage event refreshes the watch
		assertEquals(5, w.onName(20, "3x Wolf").orElseThrow().killed());
		w.watch(99, 99, "2x Tiger"); // another root
		assertEquals(StackWatch.MAX_UNHIT_DROP, w.onName(20, "0x Wolf").orElseThrow().killed());
	}

	@Test void growthRenameOrUnwatchedIsNotAKill() {
		StackWatch w = new StackWatch();
		w.watch(10, 10, "Tiger x2");
		assertEquals(0, w.onName(10, "Tiger x3").orElseThrow().killed()); // merged
		assertEquals(0, w.onName(10, "2x Panther").orElseThrow().killed()); // another name
		assertEquals(Optional.empty(), w.onName(11, "1x Tiger"));
	}

	@Test void passengerNamesReportTheirRoot() {
		StackWatch w = new StackWatch();
		w.watch(20, 10, "3x Tiger");
		assertEquals(10, w.onName(20, "2x Tiger").orElseThrow().rootId());
		w.forget(10); // the root died: its passengers go too
		assertFalse(w.watching(20));
	}

	@Test void boundedAndPrunedByRoot() {
		StackWatch w = new StackWatch();
		for (int i = 0; i < StackWatch.MAX + 10; i++) w.watch(i, i, "Tiger");
		assertEquals(StackWatch.MAX, w.size());
		w.retainRoots(id -> id == 100);
		assertEquals(1, w.size());
		assertTrue(w.watching(100));
	}

	@Test void deathVerdicts() {
		KillAttribution k = new KillAttribution();
		assertEquals(KillAttribution.Verdict.NO_HIT, k.onDeathVerdict(1, 7, 10));
		k.onDamage(1, 7, 10);
		assertTrue(k.hitBy(1, 7, 50));
		assertFalse(k.hitBy(1, 8, 50));
		assertEquals(KillAttribution.Verdict.EXPIRED, k.onDeathVerdict(1, 7, 200));
		k.onDamage(1, 8, 10);
		assertEquals(KillAttribution.Verdict.OTHER_PLAYER, k.onDeathVerdict(1, 7, 20));
		k.onDamage(1, 7, 10);
		assertEquals(KillAttribution.Verdict.LOCAL, k.onDeathVerdict(1, 7, 20));
		assertEquals(KillAttribution.Verdict.NO_HIT, k.onDeathVerdict(1, 7, 20)); // consumed
	}
}
