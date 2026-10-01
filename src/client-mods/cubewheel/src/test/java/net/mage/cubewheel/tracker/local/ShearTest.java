package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.tracker.local.CounterRule.AnyWorld;
import net.mage.cubewheel.tracker.local.CounterRule.Kind;
import net.mage.cubewheel.tracker.local.CounterRule.Named;
import net.mage.cubewheel.tracker.local.CounterRule.NamedWorld;
import net.mage.cubewheel.tracker.local.PendingShears.Outcome;
import net.mage.cubewheel.tracker.local.PendingShears.Result;
import net.mage.cubewheel.tracker.local.PendingShears.State;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** "Shear N Sheep": parsing, matching, held-tool detection and the pending-confirmation map. */
class ShearTest {
	static final WorldInfo OVERWORLD = new WorldInfo(Set.of("overworld"), false, true);
	static final WorldInfo WOLFHAVEN = new WorldInfo(Set.of("wolfhaven"), false, true);

	// ---- parsing ----

	@Test void jobListingCounterFormParses() {
		assertEquals(Optional.of(new CounterRule(Kind.SHEAR, 84, new Named("sheep"), new AnyWorld())),
				ObjectiveParserTest.parse("Shear 10/84 Sheep"));
		assertEquals(Optional.of(new CounterRule(Kind.SHEAR, 1000, new Named("sheep"), new AnyWorld())),
				ObjectiveParserTest.parse("Shear 1,000 Sheep"));
	}

	@Test void otherShearablesAndWorldsParseGenerically() {
		assertEquals(Optional.of(new CounterRule(Kind.SHEAR, 20, new Named("mooshroom"), new AnyWorld())),
				ObjectiveParserTest.parse("Shear 20 Mooshrooms"));
		assertEquals(Optional.of(new CounterRule(Kind.SHEAR, 50, new Named("sheep"), new NamedWorld("wolfhaven"))),
				ObjectiveParserTest.parse("Shear 50 Wolfhaven Sheep"));
		assertEquals(Optional.of(new CounterRule(Kind.SHEAR, 50, new Named("sheep"), new NamedWorld("wolfhaven"))),
				ObjectiveParserTest.parse("Shear 50 Sheep in Wolfhaven"));
	}

	@Test void extractorFindsShearLinesAndStoresListingObjectivesWhateverTheVerb() {
		List<String> lore = List.of("Shear 10/84 Sheep", "", "Hand In:", "", "Reward", "- 40 Job XP", "", "㎋ Click to complete job");
		assertEquals(List.of(new ObjectiveInfo.Sub("Shear 10/84 Sheep", null)),
				ObjectiveExtractor.extract("Experienced Objective", lore).subs());
		// An unknown verb on a listing is still stored (it just gets no rule).
		List<String> odd = List.of("Tame 3/40 Wolves", "", "㎋ Click to complete job");
		assertTrue(ObjectiveExtractor.extract("Beginner Objective", odd).subs().isEmpty());
		assertEquals(List.of(new ObjectiveInfo.Sub("Tame 3/40 Wolves", null)),
				ObjectiveExtractor.extract("Beginner Objective", odd, "Tame 3/40 Wolves").subs());
		// Something found in the lore wins over the fallback.
		assertEquals("Shear 10/84 Sheep", ObjectiveExtractor.extract("Experienced Objective", lore, "x").subs().get(0).text());
	}

	// ---- matching ----

	@Test void shearSignalMatchesOnlyShearRulesOfThatMob() {
		CounterRule sheep = new CounterRule(Kind.SHEAR, 84, new Named("sheep"), new AnyWorld());
		assertTrue(RuleMatcher.matches(sheep, new Signal.Sheared("minecraft:sheep", "Sheep", OVERWORLD)));
		assertTrue(RuleMatcher.matches(sheep, new Signal.Sheared("minecraft:sheep", "jeb_", OVERWORLD))); // by type id
		assertFalse(RuleMatcher.matches(sheep, new Signal.Sheared("minecraft:mooshroom", "Mooshroom", OVERWORLD)));
		assertTrue(RuleMatcher.matches(new CounterRule(Kind.SHEAR, 5, new Named("mooshroom"), new AnyWorld()),
				new Signal.Sheared("minecraft:mooshroom", "Mooshroom", OVERWORLD)));
		// Killing a sheep is not shearing it, and shearing is not killing.
		assertFalse(RuleMatcher.matches(sheep, new Signal.MobKilled("minecraft:sheep", "Sheep", Set.of("mob"), 1, OVERWORLD)));
		assertFalse(RuleMatcher.matches(new CounterRule(Kind.KILL, 5, new Named("sheep"), new AnyWorld()),
				new Signal.Sheared("minecraft:sheep", "Sheep", OVERWORLD)));
	}

