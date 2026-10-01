package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import net.mage.cubewheel.tracker.TrackerPanelModel.Line;
import net.mage.cubewheel.tracker.TrackerPanelModel.Tone;
import net.mage.cubewheel.tracker.local.ObjectiveInfo;
import net.mage.cubewheel.tracker.local.WorldInfo;
import net.mage.cubewheel.tracker.local.WorldScope.Mode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Tracker panel under the Jobs panel, in the same compact style: source marker, short title, short count;
 * group headings only when there is more than one group; objective rows under multi-objective quests.
 */
class TrackerPanelModelTest {
	@TempDir Path dir;
	static final long NOW = 10 * 60_000L;
	static final List<String> WORLDS = TrackerStoreWorldFilterTest.WORLDS;
	static final String SKILL = "prestige:Rank [✪4] · Reach 2,500 Skill Level";
	static final String PARTY = "prestige:Rank [✪9] · Reach Party Level 55";
	static final String JUNGLE = "pquests:King of the Jungle";
	static final String PURSUIT = "pquests:Jungle Pursuit";

	TrackerStore store() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("prestige", "Rank [✪4] · Reach 2,500 Skill Level", new ProgressExtractor.Progress(1902, 2500), 0);
		s.update("prestige", "Rank [✪9] · Reach Party Level 55", new ProgressExtractor.Progress(12, 55), 0);
		s.update("pquests", "King of the Jungle", new ProgressExtractor.Progress(5, 100), 0);
		// Real King of the Jungle lore (2026-09-30): the menu only gives a percentage per objective.
		s.setObjective(JUNGLE, new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Slay 2,500 Tangleroot Monsters", 2.0),
				new ObjectiveInfo.Sub("Slay 10 Golden Knights", 50.0)), false, false));
		s.update("pquests", "Jungle Pursuit", new ProgressExtractor.Progress(0, 15000), 0);
		s.setObjective(PURSUIT, TrackerStoreWorldFilterTest.obj("Mine 0/15,000 Tangleroots Resources"));
		return s;
	}

	static List<Line> build(TrackerStore s, int max, WorldInfo at, Mode mode) {
		return TrackerPanelModel.build(s.hudSections(max, false, at, WORLDS, mode), id -> s.objective(id).orElse(null),
				0.8, WORLDS, NOW);
	}

	/** "tag text | right", trimmed. */
	static List<String> texts(List<Line> lines) {
		return lines.stream().map(l -> (l.tag() + " " + l.text() + " | " + l.right()).trim()).toList();
	}

	@Test void compactRowsFromRealNamesWithObjectiveRowsAndNoHeadingForOneGroup() {
		List<Line> lines = build(store(), 10, WorldInfo.UNKNOWN, Mode.SORT);
		assertEquals(List.of(
				"✦ ✪4 Skill Level | 1,902/2,500",
				"✦ ✪9 Party Level 55 | 12/55",
				"⚑ King of the Jungle | 5/100",
				"↳ Monsters | 50/2,500",
				"↳ Golden Knights | 5/10",
				"⚑ Jungle Pursuit | 0/15k"), texts(lines));
		assertEquals(Tone.DETAIL, lines.get(3).tone());
		assertEquals(TrackerPanelModel.DETAIL_INDENT + "Golden Knights", lines.get(4).text());
		assertEquals("5/10", lines.get(4).right());
		assertEquals(0xFFFF55FF, lines.get(0).tagColor()); // prestige's marker colour
		assertEquals(Tone.NORMAL, lines.get(0).tone());
	}

	@Test void objectiveWithoutANumberCountsAsOneStep() {
		// Mob Experience: "0% → Complete the Volcano Potion Quest", "40% → Participate in Slaying 5 Lava Beasts".
		assertEquals(List.of("Volcano Potion Quest", "0/1"),
				List.of(TrackerPanelModel.detailName(new ObjectiveInfo.Sub("Complete the Volcano Potion Quest", 0.0), List.of()),
						TrackerPanelModel.detailCount(new ObjectiveInfo.Sub("Complete the Volcano Potion Quest", 0.0))));
		assertEquals(List.of("Lava Beasts", "2/5"),
				List.of(TrackerPanelModel.detailName(new ObjectiveInfo.Sub("Participate in Slaying 5 Lava Beasts", 40.0), List.of()),
						TrackerPanelModel.detailCount(new ObjectiveInfo.Sub("Participate in Slaying 5 Lava Beasts", 40.0))));
		assertEquals("", TrackerPanelModel.detailCount(new ObjectiveInfo.Sub("Slay 10 Golden Knights", null)));
	}

	@Test void headingsOnlyWithMoreThanOneGroup() {
		TrackerStore s = store();
		s.togglePin(PURSUIT);
		List<String> t = texts(build(s, 10, WorldInfo.UNKNOWN, Mode.SORT));
		assertEquals("— Pinned |", t.get(0));
		assertEquals("⚑ Jungle Pursuit | 0/15k", t.get(1));
		assertEquals("— Anywhere |", t.get(2));
		assertEquals(Tone.HEADING, build(s, 10, WorldInfo.UNKNOWN, Mode.SORT).get(0).tone());
	}

	@Test void capCountsEntriesNotObjectiveRows() {
		List<Line> lines = build(store(), 2, WorldInfo.UNKNOWN, Mode.SORT);
		assertEquals(2, lines.stream().filter(l -> !l.tag().isEmpty()).count());
		assertTrue(lines.stream().noneMatch(l -> l.tone() == Tone.HEADING));
	}

	@Test void completeIsDoneNearIsYellowAndOldReadsShowTheirAge() {
		List<TrackerRow> rows = List.of(
				TrackerRow.plain(new Trackable("pquests:Haven Harvester", "pquests", "Haven Harvester", 10, 10, 0)),
				TrackerRow.plain(new Trackable("challenges:Fisher", "challenges", "Fisher", 9, 10, 0)),
				TrackerRow.plain(new Trackable("challenges:Miner", "challenges", "Miner", 1, 10, 0)));
		List<Line> lines = TrackerPanelModel.build(List.of(new TrackerStore.HudSection(TrackerStore.HudSection.Kind.ANYWHERE,
				rows)), id -> null, 0.8, WORLDS, 3 * 3_600_000L);
		assertEquals(List.of("⚑ Haven Harvester | 10/10 ✓", "★ Fisher | 9/10", "★ Miner | 1/10"), texts(lines));
		assertEquals(List.of(Tone.DONE, Tone.NEAR, Tone.NORMAL), lines.stream().map(Line::tone).toList());
	}

	@Test void titlesAreShortened() {
		assertEquals("✪4 Skill Level", TrackerPanelModel.title("prestige", "Rank [✪4] · Reach 2,500 Skill Level", WORLDS));
		assertEquals("✪9 Party Level 55", TrackerPanelModel.title("prestige", "Rank [✪9] · Reach Party Level 55", WORLDS));
		assertEquals("Jungle Pursuit",
				TrackerPanelModel.title("pquests", "Jungle Pursuit · Mine 15,000 Tangleroots Resources", WORLDS));
		assertEquals("King of the Jungle", TrackerPanelModel.title("pquests", "King of the Jungle", WORLDS));
		assertEquals("Cherry Logs", TrackerPanelModel.title("jobs", "Farming Heavy · Harvest Cherry Logs", WORLDS));
		String longName = TrackerPanelModel.title("challenges", "A Very Long Challenge Name Indeed", WORLDS);
		assertEquals(TrackerPanelModel.MAX_TITLE, longName.length());
		assertTrue(longName.endsWith("…"));
	}

	@Test void otherWorldsAreDimmedAndJobsSkippedWhenTheJobsPanelShowsThem() {
		TrackerStoreWorldFilterTest f = new TrackerStoreWorldFilterTest();
		f.dir = dir;
		TrackerStore s = f.store();
		List<Line> lines = TrackerPanelModel.build(s.hudSections(10, false, TrackerStoreWorldFilterTest.TANGLEROOT,
				WORLDS, Mode.SORT, JobsPanelModel::isJob), id -> s.objective(id).orElse(null), 0.8, WORLDS, NOW);
		assertTrue(lines.stream().noneMatch(l -> l.tag().equals("⚒")));
		List<Line> all = TrackerPanelModel.build(s.hudSections(10, false, TrackerStoreWorldFilterTest.TANGLEROOT,
				WORLDS, Mode.SORT), id -> s.objective(id).orElse(null), 0.9, WORLDS, NOW);
		List<String> t = texts(all);
		int other = t.indexOf("— Other worlds |");
		assertTrue(other > 0);
		assertEquals("⚒ Rattle Snakes | 50/58", t.get(other + 1));
		assertEquals(Tone.OTHER_WORLD, all.get(other + 1).tone());
	}
}
