package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import net.mage.cubewheel.tracker.local.CounterRule;
import net.mage.cubewheel.tracker.local.ObjectiveInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Multi-objective quests count each objective on its own; met prestige ranks behind an unmet one say so. */
class SubObjectiveTest {
	@TempDir Path dir;
	static final List<String> WORLDS = List.of("wolfhaven", "tangleroots", "sandara", "icehaven", "morend", "burninglands");

	private static ProgressExtractor.Progress p(double c, double m) {
		return new ProgressExtractor.Progress(c, m);
	}

	private TrackerStore kingOfTheJungle() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("pquests", "King of the Jungle", p(41, 100), 0);
		s.setObjective(Trackable.idOf("pquests", "King of the Jungle"), new ObjectiveInfo(List.of(
				new ObjectiveInfo.Sub("Slay 2,500 Tangleroot Monsters", 13.0),
				new ObjectiveInfo.Sub("Slay 10 Golden Knights", 70.0)), false, false));
		return s;
	}

	@Test void eachObjectiveGetsItsOwnRule() {
		TrackerStore s = kingOfTheJungle();
		String id = Trackable.idOf("pquests", "King of the Jungle");
		var rules = s.activeRules(List.of("tangleroot"));
		assertEquals(CounterRule.Kind.KILL, rules.get(TrackerStore.subKey(id, 0)).kind());
		assertEquals(CounterRule.Kind.KILL, rules.get(TrackerStore.subKey(id, 1)).kind());
		assertTrue(s.addEstimate(TrackerStore.subKey(id, 0), 5, 1_000));
		assertEquals(5, s.counted(TrackerStore.subKey(id, 0)));
		assertEquals(Activity.ACTIVE, s.activity(id, 2_000)); // progress on an objective marks the quest
	}

	/** A count on an objective lights its own row as well as the quest's ("↳ Monsters" never lit before). */
	@Test void aCountOnAnObjectiveMarksTheObjectiveAndTheQuest() {
		TrackerStore s = kingOfTheJungle();
		String id = Trackable.idOf("pquests", "King of the Jungle");
		assertEquals(Activity.NONE, s.activity(TrackerStore.subKey(id, 0), 2_000));
		assertTrue(s.addEstimate(TrackerStore.subKey(id, 0), 5, 1_000));
		assertEquals(Activity.ACTIVE, s.activity(TrackerStore.subKey(id, 0), 2_000));
		assertEquals(Activity.ACTIVE, s.activity(id, 2_000));
		assertEquals(Activity.NONE, s.activity(TrackerStore.subKey(id, 1), 2_000)); // the other objective did nothing
		assertEquals(Activity.RECENT, s.activity(TrackerStore.subKey(id, 0), 1_000 + Activity.ACTIVE_MS));
	}

	@Test void rowsShowReadPlusCountedAndAFreshReadReplacesIt() {
		TrackerStore s = kingOfTheJungle();
		String id = Trackable.idOf("pquests", "King of the Jungle");
		s.addEstimate(TrackerStore.subKey(id, 0), 5, 1_000);
		List<TrackerPanelModel.Line> lines = TrackerPanelModel.build(
				List.of(new TrackerStore.HudSection(TrackerStore.HudSection.Kind.ANYWHERE, s.rows(true))),
				k -> s.objective(k).orElse(null), 0.9, WORLDS, 2_000, null, s::counted);
		assertEquals("~330/2,500", lines.get(1).right()); // 13% of 2,500 = 325, +5
		assertEquals("7/10", lines.get(2).right());
		assertTrue(lines.get(1).progress() > 0.13 && lines.get(1).progress() < 0.14);
		// The next /pquests read (same percents) is the truth again.
		s.setObjective(id, s.objective(id).orElseThrow());
		assertEquals(0, s.counted(TrackerStore.subKey(id, 0)));
	}

	@Test void metPrestigeRanksBehindAnUnmetOneAreBlocked() {
		TrackerStore s = new TrackerStore(dir.resolve("p.json"));
		s.update("prestige", "Rank [✪4] · Reach 2,500 Skill Level", p(2065, 2500), 0);
		s.update("prestige", "Rank [✪8] · Catch 1,000 Fish", p(1000, 1000), 0);
		s.update("prestige", "Rank [✪3] · Harvest 3,750 Resources", p(3750, 3750), 0);
		List<TrackerPanelModel.Line> lines = TrackerPanelModel.build(
				List.of(new TrackerStore.HudSection(TrackerStore.HudSection.Kind.ANYWHERE, s.rows(false))),
				k -> null, 0.9, WORLDS, 0);
		TrackerPanelModel.Line fish = lines.stream().filter(l -> l.text().contains("Fish")).findFirst().orElseThrow();
		assertEquals(TrackerPanelModel.Tone.BLOCKED, fish.tone());
		assertTrue(fish.right().endsWith("after ✪4"), fish.right());
		TrackerPanelModel.Line res = lines.stream().filter(l -> l.text().contains("Resources")).findFirst().orElseThrow();
		assertEquals(TrackerPanelModel.Tone.DONE, res.tone()); // below the next rank: not blocked
	}
}
