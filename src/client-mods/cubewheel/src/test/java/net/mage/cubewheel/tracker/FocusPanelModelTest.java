package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.mage.cubewheel.tracker.FocusPanelModel.Focus;
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

	/** The event calendar (capture 2026-10-11): today's task is due in 23 h; late when the pace will miss it, or it has passed. */
	@Test void aCalendarTaskShowsItsDeadlineAndGoesLateWhenThePaceWillMissIt() {
		TrackerStore s = new TrackerStore(dir.resolve("c.json"));
		String id = "calendar:October 11th · Catch 100 Fish";
		long now = 5_000_000L;
		s.update("calendar", "October 11th · Catch 100 Fish", new ProgressExtractor.Progress(8, 100), now);
		s.setObjective(id, TrackerStoreWorldFilterTest.obj("Catch 100 Fish"));
		s.setDue(id, now + 23 * 3_600_000L);
		Focus f = FocusPanelModel.of(s, id, WORLDS, now).orElseThrow();
		assertEquals("October 11th", f.heading());
		assertEquals("Catch 100 Fish", f.objective());
		assertEquals("8/100", f.count());
		assertEquals("due in 23 h", f.due());
		assertFalse(f.late()); // no pace known yet
		// 92 fish at 0.05/min is ~30 h: later than 23 h.
		assertTrue(FocusPanelModel.of(s, id, WORLDS, now, k -> 0.05).orElseThrow().late());
		// At 10/min it is 9 min: fine.
		Focus quick = FocusPanelModel.of(s, id, WORLDS, now, k -> 10).orElseThrow();
		assertFalse(quick.late());
		assertEquals("10/min · ~9 min left", quick.pace());
		// Past the deadline.
		Focus past = FocusPanelModel.of(s, id, WORLDS, now + 24 * 3_600_000L).orElseThrow();
		assertEquals("overdue", past.due());
		assertTrue(past.late());
		// A job listing has no deadline.
		Focus job = FocusPanelModel.of(store(), SCUTTLER, WORLDS, 0).orElseThrow();
		assertEquals("", job.due());
		assertFalse(job.late());
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

	/** 2026-10-08: "catch X while fishing is pretty redundant and dropping an extra line. it is already under fishing". */
	@Test void theObjectiveDropsTheIndustryTheHeadingNames() {
		TrackerStore s = store();
		String name = "Fishing Experienced · Catch Uncommon Lobster while fishing in Jungles Biomes";
		s.update("jobs", name, new ProgressExtractor.Progress(2, 12), 0);
		s.setObjective("jobs:" + name, TrackerStoreWorldFilterTest.obj("Catch 2/12  Uncommon Lobster while fishing in Jungles Biomes"));
		Focus f = FocusPanelModel.of(s, "jobs:" + name, WORLDS, 0).orElseThrow();
		assertEquals("Fishing · Experienced", f.heading());
		assertEquals("Catch Uncommon Lobster in Jungles Biomes", f.objective());
		assertEquals("Catch Octopus", FocusPanelModel.withoutIndustry("Catch Octopus while fishing", "Fishing"));
		assertEquals("Catch Octopus while fishing", FocusPanelModel.withoutIndustry("Catch Octopus while fishing", "Hunting"));
		assertEquals("Catch Octopus while fishing", FocusPanelModel.withoutIndustry("Catch Octopus while fishing", null));
	}

	/**
	 * 2026-10-08: "i just completed this mission to get 2500 fish, and it says ready to turn in, but it is blocked by
	 * earlier prestige missions … there's no action item for me until it gets unblocked now".
	 */
	@Test void aPrestigeRankBlockedBehindALowerOneIsNotInFocus() {
		TrackerStore s = store();
		String skill = "prestige:Rank [✪4] · Reach 3,750 Skill Level";
		String fish = "prestige:Rank [✪8] · Catch 2,500 Fish";
		String crops = "prestige:Rank [✪1] · Harvest 42,500 Crops";
		s.update("prestige", "Rank [✪4] · Reach 3,750 Skill Level", new ProgressExtractor.Progress(2852, 3750), 0);
		s.update("prestige", "Rank [✪8] · Catch 2,500 Fish", new ProgressExtractor.Progress(2500, 2500), 0);
		s.update("prestige", "Rank [✪1] · Harvest 42,500 Crops", new ProgressExtractor.Progress(42500, 42500), 0);
		assertTrue(FocusPanelModel.of(s, fish, WORLDS, 0).isEmpty()); // met, but after ✪4
		assertTrue(FocusPanelModel.of(s, crops, WORLDS, 0).orElseThrow().done()); // below ✪4: claimable
		assertFalse(FocusPanelModel.of(s, skill, WORLDS, 0).orElseThrow().done());
		s.update("prestige", "Rank [✪4] · Reach 3,750 Skill Level", new ProgressExtractor.Progress(3750, 3750), 0);
		assertTrue(FocusPanelModel.of(s, fish, WORLDS, 0).orElseThrow().done()); // ✪4 met: unblocked
	}

	@Test void aQuestUsesItsNameAsTheHeading() {
		Focus f = FocusPanelModel.of(store(), "pquests:Fisherman", WORLDS, 0).orElseThrow();
		assertEquals("Fisherman", f.heading());
		assertEquals("Catch 100 Fish", f.objective());
		assertEquals("60/100", f.count());
		assertEquals(60, f.percent());
		assertEquals(List.of(), f.rewards());
	}

	/**
	 * 2026-10-12: "Slay 1,000 Sandara Monsters", hidden from the tracker and no longer a quest on the server, still
	 * read "Ready to hand in" in Focus because its kills kept crediting it. Hidden wins here too.
	 */
	@Test void aHiddenEntryIsNeverInFocus() {
		TrackerStore s = store();
		s.togglePin("pquests:Fisherman");
		s.toggleHidden("pquests:Fisherman");
		assertTrue(FocusPanelModel.of(s, "pquests:Fisherman", WORLDS, 0).isEmpty());
		assertTrue(FocusPanelModel.of(s, "pquests:Fisherman#0", WORLDS, 0).isEmpty());
		s.toggleHidden("pquests:Fisherman");
		assertEquals("Fisherman", FocusPanelModel.of(s, "pquests:Fisherman", WORLDS, 0).orElseThrow().heading());
	}

	/** 2026-10-08: the rate is the recent one ({@link Pace}), not the stored estimate's average over hours. */
	@Test void paceComesFromTheRecentRateOnceKnown() {
		TrackerStore s = store();
		long t0 = 1_000_000;
		s.addEstimate(SCUTTLER, 1, t0);
		s.addEstimate(SCUTTLER, 1, t0 + 20_000);
		s.addEstimate(SCUTTLER, 1, t0 + 60_000); // ~13/73 counted
		assertEquals("", FocusPanelModel.of(s, SCUTTLER, WORLDS, t0 + 60_000).orElseThrow().pace()); // no rate yet
		Pace pace = new Pace();
		for (int i = 0; i <= 3; i++) pace.credit(SCUTTLER, 1, t0 + i * 20_000); // 3 units over 60 s: 3/min, 60 left
		Focus f = FocusPanelModel.of(s, SCUTTLER, WORLDS, t0 + 60_000, pace::perMin).orElseThrow();
		assertEquals("~13/73", f.count());
		assertEquals("3.0/min · ~20 min left", f.pace());
		assertTrue(f.fraction() > 0.17 && f.fraction() < 0.18);
	}

	@Test void paceStopsWhenDoneOrUnknown() {
		assertEquals("12/min · ~5 min left", FocusPanelModel.pace(12, 13, 73));
		assertEquals("15/min · ~7 min left", FocusPanelModel.pace(15, 2_386, 2_500)); // 114 fish at one every 4 s
		assertEquals("", FocusPanelModel.pace(12, 73, 73));
		assertEquals("", FocusPanelModel.pace(0, 1, 73));
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
