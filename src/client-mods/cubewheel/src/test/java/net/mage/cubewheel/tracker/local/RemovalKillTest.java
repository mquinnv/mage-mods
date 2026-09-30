package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.tracker.ProgressExtractor.Progress;
import net.mage.cubewheel.tracker.TrackerStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ManaCube custom-model mobs (real capture, Tangleroot): a tiger is an unnamed "minecraft:slime" hitbox that
 * is removed without a death event; the kill shows as loot on the action bar ("+5 Mana | +2  Tiger Hide").
 */
class RemovalKillTest {
	@TempDir Path dir;
	static final List<String> WORLDS = KillRuleTest.WORLDS;
	static final WorldInfo TANGLEROOT = WorldResolver.resolve("minecraft:tangleroot", KillRuleTest.SIDEBAR, WORLDS);

	// ---- loot lines ----

	@Test void lootItemsFromRealActionBars() {
		assertEquals(List.of("Mana", "Tiger Hide"), LootLine.items("+5 Mana | +3  Tiger Hide"));
		assertEquals(List.of("Mana", "Tiger Hide"), LootLine.items("SAMURAI KATANA » Primed 2/4 | +5 Mana | +2  Tiger Hide"));
		assertEquals(List.of("Frog Leg"), LootLine.items("+2  Frog Leg"));
		assertEquals(List.of("Tiger Claw"), LootLine.items("§a+1 §fTiger Claw"));
		assertEquals(List.of("Coin", "Tiger Hide"), LootLine.items("+1,250 Coin +2 Tiger Hide"));
	}

	@Test void noLootInOtherActionBars() {
		assertEquals(List.of(), LootLine.items("SAMURAI KATANA » Primed 2/4"));
		assertEquals(List.of(), LootLine.items("Health 20/20 +5%"));
		assertEquals(List.of(), LootLine.items(""));
		assertEquals(List.of(), LootLine.items(null));
	}

	// ---- loot -> objective ----

	static CounterRule kill(String target, CounterRule.World w) {
		return new CounterRule(CounterRule.Kind.KILL, 64, new CounterRule.Named(target), w);
	}

	@Test void lootPrefixCreditsTheOneMatchingKillRule() {
		Map<String, CounterRule> rules = Map.of(
				"jobs:tigers", KillRuleTest.tigers(),
				"pq:frogs", kill("frog", new CounterRule.AnyWorld()),
				"pq:monsters", new CounterRule(CounterRule.Kind.KILL, 100, new CounterRule.Group("monster"), new CounterRule.AnyWorld()),
				"pq:stone", new CounterRule(CounterRule.Kind.BREAK, 10, new CounterRule.Named("stone"), new CounterRule.AnyWorld()));
		assertEquals(Optional.of(new LootMatch.Credit("tiger", List.of("jobs:tigers"))),
				LootMatch.match(List.of("Mana", "Tiger Hide"), rules, TANGLEROOT));
		assertEquals(Optional.of(new LootMatch.Credit("frog", List.of("pq:frogs"))),
				LootMatch.match(List.of("Frog Legs"), rules, TANGLEROOT));
		assertEquals(Optional.empty(), LootMatch.match(List.of("Mana"), rules, TANGLEROOT));
		// "Tigerlily Seed" is not the word "tiger"
		assertEquals(Optional.empty(), LootMatch.match(List.of("Tigerlily Seed"), rules, TANGLEROOT));
	}

	@Test void lootRespectsWorldAndAmbiguity() {
		Map<String, CounterRule> rules = Map.of("jobs:tigers", KillRuleTest.tigers());
		WorldInfo sandara = WorldResolver.resolve("minecraft:sandara", KillRuleTest.SIDEBAR, WORLDS);
		assertEquals(Optional.empty(), LootMatch.match(List.of("Tiger Hide"), rules, sandara));
		// Two different targets fit ("tiger" and "tiger hide"): ambiguous, credit nothing.
		Map<String, CounterRule> two = Map.of("a", kill("tiger", new CounterRule.AnyWorld()), "b", kill("tiger hide", new CounterRule.AnyWorld()));
		assertEquals(Optional.empty(), LootMatch.match(List.of("Tiger Hide"), two, TANGLEROOT));
		// Several rules with the same target (a job and a quest) are all credited.
		Map<String, CounterRule> same = Map.of("a", kill("tiger", new CounterRule.AnyWorld()), "b", KillRuleTest.tigers());
		assertEquals(Optional.of(new LootMatch.Credit("tiger", List.of("a", "b"))), LootMatch.match(List.of("Tiger Hide"), same, TANGLEROOT));
	}

