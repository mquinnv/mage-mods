package com.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mage.cubewheel.tracker.ProgressExtractor.Progress;
import com.mage.cubewheel.tracker.TrackerStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalCounterTest {
	@TempDir Path dir;
	static final List<String> WORLDS = List.of("wolfhaven", "sandara");
	static final WorldInfo OVERWORLD = new WorldInfo(Set.of("overworld"), false, true);

	static ObjectiveInfo obj(String line, boolean special) {
		return new ObjectiveInfo(List.of(new ObjectiveInfo.Sub(line, null)), special, false);
	}

	TrackerStore store() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("prestige", "Rank [✪1] · Harvest 21,250 Crops", new Progress(20_000, 21_250), 0);
		s.setObjective("prestige:Rank [✪1] · Harvest 21,250 Crops", obj("Harvest 21,250 Crops", false));
		s.update("pquests", "Honest Work", new Progress(10, 100), 0);
		s.setObjective("pquests:Honest Work", obj("Harvest 64 Wheat", false));
		s.update("pquests", "Miner", new Progress(10, 100), 0);
		s.setObjective("pquests:Miner", obj("Mine 300 Stone", false));
		s.update("prestige", "Rank [✪6] · Slay 1,000 Monsters", new Progress(400, 1000), 0);
		s.setObjective("prestige:Rank [✪6] · Slay 1,000 Monsters", obj("Slay 1,000 Monsters", true));
		return s;
	}

	@Test void oneSignalAdvancesEveryMatchingRule() {
		TrackerStore s = store();
		List<LocalCounter.Contribution> c = LocalCounter.onSignal(
				new Signal.BlockBroken("minecraft:wheat", "Wheat", Set.of("crop"), true, true, OVERWORLD), s, WORLDS, 5);
		assertEquals(Set.of("prestige:Rank [✪1] · Harvest 21,250 Crops", "pquests:Honest Work"),
				Set.copyOf(c.stream().map(LocalCounter.Contribution::id).toList()));
		assertEquals(1, s.estimate("pquests:Honest Work").orElseThrow().count());
		assertTrue(s.estimate("pquests:Miner").isEmpty());
	}

	@Test void killsAddTheirCountAndRespectTheWorld() {
		TrackerStore s = store();
		Signal.MobKilled here = new Signal.MobKilled("minecraft:zombie", "Zombie", Set.of("mob", "monster"), 3, OVERWORLD);
		assertTrue(LocalCounter.onSignal(here, s, WORLDS, 5).isEmpty()); // "special worlds" objective
		WorldInfo sandara = new WorldInfo(Set.of("sandara"), true, true);
		List<LocalCounter.Contribution> c = LocalCounter.onSignal(
				new Signal.MobKilled("minecraft:zombie", "Zombie", Set.of("mob", "monster"), 3, sandara), s, WORLDS, 6);
		assertEquals(List.of(new LocalCounter.Contribution("prestige:Rank [✪6] · Slay 1,000 Monsters", 3)), c);
	}

	@Test void reverseTakesBackExactlyTheContributions() {
		TrackerStore s = store();
		List<LocalCounter.Contribution> c = LocalCounter.onSignal(
				new Signal.BlockBroken("minecraft:wheat", "Wheat", Set.of("crop"), true, true, OVERWORLD), s, WORLDS, 5);
		LocalCounter.onSignal(new Signal.BlockBroken("minecraft:wheat", "Wheat", Set.of("crop"), true, true, OVERWORLD), s, WORLDS, 6);
		LocalCounter.reverse(c, s);
		assertEquals(1, s.estimate("pquests:Honest Work").orElseThrow().count());
	}

	@Test void killCountsAreBounded() {
		TrackerStore s = store();
		WorldInfo sandara = new WorldInfo(Set.of("sandara"), true, true);
		List<LocalCounter.Contribution> c = LocalCounter.onSignal(
				new Signal.MobKilled("minecraft:zombie", "Zombie", Set.of("monster"), 500, sandara), s, WORLDS, 5);
		assertEquals(64, c.get(0).units());
		assertTrue(LocalCounter.onSignal(
				new Signal.MobKilled("minecraft:zombie", "Zombie", Set.of("monster"), 0, sandara), s, WORLDS, 5).isEmpty());
	}
}
