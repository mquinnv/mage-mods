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
 * "Harvest 55/201 Wolfhaven Wheat": in Wolfhaven the "wheat" you scythe is a hay block (capture 2026-09-30:
 * area breaks "counted minecraft:hay_block=7; no rule minecraft:hay_block=7").
 */
class WolfhavenWheatTest {
	@TempDir Path dir;
	static final List<String> WORLDS = DefaultConfig.manaWorlds();
	static final WorldInfo WOLFHAVEN = WorldResolver.resolve("minecraft:wolfhaven", List.of(), WORLDS);
	static final WorldInfo OVERWORLD = WorldResolver.resolve("minecraft:overworld", List.of(), WORLDS);
	static final String ID = "jobs:Farming Beginner · Harvest Wolfhaven Wheat";

	TrackerStore store() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Farming Beginner · Harvest Wolfhaven Wheat", new Progress(55, 201), 0);
		s.setObjective(ID, new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Harvest 55/201 Wolfhaven Wheat", null)), false, false));
		return s;
	}

	static Signal.BlockBroken hay(WorldInfo at) {
		return new Signal.BlockBroken("minecraft:hay_block", "Hay Bale", Set.of(), false, false, false, at);
	}

	@Test void aHayBlockScythedInWolfhavenCountsAsWheat() {
		assertEquals(List.of(new LocalCounter.Contribution(ID, 1)), LocalCounter.onSignal(hay(WOLFHAVEN), store(), WORLDS, 5));
	}

	@Test void aHayBlockOutsideWolfhavenDoesNot() {
		assertEquals(List.of(), LocalCounter.onSignal(hay(OVERWORLD), store(), WORLDS, 5));
	}

	@Test void unripeVanillaWheatStillDoesNot() {
		Signal.BlockBroken young = new Signal.BlockBroken("minecraft:wheat", "Wheat Crops", Set.of("crop"), true, false, false, WOLFHAVEN);
		assertEquals(List.of(), LocalCounter.onSignal(young, store(), WORLDS, 5));
	}
}
