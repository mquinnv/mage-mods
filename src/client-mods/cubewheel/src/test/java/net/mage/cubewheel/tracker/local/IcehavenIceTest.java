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
 * "Mine 0/85 Icehaven Ice Crystals": the crystal you mine is a plain minecraft:ice block (capture
 * 2026-10-04: break minecraft:ice, server chat "Mine 85/85 Icehaven Ice Crystals").
 */
class IcehavenIceTest {
	@TempDir Path dir;
	static final List<String> WORLDS = DefaultConfig.manaWorlds();
	static final WorldInfo ICEHAVEN = WorldResolver.resolve("minecraft:icehaven", List.of(), WORLDS);
	static final WorldInfo OVERWORLD = WorldResolver.resolve("minecraft:overworld", List.of(), WORLDS);
	static final String ID = "jobs:Mining Heavy · Mine resources in Icehaven";

	TrackerStore store() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Mining Heavy · Mine resources in Icehaven", new Progress(0, 85), 0);
		s.setObjective(ID, new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Mine 0/85 Icehaven Ice Crystals", null)), false, false));
		return s;
	}

	static Signal.BlockBroken block(String id, WorldInfo at) {
		return new Signal.BlockBroken(id, "Ice", Set.of(), false, false, false, at);
	}

	@Test void iceMinedInIcehavenCountsAsAnIceCrystal() {
		assertEquals(List.of(new LocalCounter.Contribution(ID, 1)),
				LocalCounter.onSignal(block("minecraft:ice", ICEHAVEN), store(), WORLDS, 5));
	}

	@Test void iceElsewhereDoesNot() {
		assertEquals(List.of(), LocalCounter.onSignal(block("minecraft:ice", OVERWORLD), store(), WORLDS, 5));
	}

	@Test void packedAndBlueIceDoNot() {
		assertEquals(List.of(), LocalCounter.onSignal(block("minecraft:packed_ice", ICEHAVEN), store(), WORLDS, 5));
		assertEquals(List.of(), LocalCounter.onSignal(block("minecraft:blue_ice", ICEHAVEN), store(), WORLDS, 5));
	}
}
