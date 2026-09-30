package com.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** PendingBreaks, PlacedBlocks, KillAttribution, FishDetector and StackName. */
class AttributionTest {
	static final Pos P = new Pos(1, 64, -3);
	static final List<LocalCounter.Contribution> C = List.of(new LocalCounter.Contribution("a", 1), new LocalCounter.Contribution("b", 1));
	static final int ME = 7;
	static final int OTHER = 8;

	// --- PendingBreaks ---

	@Test void rejectionWithTheSameStateReversesExactlyTheContributions() {
		PendingBreaks p = new PendingBreaks();
		p.record(P, 42, C, 100);
		assertEquals(Optional.of(C), p.onSync(P, 42));
		assertEquals(Optional.empty(), p.onSync(P, 42)); // consumed
	}

	@Test void replantOrAirIsNotARejection() {
		PendingBreaks p = new PendingBreaks();
		p.record(P, 42, C, 100);
		assertEquals(Optional.empty(), p.onSync(P, 35)); // age 0 crop
		assertTrue(p.isEmpty());
		p.record(P, 42, C, 100);
		assertEquals(Optional.empty(), p.onSync(new Pos(0, 0, 0), 42)); // another position
		assertFalse(p.isEmpty());
	}

	@Test void pendingBreaksExpireAndAreBounded() {
		PendingBreaks p = new PendingBreaks();
		p.record(P, 42, C, 100);
		p.expire(140);
		assertFalse(p.isEmpty());
		p.expire(141);
		assertTrue(p.isEmpty());
		for (int i = 0; i < 100; i++) p.record(new Pos(i, 0, 0), 1, C, 100);
		assertEquals(PendingBreaks.MAX, p.size());
	}

	// --- PlacedBlocks ---

	@Test void placedBlocksAreExcludedOnlyInTheSameState() {
		PlacedBlocks b = new PlacedBlocks(4);
		b.placed(P, 10);
		assertTrue(b.consumeIfPlaced(P, 10));
		assertFalse(b.consumeIfPlaced(P, 10)); // consumed
		b.placed(P, 10); // planted seeds (age 0)...
		assertFalse(b.consumeIfPlaced(P, 17)); // ...grown into a different state: counts
	}

	@Test void placedBlocksEvictTheOldest() {
		PlacedBlocks b = new PlacedBlocks(2);
		b.placed(new Pos(1, 0, 0), 1);
		b.placed(new Pos(2, 0, 0), 1);
		b.placed(new Pos(3, 0, 0), 1);
		assertFalse(b.consumeIfPlaced(new Pos(1, 0, 0), 1));
		assertTrue(b.consumeIfPlaced(new Pos(3, 0, 0), 1));
	}

	// --- KillAttribution ---

	@Test void ownDamageThenDeathWithin100Ticks() {
		KillAttribution k = new KillAttribution();
		k.onDamage(1, ME, 0);
		assertTrue(k.onDeath(1, ME, 100));
		assertFalse(k.onDeath(1, ME, 100)); // counted once
		k.onDamage(2, ME, 0);
		assertFalse(k.onDeath(2, ME, 101));
	}

	@Test void anotherPlayersLaterHitTakesTheKill() {
		KillAttribution k = new KillAttribution();
		k.onDamage(1, ME, 0);
		k.onDamage(1, OTHER, 5);
		assertFalse(k.onDeath(1, ME, 10));
		k.onDamage(2, OTHER, 0);
		k.onDamage(2, ME, 5);
		assertTrue(k.onDeath(2, ME, 10));
	}

	@Test void deathWithoutDamageDoesNotCount() {
		assertFalse(new KillAttribution().onDeath(1, ME, 10));
	}

	@Test void attributionIsBoundedAndExpires() {
		KillAttribution k = new KillAttribution();
		for (int i = 0; i < 1000; i++) k.onDamage(i, ME, 0);
		assertEquals(KillAttribution.MAX, k.size());
		assertTrue(k.onDeath(999, ME, 1));
		assertFalse(k.onDeath(0, ME, 1)); // evicted
		k.expire(200);
		assertEquals(0, k.size());
	}

	// --- FishDetector ---

	@Test void fishCountsOnlyWhenBiting() {
		assertTrue(FishDetector.onRodUse(true, true));
		assertFalse(FishDetector.onRodUse(true, false));
		assertFalse(FishDetector.onRodUse(false, true));
	}

	// --- StackName ---

	@Test void stackAndHealthDecorationsAreStripped() {
		assertEquals(Optional.of(new StackName.Parsed(12, "Zombie")), StackName.parse("12x Zombie"));
		assertEquals(Optional.of(new StackName.Parsed(12, "Zombie")), StackName.parse("12 x Zombie"));
		assertEquals(Optional.of(new StackName.Parsed(1, "Mana Wolf")), StackName.parse("Mana Wolf ❤ 20"));
		assertEquals(Optional.of(new StackName.Parsed(3, "Mana Slime")), StackName.parse("§a3x §fMana Slime §c[40/40]"));
		assertEquals(Optional.of(new StackName.Parsed(1, "Zombie")), StackName.parse("Zombie"));
		assertEquals(Optional.empty(), StackName.parse("  "));
		assertEquals(Optional.empty(), StackName.parse(null));
	}
}