	@Test void creditAddsOneUnitToEachId() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Hunting", new Progress(16, 64), 0);
		s.setObjective("jobs:Hunting", new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Slay 16/64 Tigers in Tangleroots", null)), false, false));
		List<LocalCounter.Contribution> c = LocalCounter.credit(List.of("jobs:Hunting", "missing"), s, 5);
		assertEquals(List.of(new LocalCounter.Contribution("jobs:Hunting", 1)), c);
		assertTrue(LocalCounter.credit(List.of(), s, 5).isEmpty());
	}

	// ---- name hints ----

	static NameResolver.Candidate c(NameResolver.Relation r, String name, double dist) {
		return new NameResolver.Candidate(r, name, dist * dist, "x");
	}

	@Test void ridersBeatTagsBeatOtherNamedEntities() {
		var rider = c(NameResolver.Relation.RIDER, "Tiger", 2);
		var tag = c(NameResolver.Relation.TAG, "Tiger 20⺛", 0.5);
		var other = c(NameResolver.Relation.OTHER, "Dart Frog", 0.2);
		assertEquals(Optional.of(rider), NameResolver.pick(List.of(other, tag, rider)));
		assertEquals(Optional.of(tag), NameResolver.pick(List.of(other, tag)));
		assertEquals(Optional.of(other), NameResolver.pick(List.of(other)));
		var nearTag = c(NameResolver.Relation.TAG, "Tiger", 1);
		var farTag = c(NameResolver.Relation.TAG, "Mana Wolf", 2.5);
		assertEquals(Optional.of(nearTag), NameResolver.pick(List.of(farTag, nearTag)));
	}

	@Test void tagsBeyondThreeBlocksAndBlankNamesAreIgnored() {
		assertEquals(Optional.empty(), NameResolver.pick(List.of(c(NameResolver.Relation.TAG, "Tiger", 3.5))));
		assertEquals(Optional.empty(), NameResolver.pick(List.of(c(NameResolver.Relation.TAG, "  ", 1))));
		// Riders/vehicles are linked, not "nearby": their distance does not matter.
		assertTrue(NameResolver.pick(List.of(c(NameResolver.Relation.VEHICLE, "Tiger", 5))).isPresent());
	}

	@Test void firstNonBlankLineOfATextDisplay() {
		assertEquals("Tiger", NameResolver.firstLine("\n  Tiger \n❤ 20/20"));
		assertEquals(null, NameResolver.firstLine(" \n "));
		assertEquals(null, NameResolver.firstLine(null));
	}

	// ---- removal as kill ----

	static RemovalKills.Removed removed(int id) {
		return new RemovalKills.Removed(id, "minecraft:slime", "Slime", TANGLEROOT);
	}

	@Test void removalCountsOnceForARecentNearbyLocalHit() {
		RemovalKills r = new RemovalKills();
		assertTrue(r.claim(7, true, true));
		assertFalse(r.claim(7, true, true)); // never twice
		assertFalse(r.claim(8, false, true)); // no hit within 30 ticks
		assertFalse(r.claim(9, true, false)); // far away: left view, not a kill
	}

	@Test void deathOrStackCountedIdsNeverCountAgainOnRemoval() {
		RemovalKills r = new RemovalKills();
		r.settle(3);
		assertFalse(r.claim(3, true, true));
	}

	@Test void hitWindowIsThirtyTicks() {
		KillAttribution k = new KillAttribution();
		k.onDamage(5, 1, 100);
		assertTrue(k.hitByWithin(5, 1, 130, RemovalKills.WINDOW_TICKS));
		assertFalse(k.hitByWithin(5, 1, 131, RemovalKills.WINDOW_TICKS));
		assertFalse(k.hitByWithin(5, 2, 110, RemovalKills.WINDOW_TICKS));
		assertFalse(k.hitByWithin(6, 1, 110, RemovalKills.WINDOW_TICKS));
	}