	@Test void worldScopedShearRuleNeedsThatWorld() {
		CounterRule rule = new CounterRule(Kind.SHEAR, 50, new Named("sheep"), new NamedWorld("wolfhaven"));
		assertFalse(RuleMatcher.matches(rule, new Signal.Sheared("minecraft:sheep", "Sheep", OVERWORLD)));
		assertTrue(RuleMatcher.matches(rule, new Signal.Sheared("minecraft:sheep", "Sheep", WOLFHAVEN)));
	}

	// ---- held tool ----

	@Test void shearsAreVanillaOrNamedOrLoredShears() {
		assertTrue(ShearTool.isShears(true, "minecraft:paper", "Anything", List.of()));
		assertTrue(ShearTool.isShears(false, "minecraft:shears", "Shears", null));
		assertTrue(ShearTool.isShears(false, "minecraft:iron_hoe", "Golden Shears ✦", List.of()));
		assertTrue(ShearTool.isShears(false, "minecraft:iron_hoe", "Wool Ripper", List.of("Legendary SHEARS", "Area: 3x3")));
		assertFalse(ShearTool.isShears(false, "minecraft:diamond_sword", "Mana Blade", List.of("Deals damage")));
		assertFalse(ShearTool.isShears(false, null, null, null));
	}

	// ---- pending confirmation ----

	@Test void targetConfirmedWhenItsFlagFlipsWithinWindow() {
		PendingShears p = new PendingShears();
		Map<Integer, State> world = new HashMap<>(Map.of(7, State.NOT_YET));
		p.use(7, List.of(), 100);
		assertEquals(List.of(), p.tick(101, world::get));
		world.put(7, State.SHEARED);
		assertEquals(List.of(new Outcome(7, Result.CONFIRMED, false)), p.tick(103, world::get));
		assertTrue(p.isEmpty());
		assertEquals(List.of(), p.tick(104, world::get)); // counted once
	}

	@Test void targetNotCountedWhenRemovedOrWindowPasses() {
		PendingShears p = new PendingShears();
		p.use(1, List.of(), 0);
		p.use(2, List.of(), 0);
		Map<Integer, State> world = new HashMap<>(Map.of(1, State.GONE, 2, State.NOT_YET));
		assertEquals(List.of(new Outcome(1, Result.GONE, false)), p.tick(1, world::get));
		assertEquals(List.of(), p.tick(PendingShears.TARGET_TICKS - 1, world::get));
		assertEquals(List.of(new Outcome(2, Result.EXPIRED, false)), p.tick(PendingShears.TARGET_TICKS, world::get));
		// Sheared after its window: not ours (maybe another player's).
		world.put(2, State.SHEARED);
		assertEquals(List.of(), p.tick(PendingShears.TARGET_TICKS + 1, world::get));
		// Unknown ids (e.g. an entity the client dropped) read as gone.
		p.use(3, List.of(), 50);
		assertEquals(List.of(new Outcome(3, Result.GONE, false)), p.tick(51, id -> null));
	}

	@Test void areaSnapshotConfirmsOnlyItsIdsWithinTheShorterWindow() {
		PendingShears p = new PendingShears();
		p.use(10, List.of(11, 12, 10), 0);
		assertEquals(3, p.size());
		Map<Integer, State> world = new HashMap<>(Map.of(10, State.SHEARED, 11, State.SHEARED, 12, State.NOT_YET,
				99, State.SHEARED)); // 99: sheared by someone else, never in our snapshot
		assertEquals(List.of(new Outcome(10, Result.CONFIRMED, false), new Outcome(11, Result.CONFIRMED, true)),
				p.tick(2, world::get));
		assertEquals(List.of(new Outcome(12, Result.EXPIRED, true)), p.tick(PendingShears.AREA_TICKS, world::get));
		assertTrue(p.isEmpty());
	}

	@Test void targetKeepsItsLongerWindowWhenAlsoNearAnotherUse() {
		PendingShears p = new PendingShears();
		p.use(1, List.of(), 0);
		p.use(2, List.of(1), 5); // 1 is still pending as a target
		Map<Integer, State> world = Map.of(1, State.NOT_YET, 2, State.NOT_YET);
		assertEquals(List.of(new Outcome(2, Result.EXPIRED, false)),
				p.tick(5 + PendingShears.TARGET_TICKS, world::get).stream().filter(o -> o.entityId() == 2).toList());
		PendingShears q = new PendingShears();
		q.use(1, List.of(), 0);
		q.use(2, List.of(1), 5);
		assertEquals(List.of(), q.tick(5 + PendingShears.AREA_TICKS, world::get)); // 1 not dropped at the area deadline
		assertEquals(List.of(new Outcome(1, Result.EXPIRED, false)), q.tick(PendingShears.TARGET_TICKS, world::get));
	}

	@Test void boundedAndClearable() {
		PendingShears p = new PendingShears();
		for (int i = 0; i < PendingShears.CAPACITY + 10; i++) p.use(i, List.of(), 0);
		assertEquals(PendingShears.CAPACITY, p.size());
		assertFalse(p.pending(0)); // oldest dropped
		assertTrue(p.pending(PendingShears.CAPACITY + 9));
		p.clear();
		assertTrue(p.isEmpty());
	}
}
