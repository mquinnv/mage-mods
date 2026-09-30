package com.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mage.cubewheel.tracker.local.Accuracy;
import com.mage.cubewheel.tracker.local.CounterRule;
import com.mage.cubewheel.tracker.local.ObjectiveInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackerStoreEstimateTest {
	@TempDir Path dir;
	static final List<String> WORLDS = List.of("wolfhaven", "sandara");
	static final String MINER = "pquests:Miner";
	static final String HARVESTER = "pquests:Haven Harvester";

	static ProgressExtractor.Progress p(double c, double m) {
		return new ProgressExtractor.Progress(c, m);
	}

	static ObjectiveInfo obj(String line) {
		return new ObjectiveInfo(List.of(new ObjectiveInfo.Sub(line, null)), false, false);
	}

	TrackerStore store() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("pquests", "Miner", p(100, 300), 0);
		s.setObjective(MINER, obj("Mine 300 Stone"));
		s.update("pquests", "Haven Harvester", p(64, 100), 0);
		s.setObjective(HARVESTER, obj("Harvest or Mine 10,000 Wolfhaven Resources"));
		return s;
	}

	@Test void activeRulesCoverIncompleteItemsWithARule() {
		TrackerStore s = store();
		s.update("pquests", "Done", p(300, 300), 0);
		s.setObjective("pquests:Done", obj("Mine 300 Cobblestone"));
		s.update("prestige", "Skills", p(1851, 2500), 0);
		s.setObjective("prestige:Skills", obj("Reach 2,500 Skill Level"));
		assertEquals(Set.of(MINER, HARVESTER), s.activeRules(WORLDS).keySet());
		assertEquals(new CounterRule.NamedWorld("wolfhaven"), s.activeRules(WORLDS).get(HARVESTER).world());
		s.update("pquests", "Miner", p(300, 300), 1);
		assertEquals(Set.of(HARVESTER), s.activeRules(WORLDS).keySet()); // rebuilt after a change
	}

	@Test void authoritativeUpdateSnapsBackAndRecordsAccuracy() {
		TrackerStore s = store();
		List<String> snaps = new ArrayList<>();
		s.setSnapBackListener((id, acc) -> snaps.add(id + "=" + acc.counted() + "/" + acc.actual()));
		assertTrue(s.addEstimate(MINER, 5, 10));
		assertTrue(s.addEstimate(MINER, 2, 20));
		assertEquals(7, s.estimate(MINER).orElseThrow().count());
		assertEquals(20, s.estimate(MINER).orElseThrow().lastAt());
		assertTrue(s.update("pquests", "Miner", p(108, 300), 30));
		assertTrue(s.estimate(MINER).isEmpty());
		assertEquals(new Accuracy(7, 8), s.lastAccuracy(MINER).orElseThrow());
		assertEquals(List.of(MINER + "=7/8.0"), snaps);
	}

	@Test void unchangedRecentUpdateStillClearsTheEstimate() {
		TrackerStore s = store();
		s.addEstimate(MINER, 3, 10);
		assertTrue(s.update("pquests", "Miner", p(100, 300), 20)); // same values, seen shortly after
		assertTrue(s.estimate(MINER).isEmpty());
		assertFalse(s.update("pquests", "Miner", p(100, 300), 30)); // nothing left to clear
	}

	@Test void percentAccuracyIsInObjectiveUnits() {
		TrackerStore s = store();
		s.addEstimate(HARVESTER, 150, 10);
		s.update("pquests", "Haven Harvester", p(66, 100), 20); // +2% of 10,000 = 200 units
		assertEquals(200, s.lastAccuracy(HARVESTER).orElseThrow().actual(), 1e-9);
	}

	@Test void liveValueIsAnAuthoritativeRead() {
		TrackerStore s = store();
		s.addEstimate(MINER, 4, 10);
		assertEquals(1, s.applyLiveValue(Pattern.compile("Miner"), 50, 20));
		assertTrue(s.estimate(MINER).isEmpty());
	}

	@Test void estimatesNeedAnIncompleteKnownItem() {
		TrackerStore s = store();
		s.update("pquests", "Done", p(300, 300), 0);
		assertFalse(s.addEstimate("pquests:Done", 1, 5));
		assertFalse(s.addEstimate("pquests:Nope", 1, 5));
		assertFalse(s.addEstimate(MINER, 0, 5));
		assertTrue(s.estimate("pquests:Done").isEmpty());
	}

	@Test void reverseFloorsAtZeroAndRemoves() {
		TrackerStore s = store();
		s.addEstimate(MINER, 3, 10);
		assertTrue(s.reverseEstimate(MINER, 1));
		assertEquals(2, s.estimate(MINER).orElseThrow().count());
		assertTrue(s.reverseEstimate(MINER, 5));
		assertTrue(s.estimate(MINER).isEmpty());
		assertFalse(s.reverseEstimate(MINER, 1));
	}

	@Test void saveLoadRoundTripsSideMaps() throws Exception {
		TrackerStore s = store();
		s.addEstimate(MINER, 9, 10);
		assertTrue(s.save());
		TrackerStore t = new TrackerStore(dir.resolve("t.json"));
		t.load();
		assertEquals(9, t.estimate(MINER).orElseThrow().count());
		assertEquals(100, t.estimate(MINER).orElseThrow().baseline(), 1e-9);
		assertEquals("Mine 300 Stone", t.objective(MINER).orElseThrow().subs().get(0).text());
		Files.writeString(dir.resolve("old.json"),
				"{\"items\":[{\"id\":\"jobs:A\",\"source\":\"jobs\",\"name\":\"A\",\"current\":1,\"max\":2,\"seenAt\":0}],\"pins\":[]}");
		TrackerStore old = new TrackerStore(dir.resolve("old.json"));
		old.load();
		assertEquals(1, old.all().size());
		assertTrue(old.estimate("jobs:A").isEmpty());
		Files.writeString(dir.resolve("nulls.json"), "{\"items\":[],\"objectives\":{\"x\":null},\"estimates\":{\"y\":null,\"z\":{\"count\":-3}}}");
		TrackerStore nulls = new TrackerStore(dir.resolve("nulls.json"));
		nulls.load();
		assertTrue(nulls.estimate("y").isEmpty());
		assertTrue(nulls.estimate("z").isEmpty());
	}

	@Test void forgetDropsSideEntries() {
		TrackerStore s = store();
		s.addEstimate(MINER, 1, 10);
		s.update("pquests", "Haven Harvester", p(64, 100), 1_000_000);
		assertEquals(1, s.forgetOlderThan(1_000_000, 500_000));
		assertTrue(s.estimate(MINER).isEmpty());
		assertTrue(s.objective(MINER).isEmpty());
	}

	@Test void hudUsesTheEstimatedFraction() {
		TrackerStore s = store();
		assertEquals(List.of(HARVESTER, MINER), ids(s.hudRows(6, true))); // 64% before 33%
		s.addEstimate(MINER, 150, 10); // 250 / 300 = 83%
		List<TrackerRow> rows = s.hudRows(6, true);
		assertEquals(List.of(MINER, HARVESTER), ids(rows)); // sorted by the estimated fraction
		assertTrue(rows.get(0).estimated()); // keeps its "~"
		assertEquals(250, rows.get(0).shownCurrent(), 1e-9);
		assertEquals(List.of(HARVESTER, MINER), ids(s.hudRows(6, false))); // counting off: stored estimates are ignored
		assertFalse(s.rows(false).get(0).estimated());
	}

	static List<String> ids(List<TrackerRow> rows) {
		return rows.stream().map(r -> r.item().id()).toList();
	}
}
