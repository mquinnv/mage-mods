package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.tracker.local.CounterRule.AnyWorld;
import net.mage.cubewheel.tracker.local.CounterRule.Kind;
import net.mage.cubewheel.tracker.local.CounterRule.Named;
import net.mage.cubewheel.tracker.local.CounterRule.NamedWorld;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * "Catch" objectives on ManaCube custom entities (real capture, Tangleroot): a firefly is a
 * minecraft:interaction hitbox riding an area_effect_cloud. The player hits (or uses a Firefly Bottle on)
 * it, the action bar says "+1  Sad Firefly" and only then, ~0.5-1 s later, the server removes the entity.
 */
class CatchRemovalTest {
	static final List<String> WORLDS = KillRuleTest.WORLDS;
	static final WorldInfo TANGLEROOT = RemovalKillTest.TANGLEROOT;

	static CounterRule fireflies() {
		return ObjectiveParser.parse(new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Catch 0/61 Tangleroots Fireflies", null)),
				false, false), WORLDS).orElseThrow();
	}

	static RemovalKills.Removed interaction(int id) {
		return new RemovalKills.Removed(id, "minecraft:interaction", "Interaction", TANGLEROOT);
	}

	// ---- parsing ----

	@Test void catchOfANonFishIsAHitAndRemoveEntityRule() {
		assertEquals(new CounterRule(Kind.KILL, 61, new Named("firefly"), new NamedWorld("tangleroot")), fireflies());
		assertEquals(Optional.of(new CounterRule(Kind.KILL, 10, new Named("butterfly"), new AnyWorld())),
				ObjectiveParserTest.parse("Catch 10 Butterflies"));
	}

	@Test void catchOfFishStaysTheFishingPath() {
		assertEquals(Optional.of(new CounterRule(Kind.FISH, 25, new CounterRule.Any(), new AnyWorld())), ObjectiveParserTest.parse("Catch 25 Fish"));
		for (String s : List.of("Catch 5 Angelfish", "Catch 5 Cod", "Catch 5 Salmon", "Catch 5 Tropical Fish", "Catch 5 Pufferfish")) {
			assertTrue(ObjectiveParserTest.parse(s).isEmpty(), s); // a specific fish: not counted locally
		}
	}

	// ---- loot matching ----

	@Test void lootNamesTheTargetAnywhereOnWordBoundaries() {
		Map<String, CounterRule> rules = Map.of("jobs:Farming Experienced", fireflies(), "jobs:Hunting", KillRuleTest.tigers());
		assertEquals(Optional.of(new LootMatch.Credit("firefly", List.of("jobs:Farming Experienced"))),
				LootMatch.match(LootLine.items("+1  Sad Firefly"), rules, TANGLEROOT));
		assertEquals(Optional.of(new LootMatch.Credit("tiger", List.of("jobs:Hunting"))),
				LootMatch.match(LootLine.items("+5 Mana | +2  Tiger Hide"), rules, TANGLEROOT));
		assertEquals(Optional.of(new LootMatch.Credit("firefly", List.of("jobs:Farming Experienced"))),
				LootMatch.match(List.of("Glowing Fireflies Jar"), rules, TANGLEROOT));
		// Not a word boundary: "Fireflyish" is not "firefly".
		assertEquals(Optional.empty(), LootMatch.match(List.of("Fireflyish Dust"), rules, TANGLEROOT));
	}

	@Test void manaIsNeverLoot() {
		Map<String, CounterRule> rules = Map.of("a", new CounterRule(Kind.KILL, 5, new Named("mana"), new AnyWorld()));
		assertEquals(Optional.empty(), LootMatch.match(LootLine.items("+5 Mana"), rules, TANGLEROOT));
	}

	@Test void twoTargetsInOneLootItemCreditNothing() {
		Map<String, CounterRule> rules = Map.of("a", fireflies(), "b", new CounterRule(Kind.KILL, 5, new Named("sad firefly"), new AnyWorld()));
		assertEquals(Optional.empty(), LootMatch.match(List.of("Sad Firefly"), rules, TANGLEROOT));
	}

	// ---- loot timing: the loot line comes before the removal ----

	@Test void realFireflyTimeline() {
		RemovalKills r = new RemovalKills();
		r.hit(117084076, 14_000); // attack on the interaction entity
		assertEquals(List.of(), r.onActionBar("+1  Sad Firefly", 14_400)); // nothing pending yet
		assertTrue(r.claim(117084076, true, true));
		Optional<List<String>> loot = r.awaitLoot(interaction(117084076), 15_200); // removal ~0.8 s later
		assertEquals(Optional.of(List.of("Sad Firefly")), loot);
		assertEquals(Optional.of(new LootMatch.Credit("firefly", List.of("jobs:Farming Experienced"))),
				LootMatch.match(loot.orElseThrow(), Map.of("jobs:Farming Experienced", fireflies()), TANGLEROOT));
	}

	@Test void lootBeforeTheHitIsNotThisEntitysLoot() {
		RemovalKills r = new RemovalKills();
		r.onActionBar("+1  Sad Firefly", 13_900);
		r.hit(5, 14_000);
		assertEquals(Optional.empty(), r.awaitLoot(interaction(5), 15_000));
	}

	@Test void lootMoreThanThreeSecondsBeforeTheRemovalIsTooOld() {
		RemovalKills r = new RemovalKills();
		r.hit(5, 10_000);
		r.onActionBar("+1  Sad Firefly", 11_000);
		assertEquals(Optional.empty(), r.awaitLoot(interaction(5), 11_001 + RemovalKills.LOOT_BEFORE_MS));
		RemovalKills r2 = new RemovalKills();
		r2.hit(5, 10_000);
		r2.onActionBar("+1  Sad Firefly", 11_000);
		assertEquals(Optional.of(List.of("Sad Firefly")), r2.awaitLoot(interaction(5), 11_000 + RemovalKills.LOOT_BEFORE_MS));
	}

	@Test void aLootLineNamesOneEarlierRemovalOnly() {
		RemovalKills r = new RemovalKills();
		r.hit(1, 1_000);
		r.hit(2, 1_000);
		r.onActionBar("+1  Sad Firefly", 1_200);
		assertEquals(Optional.of(List.of("Sad Firefly")), r.awaitLoot(interaction(1), 1_500));
		assertEquals(Optional.empty(), r.awaitLoot(interaction(2), 1_600)); // waits for its own line
		assertEquals(1, r.onActionBar("+1  Sad Firefly", 1_900).size());
	}

	@Test void theFirstHitStartsTheWindow() {
		// Hitting again after the loot appeared must not hide that loot.
		RemovalKills r = new RemovalKills();
		r.hit(3, 1_000);
		r.onActionBar("+1  Sad Firefly", 1_400);
		r.hit(3, 1_500);
		assertEquals(Optional.of(List.of("Sad Firefly")), r.awaitLoot(interaction(3), 1_800));
	}

	// ---- Firefly Bottle ----

	@Test void fireflyBottleIsFallbackLoot() {
		assertEquals("Firefly", RemovalKills.bottleLoot("Bottomless Firefly Bottle"));
		assertEquals("Firefly", RemovalKills.bottleLoot("§6Firefly Bottle (3 uses)"));
		assertNull(RemovalKills.bottleLoot("Glass Bottle"));
		assertNull(RemovalKills.bottleLoot(null));
		RemovalKills r = new RemovalKills();
		r.hit(9, 1_000);
		r.fallback(9, "Firefly");
		assertEquals(Optional.of("Firefly"), r.fallback(9));
		assertEquals(Optional.empty(), r.fallback(10));
		// Exactly one "catch fireflies" rule in this world: credited; the one-target rule still applies.
		Map<String, CounterRule> rules = Map.of("jobs:Farming Experienced", fireflies(), "jobs:Hunting", KillRuleTest.tigers());
		assertEquals(Optional.of(new LootMatch.Credit("firefly", List.of("jobs:Farming Experienced"))),
				LootMatch.match(List.of("Firefly"), rules, TANGLEROOT));
		WorldInfo sandara = WorldResolver.resolve("minecraft:sandara", KillRuleTest.SIDEBAR, WORLDS);
		assertEquals(Optional.empty(), LootMatch.match(List.of("Firefly"), rules, sandara));
		r.clear();
		assertEquals(Optional.empty(), r.fallback(9));
	}
}
