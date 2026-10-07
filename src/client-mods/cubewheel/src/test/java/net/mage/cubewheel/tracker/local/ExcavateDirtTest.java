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
 * "Excavate 0/814 Dirt" (jobs menu, 2026-10-07): the verb is "excavate" and the block is plain
 * minecraft:dirt, so digging dirt anywhere counts; a grass block does not.
 */
class ExcavateDirtTest {
	@TempDir Path dir;
	static final List<String> WORLDS = DefaultConfig.manaWorlds();
	static final WorldInfo OVERWORLD = WorldResolver.resolve("minecraft:overworld", List.of(), WORLDS);
	static final String ID = "jobs:Mining Beginner · Excavate Dirt";

	TrackerStore store() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Mining Beginner · Excavate Dirt", new Progress(0, 814), 0);
		s.setObjective(ID, new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Excavate 0/814 Dirt", null)), false, false));
		return s;
	}

	static Signal.BlockBroken block(String id, String name, WorldInfo at) {
		return new Signal.BlockBroken(id, name, Set.of(), false, false, false, at);
	}

	@Test void dirtDugCountsAsExcavated() {
		assertEquals(List.of(new LocalCounter.Contribution(ID, 1)),
				LocalCounter.onSignal(block("minecraft:dirt", "Dirt", OVERWORLD), store(), WORLDS, 5));
	}

	@Test void grassBlockDoesNot() {
		assertEquals(List.of(), LocalCounter.onSignal(block("minecraft:grass_block", "Grass Block", OVERWORLD), store(), WORLDS, 5));
	}
}
