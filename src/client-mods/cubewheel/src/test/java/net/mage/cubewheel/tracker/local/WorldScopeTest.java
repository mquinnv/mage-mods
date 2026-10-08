package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.tracker.local.CounterRule.Any;
import net.mage.cubewheel.tracker.local.CounterRule.AnyWorld;
import net.mage.cubewheel.tracker.local.CounterRule.Group;
import net.mage.cubewheel.tracker.local.CounterRule.Kind;
import net.mage.cubewheel.tracker.local.CounterRule.Named;
import net.mage.cubewheel.tracker.local.CounterRule.NamedWorld;
import net.mage.cubewheel.tracker.local.CounterRule.Special;
import net.mage.cubewheel.tracker.local.WorldScope.Relevance;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorldScopeTest {
	static final List<String> WORLDS = List.of("wolfhaven", "tangleroots", "sandara", "icehaven", "morend", "burninglands");
	static final WorldInfo TANGLEROOT = WorldResolver.resolve("minecraft:tangleroot", List.of(), WORLDS);
	static final WorldInfo SANDARA = WorldResolver.resolve("minecraft:sandara", List.of(), WORLDS);
	static final WorldInfo SPAWN = WorldResolver.resolve("minecraft:overworld", List.of(), WORLDS);
	static final WorldInfo ICEHAVEN = WorldResolver.resolve("minecraft:icehaven", List.of(), WORLDS);

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

	static CounterRule rule(String line, boolean special) {
		return ObjectiveParser.parse(new ObjectiveInfo(List.of(new ObjectiveInfo.Sub(line, null)), special, false), WORLDS)
				.orElseThrow();
	}

	/** A bare vanilla job ("Mine 245 Coal") cannot be done in a mana world: it has its own variants. */
	@Test void bareVanillaTargetsAreImpossibleInAManaWorld() {
		CounterRule coal = rule("Mine 0/245 Coal", false);
		assertEquals(new CounterRule(Kind.BREAK, 245, new Named("coal"), new AnyWorld()), coal);
		assertTrue(WorldScope.impossibleIn(coal, ICEHAVEN));
		assertTrue(WorldScope.impossibleIn(rule("Harvest 0/850 Beetroot", false), SANDARA));
		assertTrue(WorldScope.impossibleIn(rule("Slay 0/120 Zombies", false), TANGLEROOT));
		// Only a mana world: in the overworld (known, not special) or an unknown world it stays possible.
		assertFalse(WorldScope.impossibleIn(coal, SPAWN));
		assertFalse(WorldScope.impossibleIn(coal, WorldInfo.UNKNOWN));
		assertFalse(WorldScope.impossibleIn(coal, null));
		assertFalse(WorldScope.impossibleIn(null, ICEHAVEN));
	}

	/** Groups, "anything", and rules already naming a world are left to relevance. */
	@Test void groupsAndWorldScopedRulesStayPossible() {
		CounterRule monsters = rule("Slay 0/670 Monsters", false);
		assertEquals(new Group("monster"), monsters.what());
		assertFalse(WorldScope.impossibleIn(monsters, ICEHAVEN));
		CounterRule resources = rule("Harvest or Mine 0/3,750 Resources", false);
		assertEquals(new Any(), resources.what());
		assertFalse(WorldScope.impossibleIn(resources, ICEHAVEN));
		assertFalse(WorldScope.impossibleIn(new CounterRule(Kind.BREAK, 10, new Group("ore"), new AnyWorld()), ICEHAVEN));
		CounterRule crystals = rule("Mine 0/85 Icehaven Ice Crystals", false);
		assertEquals(new NamedWorld("icehaven"), crystals.world());
		assertFalse(WorldScope.impossibleIn(crystals, ICEHAVEN));
		assertFalse(WorldScope.impossibleIn(crystals, SANDARA));
		CounterRule prestige = rule("Slay 1,000 Zombies", true);
		assertEquals(new Special(), prestige.world());
		assertFalse(WorldScope.impossibleIn(prestige, ICEHAVEN));
	}

	@Test void modes() {
		assertEquals(WorldScope.Mode.SORT, WorldScope.Mode.parse(null));
		assertEquals(WorldScope.Mode.SORT, WorldScope.Mode.parse("bogus"));
		assertEquals(WorldScope.Mode.HIDE, WorldScope.Mode.parse(" Hide "));
		assertEquals(WorldScope.Mode.OFF, WorldScope.Mode.parse("off"));
	}
}
