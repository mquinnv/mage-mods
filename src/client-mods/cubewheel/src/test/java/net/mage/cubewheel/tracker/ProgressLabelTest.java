package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.*;

import net.mage.cubewheel.tracker.local.LocalCounter;
import net.mage.cubewheel.tracker.local.ObjectiveInfo;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgressLabelTest {
	@TempDir Path dir;
	static final List<String> WORLDS = List.of("Wolfhaven", "Tangleroots");

	static ProgressExtractor.Progress p(double c, double m) {
		return new ProgressExtractor.Progress(c, m);
	}

	static ObjectiveInfo obj(String line) {
		return new ObjectiveInfo(List.of(new ObjectiveInfo.Sub(line, null)), false, false);
	}

	private TrackerStore store() {
		return new TrackerStore(dir.resolve("t.json"));
	}

	@Test void theStoreReportsLocalCountsAndTakeBacksButNotReads() {
		TrackerStore s = store();
		s.update("pquests", "Miner", p(100, 300), 0);
		s.setObjective("pquests:Miner", obj("Mine 300 Stone"));
		List<String> events = new ArrayList<>();
		s.setProgressListener((key, units) -> events.add(key + " " + units));
		s.addEstimate("pquests:Miner", 3, 1_000);
		s.reverseEstimate("pquests:Miner", 1);
		s.reverseEstimate("pquests:Miner", 5); // only the 2 left are taken back
		s.addEstimate("pquests:Miner", 0, 1_000); // nothing added: no event
		s.update("pquests", "Miner", p(150, 300), 2_000); // a menu read
		assertEquals(List.of("pquests:Miner 3", "pquests:Miner -1", "pquests:Miner -2"), events);
	}

	@Test void aFailingListenerDoesNotBreakCounting() {
		TrackerStore s = store();
		s.update("pquests", "Miner", p(100, 300), 0);
		s.setProgressListener((key, units) -> { throw new IllegalStateException("boom"); });
		assertTrue(s.addEstimate("pquests:Miner", 1, 1_000));
		assertEquals(1, s.counted("pquests:Miner"));
	}

	@Test void anEntryIsNamedAndCountedAsTheTrackerPanelShowsIt() {
		TrackerStore s = store();
		s.update("pquests", "Wolf Hunter", p(10, 74), 0);
		s.setObjective("pquests:Wolf Hunter", obj("Slay 74 Mana Wolves"));
		s.addEstimate("pquests:Wolf Hunter", 1, 1_000);
		ProgressLabel l = ProgressLabel.of(s, "pquests:Wolf Hunter", WORLDS).orElseThrow();
		assertEquals("Wolf Hunter", l.name());
		assertEquals("~11/74", l.count());
		assertFalse(l.done());
		TrackerPanelModel.Line line = TrackerPanelModel.build(s.hudSections(10, true, null, WORLDS,
				net.mage.cubewheel.tracker.local.WorldScope.Mode.OFF, null), id -> s.objective(id).orElse(null), 0.8,
				WORLDS, 1_000).get(0);
		assertEquals(line.text(), l.name());
		assertEquals(line.right(), l.count());
	}

	@Test void aJobIsNamedByWhatItAsksFor() {
		TrackerStore s = store();
		s.update("jobs", "Farming Heavy · Harvest Acacia Logs", p(492, 630), 0);
		s.setObjective("jobs:Farming Heavy · Harvest Acacia Logs", obj("Harvest Acacia Logs 492/630"));
		s.addEstimate("jobs:Farming Heavy · Harvest Acacia Logs", 1, 1_000);
		ProgressLabel l = ProgressLabel.of(s, "jobs:Farming Heavy · Harvest Acacia Logs", WORLDS).orElseThrow();
		assertEquals("Acacia Logs", l.name());
		assertEquals("~493/630", l.count());
		JobsPanelModel.Line row = JobsPanelModel.build(s.rows(true), null, id -> s.objective(id).orElse(null), null,
				WORLDS, 1_000).lines().get(1); // under the "⚒ Farming" heading
		assertEquals(row.text(), l.name());
		assertEquals(row.right(), l.count());
	}

	@Test void reachingTheTargetIsDoneAndDropsThePanelsQueryMark() {
		TrackerStore s = store();
		s.update("pquests", "Wolf Hunter", p(73, 74), 0);
		s.setObjective("pquests:Wolf Hunter", obj("Slay 74 Mana Wolves"));
		s.addEstimate("pquests:Wolf Hunter", 1, 1_000);
		ProgressLabel l = ProgressLabel.of(s, "pquests:Wolf Hunter", WORLDS).orElseThrow();
		assertTrue(l.done());
		assertEquals("~74/74", l.count());
	}

	@Test void anObjectiveOfAMultiObjectiveQuestIsNamedAsItsRow() {
		TrackerStore s = store();
		String id = "pquests:King of the Jungle";
		s.update("pquests", "King of the Jungle", p(30, 100), 0);
		s.setObjective(id, new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Slay 2,500 Tangleroot Monsters", 10.0),
				new ObjectiveInfo.Sub("Slay 10 Golden Knights", 50.0)), false, false));
		String key = TrackerStore.subKey(id, 1);
		s.addEstimate(key, 1, 1_000);
		ProgressLabel l = ProgressLabel.of(s, key, WORLDS).orElseThrow();
		assertEquals("Golden Knights", l.name());
		assertEquals("~6/10", l.count());
		assertFalse(l.done());
		s.addEstimate(key, 4, 1_000);
		ProgressLabel done = ProgressLabel.of(s, key, WORLDS).orElseThrow();
		assertEquals("~10/10", done.count());
		assertTrue(done.done());
	}

	@Test void aCompletedQuestStepFromChatIsDone() {
		TrackerStore s = store();
		String id = "pquests:Brewer";
		s.update("pquests", "Brewer", p(0, 1), 0);
		s.setObjective(id, obj("Complete the Volcano Potion Quest"));
		List<LocalCounter.Contribution> added = LocalCounter.questCompleted("Volcano Potion Quest", s, 1_000);
		assertEquals(1, added.size());
		assertTrue(ProgressLabel.of(s, id, WORLDS).orElseThrow().done());
	}

	@Test void unknownKeysHaveNoLabel() {
		TrackerStore s = store();
		assertTrue(ProgressLabel.of(s, "pquests:Nope", WORLDS).isEmpty());
		assertTrue(ProgressLabel.of(s, null, WORLDS).isEmpty());
		assertTrue(ProgressLabel.of(null, "x", WORLDS).isEmpty());
	}
}
