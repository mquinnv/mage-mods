package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.mage.cubewheel.config.DefaultConfig;
import net.mage.cubewheel.tracker.ProgressExtractor.Progress;
import net.mage.cubewheel.tracker.TrackerStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * "Mine 0/830 resources in Icehaven": in a mana world only the resource nodes are mineable. Any other block
 * (stone, dirt, logs) vanishes under the pickaxe and the server puts it straight back, and ManaCube does not credit
 * it (Michael 2026-10-08). Ore does count: the world's own ore variants, and ancient debris in Icehaven.
 */
class ManaWorldBlocksTest {
	@TempDir Path dir;
	static final List<String> WORLDS = DefaultConfig.manaWorlds();
	static final WorldInfo ICEHAVEN = WorldResolver.resolve("minecraft:icehaven", List.of(), WORLDS);
	static final WorldInfo OVERWORLD = WorldResolver.resolve("minecraft:overworld", List.of(), WORLDS);
	static final String ICEHAVEN_RESOURCES = "jobs:Mining Heavy · Mine resources in Icehaven";
	static final String ANY_RESOURCES = "jobs:Mining Beginner · Mine Resources";

	TrackerStore store() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Mining Heavy · Mine resources in Icehaven", new Progress(0, 830), 0);
		s.setObjective(ICEHAVEN_RESOURCES, obj("Mine 0/830 resources in Icehaven"));
		s.update("jobs", "Mining Beginner · Mine Resources", new Progress(0, 500), 0);
		s.setObjective(ANY_RESOURCES, obj("Mine 0/500 Resources"));
		return s;
	}

	static ObjectiveInfo obj(String line) {
		return new ObjectiveInfo(List.of(new ObjectiveInfo.Sub(line, null)), false, false);
	}

	static Signal.BlockBroken block(String id, String name, Set<String> groups, WorldInfo at) {
		return new Signal.BlockBroken(id, name, groups, false, false, false, at);
	}

	static List<LocalCounter.Contribution> count(Signal.BlockBroken b, TrackerStore s) {
		return LocalCounter.onSignal(b, s, WORLDS, 5);
	}

	@Test void stoneAndLogsInAManaWorldAreNotResourcesMined() {
		TrackerStore s = store();
		assertEquals(List.of(), count(block("minecraft:stone", "Stone", Set.of(), ICEHAVEN), s));
		assertEquals(List.of(), count(block("minecraft:spruce_log", "Spruce Log", Set.of("logs"), ICEHAVEN), s));
		assertEquals(List.of(), count(block("minecraft:packed_ice", "Packed Ice", Set.of(), ICEHAVEN), s));
	}

	@Test void oreAncientDebrisAndIceCrystalsInIcehavenAre() {
		TrackerStore s = store();
		List<LocalCounter.Contribution> both = List.of(
				new LocalCounter.Contribution(ICEHAVEN_RESOURCES, 1), new LocalCounter.Contribution(ANY_RESOURCES, 1));
		assertEquals(both, count(block("minecraft:deepslate_diamond_ore", "Deepslate Diamond Ore", Set.of("ore"), ICEHAVEN), s));
		assertEquals(both, count(block("minecraft:ancient_debris", "Ancient Debris", Set.of("ore"), ICEHAVEN), s));
		assertEquals(both, count(block("minecraft:ice", "Ice", Set.of(), ICEHAVEN), s));
	}

	@Test void stoneInTheOverworldStillCountsForABareResourcesJob() {
		assertEquals(List.of(new LocalCounter.Contribution(ANY_RESOURCES, 1)),
				count(block("minecraft:stone", "Stone", Set.of(), OVERWORLD), store()));
	}

	@Test void aJobNamingTheBlockIsStillJudgedByItsName() {
		TrackerStore s = new TrackerStore(dir.resolve("n.json"));
		s.update("jobs", "Mining Heavy · Mine Icehaven Ice Crystals", new Progress(0, 85), 0);
		s.setObjective("jobs:Mining Heavy · Mine Icehaven Ice Crystals", obj("Mine 0/85 Icehaven Ice Crystals"));
		assertEquals(List.of(new LocalCounter.Contribution("jobs:Mining Heavy · Mine Icehaven Ice Crystals", 1)),
				count(block("minecraft:ice", "Ice", Set.of(), ICEHAVEN), s));
		assertEquals(List.of(), count(block("minecraft:stone", "Stone", Set.of(), ICEHAVEN), s));
	}
}
