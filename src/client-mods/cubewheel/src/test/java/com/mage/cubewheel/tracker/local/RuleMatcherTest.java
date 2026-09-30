package com.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mage.cubewheel.tracker.local.CounterRule.Any;
import com.mage.cubewheel.tracker.local.CounterRule.AnyWorld;
import com.mage.cubewheel.tracker.local.CounterRule.Group;
import com.mage.cubewheel.tracker.local.CounterRule.Kind;
import com.mage.cubewheel.tracker.local.CounterRule.Named;
import com.mage.cubewheel.tracker.local.CounterRule.NamedWorld;
import com.mage.cubewheel.tracker.local.CounterRule.Special;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleMatcherTest {
	static final WorldInfo OVERWORLD = new WorldInfo(Set.of("overworld"), false, true);
	static final WorldInfo SANDARA = new WorldInfo(Set.of("sandara"), true, true);
	static final WorldInfo UNKNOWN = WorldInfo.UNKNOWN;

	static CounterRule rule(Kind k, CounterRule.Target t, CounterRule.World w) {
		return new CounterRule(k, 100, t, w);
	}

	static Signal.BlockBroken wheat(boolean mature) {
		return new Signal.BlockBroken("minecraft:wheat", "Wheat", Set.of("crop"), true, mature, OVERWORLD);
	}

	@Test void cropsCountOnlyWhenMature() {
		CounterRule crops = rule(Kind.HARVEST, new Group("crop"), new AnyWorld());
		CounterRule wheat = rule(Kind.HARVEST, new Named("wheat"), new AnyWorld());
		CounterRule anyBlock = rule(Kind.BREAK, new Any(), new AnyWorld());
		assertTrue(RuleMatcher.matches(crops, wheat(true)));
		assertTrue(RuleMatcher.matches(wheat, wheat(true)));
		assertTrue(RuleMatcher.matches(anyBlock, wheat(true)));
		assertFalse(RuleMatcher.matches(crops, wheat(false)));
		assertFalse(RuleMatcher.matches(wheat, wheat(false)));
		assertFalse(RuleMatcher.matches(anyBlock, wheat(false)));
	}

	@Test void blockNamesMatchIdsAndPlurals() {
		CounterRule cobble = rule(Kind.BREAK, new Named("cobblestone"), new AnyWorld());
		CounterRule stone = rule(Kind.BREAK, new Named("stone"), new AnyWorld());
		Signal.BlockBroken stoneBlock = new Signal.BlockBroken("minecraft:stone", "Stone", Set.of(), false, false, OVERWORLD);
		assertFalse(RuleMatcher.matches(cobble, stoneBlock));
		assertTrue(RuleMatcher.matches(stone, stoneBlock));
		CounterRule potato = rule(Kind.HARVEST, new Named("potato"), new AnyWorld());
		assertTrue(RuleMatcher.matches(potato,
				new Signal.BlockBroken("minecraft:potatoes", "Potatoes", Set.of("crop"), true, true, OVERWORLD)));
		CounterRule oakLogs = rule(Kind.BREAK, new Named("oak log"), new AnyWorld());
		assertTrue(RuleMatcher.matches(oakLogs,
				new Signal.BlockBroken("minecraft:oak_log", "Oak Log", Set.of("logs"), false, false, OVERWORLD)));
		assertFalse(RuleMatcher.matches(rule(Kind.KILL, new Any(), new AnyWorld()), stoneBlock));
	}

	@Test void worldScopedKills() {
		CounterRule sandara = rule(Kind.KILL, new Group("monster"), new NamedWorld("sandara"));
		CounterRule special = rule(Kind.KILL, new Group("monster"), new Special());
		Signal.MobKilled zombieInSandara = new Signal.MobKilled("minecraft:zombie", "Zombie", Set.of("mob", "monster"), 1, SANDARA);
		Signal.MobKilled zombieHere = new Signal.MobKilled("minecraft:zombie", "Zombie", Set.of("mob", "monster"), 1, OVERWORLD);
		Signal.MobKilled zombieNowhere = new Signal.MobKilled("minecraft:zombie", "Zombie", Set.of("mob", "monster"), 1, UNKNOWN);
		assertTrue(RuleMatcher.matches(sandara, zombieInSandara));
		assertTrue(RuleMatcher.matches(special, zombieInSandara));
		assertFalse(RuleMatcher.matches(sandara, zombieHere));
		assertFalse(RuleMatcher.matches(special, zombieHere));
		assertFalse(RuleMatcher.matches(sandara, zombieNowhere));
		assertFalse(RuleMatcher.matches(special, zombieNowhere));
		assertTrue(RuleMatcher.matches(rule(Kind.KILL, new Group("mob"), new AnyWorld()), zombieNowhere));
	}

	@Test void multiWordWorldsMatch() {
		CounterRule burning = rule(Kind.KILL, new Group("monster"), new NamedWorld("burningland"));
		WorldInfo there = WorldResolver.resolve("minecraft:overworld", java.util.List.of("World: Burning Lands"), java.util.List.of());
		assertTrue(RuleMatcher.matches(burning,
				new Signal.MobKilled("minecraft:blaze", "Blaze", Set.of("mob", "monster"), 1, there)));
		assertFalse(RuleMatcher.matches(burning,
				new Signal.MobKilled("minecraft:blaze", "Blaze", Set.of("mob", "monster"), 1, OVERWORLD)));
	}

	@Test void customNamedMobs() {
		Signal.MobKilled manaWolf = new Signal.MobKilled("minecraft:wolf", "Mana Wolf", Set.of("mob", "monster"), 1, OVERWORLD);
		assertTrue(RuleMatcher.matches(rule(Kind.KILL, new Named("mana wolf"), new AnyWorld()), manaWolf));
		assertTrue(RuleMatcher.matches(rule(Kind.KILL, new Named("wolf"), new AnyWorld()), manaWolf));
		assertTrue(RuleMatcher.matches(rule(Kind.KILL, new Group("monster"), new AnyWorld()), manaWolf));
		assertFalse(RuleMatcher.matches(rule(Kind.KILL, new Named("mana slime"), new AnyWorld()), manaWolf));
		Signal.MobKilled enderman = new Signal.MobKilled("minecraft:enderman", "Enderman", Set.of("mob", "monster"), 1, OVERWORLD);
		assertTrue(RuleMatcher.matches(rule(Kind.KILL, new Named("enderman"), new AnyWorld()), enderman));
		Signal.MobKilled player = new Signal.MobKilled("minecraft:player", "Steve", Set.of("player"), 1, OVERWORLD);
		assertFalse(RuleMatcher.matches(rule(Kind.KILL, new Any(), new AnyWorld()), player));
	}

	@Test void anyTargetsIgnoreInstabreakVegetation() {
		CounterRule resources = rule(Kind.BREAK, new Any(), new AnyWorld());
		// trivial = replaceable or instabreak (destroy speed 0): grass, flowers, ferns...
		Signal.BlockBroken grass = new Signal.BlockBroken("minecraft:short_grass", "Short Grass", Set.of(), false, false, true, OVERWORLD);
		Signal.BlockBroken poppy = new Signal.BlockBroken("minecraft:poppy", "Poppy", Set.of(), false, false, true, OVERWORLD);
		assertFalse(RuleMatcher.matches(resources, grass));
		assertFalse(RuleMatcher.matches(resources, poppy));
		// ...but a mature crop is instabreak too and still counts; so does a named target.
		Signal.BlockBroken ripeWheat = new Signal.BlockBroken("minecraft:wheat", "Wheat", Set.of("crop"), true, true, true, OVERWORLD);
		assertTrue(RuleMatcher.matches(resources, ripeWheat));
		assertTrue(RuleMatcher.matches(rule(Kind.BREAK, new Named("short grass"), new AnyWorld()), grass));
		Signal.BlockBroken stone = new Signal.BlockBroken("minecraft:stone", "Stone", Set.of(), false, false, false, OVERWORLD);
		assertTrue(RuleMatcher.matches(resources, stone));
	}

	@Test void cropIdAliases() {
		Signal.BlockBroken bush = new Signal.BlockBroken("minecraft:sweet_berry_bush", "Sweet Berry Bush", Set.of("crop"), true, true, OVERWORLD);
		Signal.BlockBroken cocoa = new Signal.BlockBroken("minecraft:cocoa", "Cocoa", Set.of("crop"), true, true, OVERWORLD);
		CounterRule berries = ObjectiveParserTest.parse("Harvest 50 Sweet Berries").orElseThrow();
		CounterRule beans = ObjectiveParserTest.parse("Harvest 50 Cocoa Beans").orElseThrow();
		assertTrue(RuleMatcher.matches(berries, bush));
		assertTrue(RuleMatcher.matches(beans, cocoa));
		assertFalse(RuleMatcher.matches(berries, cocoa));
		assertFalse(RuleMatcher.matches(beans, bush));
	}

	@Test void fish() {
		assertTrue(RuleMatcher.matches(rule(Kind.FISH, new Any(), new AnyWorld()), new Signal.FishCaught(UNKNOWN)));
		assertFalse(RuleMatcher.matches(rule(Kind.FISH, new Any(), new Special()), new Signal.FishCaught(OVERWORLD)));
		assertFalse(RuleMatcher.matches(rule(Kind.BREAK, new Any(), new AnyWorld()), new Signal.FishCaught(OVERWORLD)));
	}
}
