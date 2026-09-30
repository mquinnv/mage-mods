package com.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mage.cubewheel.tracker.local.ObjectiveInfo;
import com.mage.cubewheel.tracker.local.WorldInfo;
import com.mage.cubewheel.tracker.local.WorldResolver;
import com.mage.cubewheel.tracker.local.WorldScope.Mode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** tracker.worldFilter: HUD entries for the current world first (sort) or other worlds' entries dropped (hide). */
class TrackerStoreWorldFilterTest {
	@TempDir Path dir;
	static final List<String> WORLDS = List.of("wolfhaven", "tangleroots", "sandara");
	static final WorldInfo TANGLEROOT = WorldResolver.resolve("minecraft:tangleroot", List.of(), WORLDS);
	static final String TIGERS = "jobs:Hunting Beginner · Slay Tigers in Tangleroots";
	static final String SNAKES = "jobs:Hunting Beginner · Slay Rattle Snakes in Sandara";
	static final String STONE = "pquests:Miner";
	static final String HAVEN = "pquests:Haven Harvester";

	static ObjectiveInfo obj(String line) {
		return new ObjectiveInfo(List.of(new ObjectiveInfo.Sub(line, null)), false, false);
	}

	TrackerStore store() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Hunting Beginner · Slay Tigers in Tangleroots", new ProgressExtractor.Progress(16, 64), 0); // 25%
		s.setObjective(TIGERS, obj("Slay 16/64 Tigers in Tangleroots"));
		s.update("jobs", "Hunting Beginner · Slay Rattle Snakes in Sandara", new ProgressExtractor.Progress(50, 58), 0); // 86%
		s.setObjective(SNAKES, obj("Slay 50/58 Rattle Snakes in Sandara"));
		s.update("pquests", "Miner", new ProgressExtractor.Progress(100, 300), 0); // 33%, unscoped
		s.setObjective(STONE, obj("Mine 300 Stone"));
		s.update("pquests", "Haven Harvester", new ProgressExtractor.Progress(64, 100), 0); // 64%
		s.setObjective(HAVEN, obj("Harvest or Mine 10,000 Wolfhaven Resources"));
		return s;
	}

	static List<String> ids(List<TrackerRow> rows) {
		return rows.stream().map(r -> r.item().id()).toList();
	}

	@Test void offOrUnknownWorldKeepsTheFractionOrder() {
		TrackerStore s = store();
		List<String> byFraction = List.of(SNAKES, HAVEN, STONE, TIGERS);
		assertEquals(byFraction, ids(s.hudRows(10, false)));
		assertEquals(byFraction, ids(s.hudRows(10, false, TANGLEROOT, WORLDS, Mode.OFF)));
		assertEquals(byFraction, ids(s.hudRows(10, false, WorldInfo.UNKNOWN, WORLDS, Mode.SORT)));
		assertEquals(byFraction, ids(s.hudRows(10, false, null, WORLDS, Mode.HIDE)));
	}

	@Test void sortPutsTheCurrentWorldFirstAndOtherWorldsLast() {
		TrackerStore s = store();
		assertEquals(List.of(TIGERS, STONE, SNAKES, HAVEN), ids(s.hudRows(10, false, TANGLEROOT, WORLDS, Mode.SORT)));
		assertEquals(List.of(TIGERS, STONE), ids(s.hudRows(2, false, TANGLEROOT, WORLDS, Mode.SORT)));
	}

	@Test void pinnedStayFirstInTheirOwnOrder() {
		TrackerStore s = store();
		s.togglePin(HAVEN);
		assertEquals(List.of(HAVEN, TIGERS, STONE, SNAKES), ids(s.hudRows(10, false, TANGLEROOT, WORLDS, Mode.SORT)));
		// hide: other worlds' entries leave the HUD unless pinned
		assertEquals(List.of(HAVEN, TIGERS, STONE), ids(s.hudRows(10, false, TANGLEROOT, WORLDS, Mode.HIDE)));
	}
}
