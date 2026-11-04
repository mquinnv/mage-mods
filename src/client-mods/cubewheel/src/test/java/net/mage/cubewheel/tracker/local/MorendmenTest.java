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
 * "Slay 0/96 Morendmen in Morend" (listing, capture 2026-11-03): the mob is a slime on a recycled hitbox, named
 * "Morendman 150⺛" by a text display. "Morendmen" is the plural of "Morendman", a word no list of irregulars knew.
 */
class MorendmenTest {
	@TempDir Path dir;
	static final List<String> WORLDS = DefaultConfig.manaWorlds();
	static final WorldInfo MOREND = WorldResolver.resolve("minecraft:morend", List.of(), WORLDS);
	static final String ID = "jobs:Hunting Experienced · Slay Morendmen in Morend";

	TrackerStore store() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Hunting Experienced · Slay Morendmen in Morend", new Progress(0, 96), 0);
		s.setObjective(ID, new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Slay 0/96 Morendmen in Morend", null)), false, false));
		return s;
	}

	@Test void aMorendmanKilledInMorendCounts() {
		Signal.MobKilled kill = new Signal.MobKilled("minecraft:slime", "Morendman 150⺛", Set.of("mob"), 1, MOREND);
		assertEquals(List.of(new LocalCounter.Contribution(ID, 1)), LocalCounter.onSignal(kill, store(), WORLDS, 5));
	}
}
