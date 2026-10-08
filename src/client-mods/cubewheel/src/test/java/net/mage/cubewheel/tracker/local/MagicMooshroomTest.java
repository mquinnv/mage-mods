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
 * "Slay 7/68 Magic Mooshrooms in Wolfhaven" (listing, capture 2026-10-08): the mob is a mooshroom named
 * "Mooshroom 40⺛", killed with a death event. The world's prefix is in the job, not on the mob.
 */
class MagicMooshroomTest {
	@TempDir Path dir;
	static final List<String> WORLDS = DefaultConfig.manaWorlds();
	static final WorldInfo WOLFHAVEN = WorldResolver.resolve("minecraft:wolfhaven", List.of(), WORLDS);
	static final WorldInfo OVERWORLD = WorldResolver.resolve("minecraft:overworld", List.of(), WORLDS);
	static final String ID = "jobs:Hunting Beginner · Slay Magic Mooshrooms in Wolfhaven";

	TrackerStore store() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Hunting Beginner · Slay Magic Mooshrooms in Wolfhaven", new Progress(7, 68), 0);
		s.setObjective(ID, new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Slay 7/68 Magic Mooshrooms in Wolfhaven", null)), false, false));
		s.update("jobs", "Hunting Heavy · Slay Magic Mooshrooms", new Progress(0, 50), 0); // bare: no world
		s.setObjective("jobs:Hunting Heavy · Slay Magic Mooshrooms",
				new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Slay 0/50 Magic Mooshrooms", null)), false, false));
		return s;
	}

	static Signal.MobKilled mob(String id, String name, WorldInfo at) {
		return new Signal.MobKilled(id, name, Set.of("mob"), 1, at);
	}

	@Test void aMooshroomInWolfhavenIsAMagicMooshroom() {
		assertEquals(List.of(new LocalCounter.Contribution(ID, 1)),
				LocalCounter.onSignal(mob("minecraft:mooshroom", "Mooshroom 40⺛", WOLFHAVEN), store(), WORLDS, 5));
	}

	@Test void notOutsideWolfhavenAndNotForABareJob() {
		// Outside Wolfhaven nothing counts: the world-scoped job needs its world, and the bare "Slay Magic Mooshrooms"
		// job never reaches a mob by the last word of its noun (only a world-scoped rule may).
		assertEquals(List.of(), LocalCounter.onSignal(mob("minecraft:mooshroom", "Mooshroom 40⺛", OVERWORLD), store(), WORLDS, 5));
		assertEquals(List.of(new LocalCounter.Contribution(ID, 1)),
				LocalCounter.onSignal(mob("minecraft:mooshroom", "Mooshroom 40⺛", WOLFHAVEN), store(), WORLDS, 5));
	}
}
