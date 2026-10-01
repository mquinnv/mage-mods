package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.tracker.local.CounterRule.AnyWorld;
import net.mage.cubewheel.tracker.local.CounterRule.Kind;
import net.mage.cubewheel.tracker.local.CounterRule.Named;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** "Milk N Cows": parsing, matching and confirming a bucket use. */
class MilkTest {
	static final WorldInfo OVERWORLD = new WorldInfo(Set.of("overworld"), false, true);

	@Test void jobListingParses() {
		assertEquals(Optional.of(new CounterRule(Kind.MILK, 25, new Named("cow"), new AnyWorld())),
				ObjectiveParserTest.parse("Milk 15/25 Cows"));
		assertEquals(List.of(new ObjectiveInfo.Sub("Milk 3/40 Cows", null)),
				ObjectiveExtractor.extract("Beginner Objective", List.of("Milk 3/40 Cows", "", "㎋ Click to complete job")).subs());
	}

	@Test void matchesCowsOnly() {
		CounterRule cows = new CounterRule(Kind.MILK, 25, new Named("cow"), new AnyWorld());
		assertTrue(RuleMatcher.matches(cows, new Signal.Milked("minecraft:cow", "Cow", OVERWORLD)));
		assertFalse(RuleMatcher.matches(cows, new Signal.Milked("minecraft:goat", "Goat", OVERWORLD)));
		assertFalse(RuleMatcher.matches(cows, new Signal.Sheared("minecraft:cow", "Cow", OVERWORLD)));
		assertFalse(RuleMatcher.matches(new CounterRule(Kind.KILL, 5, new Named("cow"), new AnyWorld()),
				new Signal.Milked("minecraft:cow", "Cow", OVERWORLD)));
	}

	@Test void aUseCountsOnlyIfTheBucketStaysGone() {
		PendingMilk p = new PendingMilk();
		p.use(100, 5, "minecraft:cow", "Cow");
		assertTrue(p.tick(110, 4).isEmpty()); // still waiting
		assertEquals(List.of(new PendingMilk.Outcome("minecraft:cow", "Cow", true)), p.tick(120, 4));
		assertTrue(p.isEmpty());

		p.use(200, 4, "minecraft:cow", "Cow"); // refused: the server put the bucket back
		assertEquals(List.of(new PendingMilk.Outcome("minecraft:cow", "Cow", false)), p.tick(220, 4));
	}

	@Test void quickSuccessiveMilkingsEachCount() {
		PendingMilk p = new PendingMilk();
		p.use(100, 5, "minecraft:cow", "Cow");
		p.use(105, 4, "minecraft:cow", "Cow");
		p.use(110, 3, "minecraft:cow", "Cow");
		assertEquals(3, p.tick(130, 2).stream().filter(PendingMilk.Outcome::confirmed).count());
	}

	@Test void boundedCapacity() {
		PendingMilk p = new PendingMilk();
		for (int i = 0; i < PendingMilk.CAPACITY + 5; i++) p.use(i, 100, "minecraft:cow", "Cow");
		assertEquals(PendingMilk.CAPACITY, p.tick(1000, 0).size());
	}
}
