package com.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mage.cubewheel.tracker.local.CounterRule.Any;
import com.mage.cubewheel.tracker.local.CounterRule.AnyWorld;
import com.mage.cubewheel.tracker.local.CounterRule.Group;
import com.mage.cubewheel.tracker.local.CounterRule.Kind;
import com.mage.cubewheel.tracker.local.CounterRule.Named;
import com.mage.cubewheel.tracker.local.CounterRule.NamedWorld;
import com.mage.cubewheel.tracker.local.CounterRule.Special;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ObjectiveParserTest {
	static final List<String> WORLDS = List.of("wolfhaven", "tangleroots", "sandara", "icehaven", "morend", "burninglands");

	static Optional<CounterRule> parse(String line) {
		return parse(line, false);
	}

	static Optional<CounterRule> parse(String line, boolean special) {
		return ObjectiveParser.parse(new ObjectiveInfo(List.of(new ObjectiveInfo.Sub(line, null)), special, false), WORLDS);
	}

	static CounterRule rule(Kind k, long n, CounterRule.Target t, CounterRule.World w) {
		return new CounterRule(k, n, t, w);
	}

	@Test void prestigeObjectives() {
		assertEquals(Optional.of(rule(Kind.HARVEST, 21250, new Group("crop"), new AnyWorld())), parse("Harvest 21,250 Crops"));
		assertEquals(Optional.of(rule(Kind.KILL, 10000, new Group("mob"), new AnyWorld())), parse("Kill 10,000 mobs"));
		assertEquals(Optional.of(rule(Kind.KILL, 1000, new Group("monster"), new Special())), parse("Slay 1,000 Monsters", true));
		assertEquals(Optional.of(rule(Kind.BREAK, 3750, new Any(), new Special())), parse("Harvest 3,750 Resources", true));
		assertEquals(Optional.of(rule(Kind.FISH, 1000, new Any(), new AnyWorld())), parse("Catch 1,000 Fish"));
	}

	@Test void questObjectives() {
		assertEquals(Optional.of(rule(Kind.BREAK, 10000, new Any(), new NamedWorld("wolfhaven"))),
				parse("Harvest or Mine  10,000 Wolfhaven Resources"));
		assertEquals(Optional.of(rule(Kind.KILL, 1000, new Group("monster"), new NamedWorld("sandara"))),
				parse("Slay 1,000 Sandara Monsters"));
		assertEquals(Optional.of(rule(Kind.BREAK, 300, new Named("cobblestone"), new AnyWorld())), parse("Mine 300 Cobblestone"));
		assertEquals(Optional.of(rule(Kind.BREAK, 300, new Named("stone"), new AnyWorld())), parse("Mine 300 Stone"));
		assertEquals(Optional.of(rule(Kind.HARVEST, 64, new Named("wheat"), new AnyWorld())), parse("Harvest 64 Wheat"));
		assertEquals(Optional.of(rule(Kind.FISH, 25, new Any(), new AnyWorld())), parse("Catch 25 Fish"));
		assertEquals(Optional.of(rule(Kind.KILL, 500, new Named("enderman"), new AnyWorld())), parse("Kill 500 Endermen"));
		assertEquals(Optional.of(rule(Kind.KILL, 1000, new Named("mana wolf"), new AnyWorld())), parse("Slay 1,000 Mana Wolves"));
	}

	@Test void inSuffixAndSingularWorldTokens() {
		assertEquals(Optional.of(rule(Kind.KILL, 20, new Named("zombie"), new NamedWorld("tangleroot"))),
				parse("Kill 20 Zombies in Tangleroots"));
		assertEquals(Optional.of(rule(Kind.BREAK, 50, new Group("logs"), new NamedWorld("tangleroot"))),
				parse("Chop 50 Tangleroot Logs"));
		assertEquals(Optional.of(rule(Kind.BREAK, 40, new Group("ore"), new AnyWorld())), parse("Mine 40 Ores"));
	}

	@Test void multiWordAndUnderscoredWorldNames() {
		CounterRule.World burning = new NamedWorld("burningland");
		assertEquals(Optional.of(rule(Kind.BREAK, 500, new Any(), burning)), parse("Mine 500 Burning Lands Resources"));
		assertEquals(Optional.of(rule(Kind.BREAK, 500, new Any(), burning)), parse("Mine 500 Burninglands Resources"));
		assertEquals(Optional.of(rule(Kind.KILL, 20, new Named("zombie"), burning)), parse("Kill 20 Zombies in Burning Lands"));
		assertEquals(Optional.of(rule(Kind.KILL, 20, new Named("zombie"), burning)), parse("Kill 20 Zombies in the burning_lands"));
		// The configured name may itself be spaced or underscored.
		for (String configured : List.of("Burning Lands", "burning_lands", "burninglands")) {
			ObjectiveInfo info = new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Slay 50 Burning Lands Monsters", null)), false, false);
			assertEquals(Optional.of(rule(Kind.KILL, 50, new Group("monster"), burning)),
					ObjectiveParser.parse(info, List.of(configured)), configured);
		}
		// A world name alone is not a noun: "Mine 5 Burning Lands" has no target left after the world.
		assertEquals(Optional.of(rule(Kind.BREAK, 5, new Named("burning land"), new AnyWorld())), parse("Mine 5 Burning Lands"));
	}

	@Test void jobListingCounterBetweenVerbAndTarget() {
		// /jobs listings: "<verb> <cur>/<max> <target>"; the target number is the max.
		assertEquals(Optional.of(rule(Kind.BREAK, 4773, new Named("cherry log"), new AnyWorld())),
				parse("Harvest 3,127/4,773 Cherry Logs"));
		assertEquals(Optional.of(rule(Kind.BREAK, 506, new Named("acacia log"), new AnyWorld())),
				parse("Harvest 0/506 Acacia Logs"));
		// A specific catch in a named world (fireflies are not fish): no rule, as for "Catch 5 Angelfish".
		assertTrue(parse("Catch 0/61 Tangleroots Fireflies").isEmpty());
		assertTrue(parse("Harvest 5/0 Cherry Logs").isEmpty());
	}

	@Test void harvestOfANonCropFallsBackToBreak() {
		assertEquals(Optional.of(rule(Kind.BREAK, 10, new Named("oak log"), new AnyWorld())), parse("Harvest 10 Oak Logs"));
		assertEquals(Optional.of(rule(Kind.HARVEST, 10, new Named("potato"), new AnyWorld())), parse("Harvest 10 Potatoes"));
	}

	@Test void noRule() {
		for (String s : List.of("Reach 2,500 Skill Level", "Complete 25 Jobs", "Get 20 Boss Kills",
				"Participate in Killing 20 Bosses", "Place a Spawner", "Discover Wolfhaven", "Catch 5 Angelfish",
				"Slay 5 Great Bosses", "Kill 0 Zombies", "Pay $10,000,000", "Reach Party Level 55")) {
			assertTrue(parse(s).isEmpty(), s);
		}
		ObjectiveInfo multi = new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Kill 500 Zombies", 100.0),
				new ObjectiveInfo.Sub("Kill 500 Skeletons", 40.0)), false, false);
		assertTrue(ObjectiveParser.parse(multi, WORLDS).isEmpty());
		ObjectiveInfo handIn = new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Harvest 400 Wheat", null)), false, true);
		assertTrue(ObjectiveParser.parse(handIn, WORLDS).isEmpty());
		assertTrue(ObjectiveParser.parse(null, WORLDS).isEmpty());
	}
}
