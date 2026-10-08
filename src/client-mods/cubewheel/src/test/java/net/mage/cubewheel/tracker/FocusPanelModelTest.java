package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.mage.cubewheel.tracker.FocusPanelModel.Focus;
import net.mage.cubewheel.tracker.local.Estimate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Focus panel: the last-credited entry in full, with a big bar, pace and rewards (Michael 2026-10-08). */
class FocusPanelModelTest {
	@TempDir Path dir;
	static final List<String> WORLDS = TrackerStoreWorldFilterTest.WORLDS;
	static final String SCUTTLER = "jobs:Hunting Experienced · Slay Void Scuttler in Morend";

	TrackerStore store() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Hunting Experienced · Slay Void Scuttler in Morend", new ProgressExtractor.Progress(10, 73), 0);
		s.setObjective(SCUTTLER, TrackerStoreWorldFilterTest.obj("Slay 10/73 Void Scuttler in Morend"));
		s.setRewards(SCUTTLER, List.of("40 Job XP (Hunting)", "$70,000"));
		s.update("pquests", "Fisherman", new ProgressExtractor.Progress(60, 100), 0);
		s.setObjective("pquests:Fisherman", TrackerStoreWorldFilterTest.obj("Catch 100 Fish"));
		return s;
	}

	@Test void aJobListingInFull() {
		TrackerStore s = store();
		Focus f = FocusPanelModel.of(s, SCUTTLER, WORLDS, 0).orElseThrow();
		assertEquals("Hunting · Experienced", f.heading());
		assertEquals("Slay Void Scuttler in Morend", f.objective());
		assertEquals("10/73", f.count());
		assertEquals(13, f.percent());
		assertFalse(f.done());
		assertEquals("", f.pace()); // nothing counted yet
		assertEquals(List.of("40 Job XP (Hunting)", "$70,000"), f.rewards());
	}

	@Test void aQuestUsesItsNameAsTheHeading() {
		Focus f = FocusPanelModel.of(store(), "pquests:Fisherman", WORLDS, 0).orElseThrow();
		assertEquals("Fisherman", f.heading());
		assertEquals("Catch 100 Fish", f.objective());
		assertEquals("60/100", f.count());
		assertEquals(60, f.percent());
		assertEquals(List.of(), f.rewards());
	}

	@Test void paceComesFromTheEstimateOnceThereIsEnough() {
		TrackerStore s = store();
		long t0 = 1_000_000;
		s.addEstimate(SCUTTLER, 1, t0);
		s.addEstimate(SCUTTLER, 1, t0 + 20_000);
		assertEquals("", FocusPanelModel.of(s, SCUTTLER, WORLDS, t0 + 20_000).orElseThrow().pace()); // 2 units
		s.addEstimate(SCUTTLER, 1, t0 + 60_000); // 3 units in 60 s: 3/min, 60 left: ~20 min
		Focus f = FocusPanelModel.of(s, SCUTTLER, WORLDS, t0 + 60_000).orElseThrow();
		assertEquals("~13/73", f.count());
		assertEquals("3.0/min · ~20 min left", f.pace());
		assertTrue(f.fraction() > 0.17 && f.fraction() < 0.18);
	}

	@Test void paceNeedsThirtySecondsAndStopsWhenDone() {
		Estimate quick = new Estimate(5, 0, 0, 10_000);
		assertEquals("", FocusPanelModel.pace(quick, 5, 73, 10_000));
		Estimate fine = new Estimate(12, 0, 0, 60_000);
		assertEquals("12/min · ~5 min left", FocusPanelModel.pace(fine, 13, 73, 60_000));
		assertEquals("", FocusPanelModel.pace(fine, 73, 73, 60_000));
		assertEquals("", FocusPanelModel.pace(null, 1, 73, 0));
	}

	@Test void durations() {
		assertEquals("45 s", FocusPanelModel.duration(45_000));
		assertEquals("19 min", FocusPanelModel.duration(19 * 60_000 + 30_000));
		assertEquals("1 h 20 min", FocusPanelModel.duration(80 * 60_000));
		assertEquals("2 h", FocusPanelModel.duration(120 * 60_000));
		assertEquals("2 d 3 h", FocusPanelModel.duration(51 * 3_600_000L));
		assertEquals("3.2", FocusPanelModel.rate(3.24));
		assertEquals("12", FocusPanelModel.rate(12.4));
	}

	@Test void unknownKeysGiveNothing() {
		assertEquals(Optional.empty(), FocusPanelModel.of(store(), "jobs:nope", WORLDS, 0));
		assertEquals(Optional.empty(), FocusPanelModel.of(null, SCUTTLER, WORLDS, 0));
	}
}
