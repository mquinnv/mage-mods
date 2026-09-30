package com.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mage.cubewheel.tracker.local.CounterRule.Kind;
import com.mage.cubewheel.tracker.local.CounterRule.Named;
import com.mage.cubewheel.tracker.local.CounterRule.NamedWorld;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** "Slay 16/64 Tigers in Tangleroots" (a /jobs Hunting listing) end to end through the pure classes. */
class KillRuleTest {
	static final List<String> WORLDS = List.of("wolfhaven", "tangleroots", "sandara", "icehaven", "morend", "burninglands");
	static final List<String> SIDEBAR = List.of("     §7ꜱᴘᴀᴡɴ ꜱᴇʀᴠᴇʀ  §0§1§r", "§r§0§2§r", "§7⛃ §fMoney: §a$3.09M§0§3§r");

	static CounterRule tigers() {
		return ObjectiveParser.parse(new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Slay 16/64 Tigers in Tangleroots", null)),
				false, false), WORLDS).orElseThrow();
	}

	static WorldInfo tangleroots() {
		return WorldResolver.resolve("minecraft:tangleroots", SIDEBAR, WORLDS);
	}

	static Signal.MobKilled kill(String typeId, String rawName, WorldInfo at) {
		String name = StackName.parse(rawName).map(StackName.Parsed::name).orElse("");
		return new Signal.MobKilled(typeId, name, Set.of("mob", "monster"), 1, at);
	}

	@Test void jobListingKillParses() {
		assertEquals(new CounterRule(Kind.KILL, 64, new Named("tiger"), new NamedWorld("tangleroot")), tigers());
		assertEquals(Optional.of(new CounterRule(Kind.KILL, 58, new Named("rattle snake"), new NamedWorld("sandara"))),
				ObjectiveParser.parse(new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Slay 4/58 Rattle Snakes in Sandara", null)),
						false, false), WORLDS));
	}

	@Test void tanglerootsWorldResolvesFromDimension() {
		WorldInfo at = tangleroots();
		assertTrue(at.known());
		assertTrue(at.special());
		assertTrue(RuleMatcher.worldMatches(tigers().world(), at));
		// Real capture: the dimension is singular ("minecraft:tangleroot"), objectives and config say "Tangleroots".
		WorldInfo real = WorldResolver.resolve("minecraft:tangleroot", SIDEBAR, WORLDS);
		assertTrue(real.special());
		assertTrue(RuleMatcher.worldMatches(tigers().world(), real));
		assertTrue(RuleMatcher.worldMatches(tigers().world(), WorldResolver.resolve("minecraft:world_tangleroots", SIDEBAR, WORLDS)));
		assertFalse(RuleMatcher.worldMatches(tigers().world(), WorldResolver.resolve("minecraft:wolfhaven", SIDEBAR, WORLDS)));
	}

	@Test void tigerNamesMatch() {
		CounterRule r = tigers();
		WorldInfo at = tangleroots();
		for (String name : List.of("Tiger", "Tigers", "5x Tiger", "Tiger ❤ 20", "§6Tiger", "Tangleroots Tiger", "Tiger [20/20]")) {
			assertTrue(RuleMatcher.matches(r, kill("minecraft:ocelot", name, at)), name);
		}
		assertTrue(RuleMatcher.matches(r, kill("manacube:tiger", "", at)));
		assertFalse(RuleMatcher.matches(r, kill("minecraft:ocelot", "Ocelot", at)));
		assertFalse(RuleMatcher.matches(r, kill("minecraft:ocelot", "Tiger", WorldResolver.resolve("minecraft:sandara", SIDEBAR, WORLDS))));
	}

	/** Real capture, Tangleroots: {"signal":"kill","id":"minecraft:frog","name":"Dart Frog 20⺛","world":["tangleroot"]}. */
	@Test void healthSuffixDigitsAndGlyphIsStripped() {
		assertEquals(Optional.of(new StackName.Parsed(1, "Dart Frog")), StackName.parse("Dart Frog 20⺛"));
		assertEquals(Optional.of(new StackName.Parsed(1, "Tiger")), StackName.parse("Tiger 12.5⺛"));
		assertEquals(Optional.of(new StackName.Parsed(5, "Tiger")), StackName.parse("5x Tiger 20⺛"));
		assertTrue(RuleMatcher.matches(tigers(), kill("minecraft:ocelot", "Tiger 20⺛", WorldResolver.resolve("minecraft:tangleroot", SIDEBAR, WORLDS))));
		// The raw capture name, even if a caller forgot to strip it.
		assertTrue(RuleMatcher.targetMatches(new Named("dart frog"), "minecraft:frog", "Dart Frog 20⺛", Set.of()));
	}

	@Test void stackFormats() {
		assertEquals(Optional.of(new StackName.Parsed(5, "Tiger")), StackName.parse("Tiger x5"));
		assertEquals(Optional.of(new StackName.Parsed(5, "Tiger")), StackName.parse("Tiger x 5"));
		assertEquals(Optional.of(new StackName.Parsed(5, "Tiger")), StackName.parse("x5 Tiger"));
		assertEquals(Optional.of(new StackName.Parsed(5, "Tiger")), StackName.parse("Tiger (x5)"));
		assertEquals(Optional.of(new StackName.Parsed(5, "Tiger")), StackName.parse("Tiger 5x"));
		assertEquals(Optional.of(new StackName.Parsed(5, "Tiger")), StackName.parse("Tiger x5 20⺛"));
		assertEquals(Optional.of(new StackName.Parsed(1, "Sphinx")), StackName.parse("Sphinx"));
	}

	@Test void compoundNamesMatchWrittenTogether() {
		assertTrue(RuleMatcher.targetMatches(new Named("rattle snake"), "minecraft:husk", "Rattlesnake 12⺛", Set.of()));
		assertFalse(RuleMatcher.targetMatches(new Named("snake"), "minecraft:husk", "Rattlesnake", Set.of()));
	}

	@Test void smallCapsAndLevelDecoratedNamesMatch() {
		CounterRule r = tigers();
		WorldInfo at = tangleroots();
		for (String name : List.of("ᴛɪɢᴇʀ", "TIGER", "[Lv. 5] Tiger", "Tiger [Lv. 5]", "Tiger Lvl 12", "✦ Tiger ✦")) {
			assertTrue(RuleMatcher.matches(r, kill("minecraft:ocelot", name, at)), name);
		}
	}
}
