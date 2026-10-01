package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import net.mage.cubewheel.tracker.JobsPanelModel.Line;
import net.mage.cubewheel.tracker.JobsPanelModel.Model;
import net.mage.cubewheel.tracker.JobsPanelModel.Tone;
import net.mage.cubewheel.tracker.local.WorldInfo;
import net.mage.cubewheel.tracker.local.WorldScope;
import net.mage.cubewheel.tracker.local.WorldScope.Mode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The left-hand Jobs panel: every job listing, grouped by industry and tier, crate in the title. */
class JobsPanelModelTest {
	@TempDir Path dir;
	static final long NOW = 10 * 60_000L;

	TrackerStore store() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Mining Heavy · Mine Deepslate", new ProgressExtractor.Progress(10, 100), 0);
		s.update("jobs", "Farming Heavy · Harvest Cherry Logs", new ProgressExtractor.Progress(3127, 4773), 0);
		s.update("jobs", "Farming Beginner · Harvest Wheat", new ProgressExtractor.Progress(64, 64), 0); // done
		s.update("jobs", "Farming Experienced · Harvest or Mine Wolfhaven Resources", new ProgressExtractor.Progress(1, 10), 0);
		s.update("jobs", "Hunting Beginner · Slay Tigers in Tangleroots", new ProgressExtractor.Progress(16, 64), 0);
		s.setObjective("jobs:Hunting Beginner · Slay Tigers in Tangleroots",
				TrackerStoreWorldFilterTest.obj("Slay 16/64 Tigers in Tangleroots"));
		s.update("jobs", "Hunting Experienced · Slay Rattle Snakes in Sandara", new ProgressExtractor.Progress(5, 58), 0);
		s.update("jobs", "Cooking Heavy · Cook Fish", new ProgressExtractor.Progress(1, 2), 0); // unknown industry
		s.update("jobs", "GOLDEN CRATE", new ProgressExtractor.Progress(4, 5), 0);
		s.update("pquests", "Miner", new ProgressExtractor.Progress(100, 300), 0); // not a job
		return s;
	}

	static Model build(TrackerStore s, WorldInfo at) {
		return JobsPanelModel.build(s.rows(true), s::isHidden, id -> s.objective(id).orElse(null),
				t -> WorldScope.relevance(WorldScope.of(t.name(), s.objective(t.id()).orElse(null),
						TrackerStoreWorldFilterTest.WORLDS), at), NOW);
	}

	static List<String> texts(Model m) {
		return m.lines().stream().map(Line::text).toList();
	}

	@Test void groupsByIndustryInFixedOrderThenTiersUncapped() {
		Model m = build(store(), WorldInfo.UNKNOWN);
		assertEquals("Jobs · Golden Crate 4/5", m.title());
		assertEquals(List.of(
				"⚒ Farming",
				"  Beginner · Harvest Wheat  64 / 64 (100%) · 10m · hand in",
				"  Experienced · Harvest or Mine Wolfhaven Resources  1 / 10 (10%) · 10m",
				"  Heavy · Harvest Cherry Logs  3,127 / 4,773 (66%) · 10m",
				"⚒ Hunting",
				"  Beginner · Slay Tigers in Tangleroots  16 / 64 (25%) · 10m",
				"  Experienced · Slay Rattle Snakes in Sandara  5 / 58 (9%) · 10m",
				"⚒ Mining",
				"  Heavy · Mine Deepslate  10 / 100 (10%) · 10m",
				"⚒ Cooking",
				"  Heavy · Cook Fish  1 / 2 (50%) · 10m"), texts(m));
		assertEquals(Tone.DONE, m.lines().get(1).tone());
		assertEquals(Tone.INDUSTRY, m.lines().get(0).tone());
		assertEquals(Tone.NEUTRAL, m.lines().get(2).tone());
	}

	@Test void currentWorldIsHighlightedAndOtherWorldsDimmed() {
		Model m = build(store(), TrackerStoreWorldFilterTest.TANGLEROOT);
		List<String> t = texts(m);
		assertEquals(Tone.CURRENT, m.lines().get(t.indexOf("  Beginner · Slay Tigers in Tangleroots  16 / 64 (25%) · 10m")).tone());
		assertEquals(Tone.OTHER_WORLD, m.lines().get(t.indexOf("  Experienced · Slay Rattle Snakes in Sandara  5 / 58 (9%) · 10m")).tone());
		assertEquals(Tone.OTHER_WORLD,
				m.lines().get(t.indexOf("  Experienced · Harvest or Mine Wolfhaven Resources  1 / 10 (10%) · 10m")).tone());
		assertEquals(Tone.NEUTRAL, m.lines().get(t.indexOf("  Heavy · Mine Deepslate  10 / 100 (10%) · 10m")).tone());
	}

	@Test void hiddenEntriesAndTheCrateStayHidden() {
		TrackerStore s = store();
		s.toggleHidden("jobs:Mining Heavy · Mine Deepslate");
		s.toggleHidden("jobs:GOLDEN CRATE");
		Model m = build(s, WorldInfo.UNKNOWN);
		assertEquals("Jobs", m.title());
		assertFalse(texts(m).contains("⚒ Mining"));
		assertTrue(texts(m).stream().noneMatch(l -> l.contains("Deepslate")));
	}

	@Test void estimatesShowTildeAndAtCapMark() {
		TrackerStore s = store();
		String id = "jobs:Cooking Heavy · Cook Fish";
		s.addEstimate(id, 1, NOW);
		Model m = build(s, WorldInfo.UNKNOWN);
		Line cook = m.lines().get(texts(m).indexOf("⚒ Cooking") + 1);
		assertEquals("  Heavy · Cook Fish  ~2 / 2 (99%) ✓? · 10m", cook.text());
		assertEquals(Tone.AT_CAP, cook.tone());
	}

	@Test void nonListingJobEntriesGoToOtherLast() {
		TrackerStore s = new TrackerStore(dir.resolve("o.json"));
		s.update("jobs", "Heavy · Harvest Cherry Logs", new ProgressExtractor.Progress(1, 4), 0);
		s.update("jobs", "Weekly Bonus", new ProgressExtractor.Progress(1, 3), 0);
		s.update("jobs", "Fishing Beginner · Catch Cod", new ProgressExtractor.Progress(1, 2), 0);
		Model m = build(s, WorldInfo.UNKNOWN);
		assertEquals("Jobs", m.title());
		assertEquals(List.of("⚒ Fishing", "  Beginner · Catch Cod  1 / 2 (50%) · 10m",
				"⚒ Other", "  Heavy · Harvest Cherry Logs  1 / 4 (25%) · 10m", "  Weekly Bonus  1 / 3 (33%) · 10m"), texts(m));
	}

	@Test void noJobsMeansNoLines() {
		TrackerStore s = new TrackerStore(dir.resolve("e.json"));
		s.update("pquests", "Miner", new ProgressExtractor.Progress(1, 3), 0);
		assertTrue(build(s, WorldInfo.UNKNOWN).lines().isEmpty());
	}

	@Test void trackerHudSkipsJobEntriesPinnedOrNotWhenAsked() {
		TrackerStore s = store();
		s.togglePin("jobs:Mining Heavy · Mine Deepslate");
		List<TrackerRow> all = s.hudRows(20, false, null, List.of(), Mode.OFF);
		assertTrue(all.stream().anyMatch(r -> JobsPanelModel.isJob(r.item())));
		List<TrackerRow> rows = s.hudSections(1, false, null, List.of(), Mode.OFF, JobsPanelModel::isJob)
				.stream().flatMap(sec -> sec.rows().stream()).toList();
		assertEquals(List.of("pquests:Miner"), TrackerStoreWorldFilterTest.ids(rows)); // and jobs take no line of the cap
	}
}