	@Test void hintsAreRememberedPerHitEntity() {
		RemovalKills r = new RemovalKills();
		assertFalse(r.seen(1));
		r.remember(1, null);
		assertTrue(r.seen(1));
		assertEquals(Optional.empty(), r.hint(1));
		r.remember(2, new RemovalKills.Hint("Tiger", RemovalKills.Method.LINKED, "text_display #9"));
		assertEquals("Tiger", r.hint(2).orElseThrow().name());
		for (int i = 100; i < 100 + RemovalKills.MAX_HINTS; i++) r.remember(i, null);
		assertFalse(r.seen(1)); // bounded, oldest first
	}

	@Test void lootAfterRemovalResolvesPendingKills() {
		RemovalKills r = new RemovalKills();
		assertEquals(Optional.empty(), r.awaitLoot(removed(1), 1_000));
		assertEquals(Optional.empty(), r.awaitLoot(removed(2), 1_050));
		assertEquals(List.of(), r.onActionBar("SAMURAI KATANA » Primed 2/4", 1_100)); // no loot entry yet
		List<RemovalKills.Resolved> got = r.onActionBar("+5 Mana | +2  Tiger Hide", 1_400);
		assertEquals(2, got.size());
		assertEquals(List.of("Mana", "Tiger Hide"), got.get(0).loot());
		assertEquals(1, got.get(0).removed().entityId());
		assertEquals(List.of(), r.expire(10_000));
	}

	@Test void lootJustBeforeTheRemovalPacketIsUsed() {
		// The loot action bar and the removal can arrive in the same tick in either order.
		RemovalKills r = new RemovalKills();
		r.onActionBar("+5 Mana | +3  Tiger Hide", 1_000);
		assertEquals(Optional.of(List.of("Mana", "Tiger Hide")), r.awaitLoot(removed(1), 1_250));
		// That loot line is used up by the first kill: wait for the next message instead.
		assertEquals(Optional.empty(), r.awaitLoot(removed(2), 2_000));
	}

	@Test void noLootWithinWindowIsUnattributed() {
		RemovalKills r = new RemovalKills();
		r.awaitLoot(removed(1), 1_000);
		assertEquals(List.of(), r.expire(1_000 + RemovalKills.LOOT_WAIT_MS));
		List<RemovalKills.Removed> gone = r.expire(1_001 + RemovalKills.LOOT_WAIT_MS);
		assertEquals(List.of(removed(1)), gone);
		assertEquals(List.of(), r.onActionBar("+2 Tiger Hide", 2_600)); // too late: nothing pending
	}

	@Test void pendingIsBounded() {
		RemovalKills r = new RemovalKills();
		for (int i = 0; i < RemovalKills.MAX_PENDING + 5; i++) r.awaitLoot(removed(i), 1_000);
		assertEquals(RemovalKills.MAX_PENDING, r.onActionBar("+1 Tiger Hide", 1_100).size());
	}

	@Test void clearForgetsEverything() {
		RemovalKills r = new RemovalKills();
		r.settle(1);
		r.remember(2, null);
		r.awaitLoot(removed(3), 0);
		r.clear();
		assertTrue(r.claim(1, true, true));
		assertFalse(r.seen(2));
		assertEquals(List.of(), r.onActionBar("+1 Tiger Hide", 10));
	}

	@Test void tigerEndToEnd() {
		// The loot match feeds the credited rule ids; no id of the hitbox ("slime") is used.
		Map<String, CounterRule> rules = Map.of("jobs:Hunting", KillRuleTest.tigers(),
				"pq:slimes", kill("slime", new CounterRule.AnyWorld()));
		RemovalKills r = new RemovalKills();
		assertTrue(r.claim(42, true, true));
		assertEquals(Optional.empty(), r.hint(42));
		r.awaitLoot(removed(42), 5_000);
		RemovalKills.Resolved res = r.onActionBar("+5 Mana | +2  Tiger Hide", 5_300).get(0);
		assertEquals(Optional.of(new LootMatch.Credit("tiger", List.of("jobs:Hunting"))), LootMatch.match(res.loot(), rules, res.removed().world()));
	}
}
