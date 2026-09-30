package com.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mage.cubewheel.tracker.local.WorldScope.Relevance;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorldScopeTest {
	static final List<String> WORLDS = List.of("wolfhaven", "tangleroots", "sandara", "icehaven", "morend", "burninglands");
	static final WorldInfo TANGLEROOT = WorldResolver.resolve("minecraft:tangleroot", List.of(), WORLDS);
	static final WorldInfo SANDARA = WorldResolver.resolve("minecraft:sandara", List.of(), WORLDS);
	static final WorldInfo SPAWN = WorldResolver.resolve("minecraft:overworld", List.of(), WORLDS);

	static ObjectiveInfo obj(String line, boolean special) {
		return new ObjectiveInfo(List.of(new ObjectiveInfo.Sub(line, null)), special, false);
	}

	static WorldScope.Scope scope(String name, String line) {
		return WorldScope.of(name, line == null ? null : obj(line, false), WORLDS);
	}

	@Test void worldsAreFoundInNameAndObjective() {
		assertEquals(Set.of("tangleroot"), scope("Hunting Beginner · Slay Tigers in Tangleroots", "Slay 16/64 Tigers in Tangleroots").worlds());
		assertEquals(Set.of("wolfhaven"), scope("Haven Harvester", "Harvest or Mine 10,000 Wolfhaven Resources").worlds());
		assertEquals(Set.of("tangleroot"), scope("King of the Jungle", "Slay 2,500 Tangleroot Monsters").worlds());
		assertEquals(Set.of("burningland"), scope("Scorched", "Slay 50 Burning Lands Monsters").worlds());
		assertEquals(Set.of("tangleroot"), scope("Farming Experienced · Catch Tangleroots Fireflies", null).worlds());
		assertFalse(scope("Mining Novice · Mine Stone", "Mine 5/100 Stone").scoped());
		assertTrue(WorldScope.of("Rank [✪6] · Slay 1,000 Monsters", obj("Slay 1,000 Monsters", true), WORLDS).scoped());
	}

	@Test void relevanceToTheCurrentWorld() {
		WorldScope.Scope tigers = scope("Hunting Beginner · Slay Tigers in Tangleroots", "Slay 16/64 Tigers in Tangleroots");
		assertEquals(Relevance.CURRENT, WorldScope.relevance(tigers, TANGLEROOT));
		assertEquals(Relevance.OTHER, WorldScope.relevance(tigers, SANDARA));
		assertEquals(Relevance.OTHER, WorldScope.relevance(tigers, SPAWN));
		assertEquals(Relevance.NEUTRAL, WorldScope.relevance(tigers, WorldInfo.UNKNOWN));
		assertEquals(Relevance.NEUTRAL, WorldScope.relevance(tigers, null));
		WorldScope.Scope stone = scope("Mine Stone", "Mine 100 Stone");
		assertEquals(Relevance.NEUTRAL, WorldScope.relevance(stone, TANGLEROOT));
		WorldScope.Scope special = WorldScope.of("Rank [✪6] · Slay 1,000 Monsters", obj("Slay 1,000 Monsters", true), WORLDS);
		assertEquals(Relevance.CURRENT, WorldScope.relevance(special, SANDARA));
		assertEquals(Relevance.OTHER, WorldScope.relevance(special, SPAWN));
	}

	@Test void modes() {
		assertEquals(WorldScope.Mode.SORT, WorldScope.Mode.parse(null));
		assertEquals(WorldScope.Mode.SORT, WorldScope.Mode.parse("bogus"));
		assertEquals(WorldScope.Mode.HIDE, WorldScope.Mode.parse(" Hide "));
		assertEquals(WorldScope.Mode.OFF, WorldScope.Mode.parse("off"));
	}
}
