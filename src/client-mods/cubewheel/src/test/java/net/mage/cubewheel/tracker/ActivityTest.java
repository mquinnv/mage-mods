package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActivityTest {
	@TempDir Path dir;

	private static ProgressExtractor.Progress p(double c, double m) {
		return new ProgressExtractor.Progress(c, m);
	}

	@Test void windows() {
		assertEquals(Activity.NONE, Activity.of(0, 1_000_000));
		assertEquals(Activity.ACTIVE, Activity.of(1_000_000, 1_000_000 + Activity.ACTIVE_MS - 1));
		assertEquals(Activity.RECENT, Activity.of(1_000_000, 1_000_000 + Activity.ACTIVE_MS));
		assertEquals(Activity.NONE, Activity.of(1_000_000, 1_000_000 + Activity.RECENT_MS));
	}

	@Test void storeMarksProgressNotMereReads() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		long t0 = 10_000_000;
		s.update("jobs", "Milk Cows", p(15, 25), t0);
		String id = Trackable.idOf("jobs", "Milk Cows");
		assertEquals(Activity.NONE, s.activity(id, t0)); // first sight is not progress
		s.update("jobs", "Milk Cows", p(15, 25), t0 + 60_000);
		assertEquals(Activity.NONE, s.activity(id, t0 + 60_000)); // same value re-read
		s.addEstimate(id, 1, t0 + 120_000); // a local count
		assertEquals(Activity.ACTIVE, s.activity(id, t0 + 130_000));
		assertEquals(Activity.RECENT, s.activity(id, t0 + 120_000 + Activity.ACTIVE_MS + 1));

		s.update("jobs", "Iron", p(0, 201), t0);
		String iron = Trackable.idOf("jobs", "Iron");
		s.update("jobs", "Iron", p(4, 201), t0 + 5_000); // a higher server value
		assertEquals(Activity.ACTIVE, s.activity(iron, t0 + 6_000));

		s.update("pquests", "Skill", p(10, 100), t0);
		s.applyLiveValue(Pattern.compile("Skill"), 12, t0 + 7_000);
		assertEquals(Activity.ACTIVE, s.activity(Trackable.idOf("pquests", "Skill"), t0 + 8_000));
	}

	@Test void modelsCarryActivityOnlyOnEntryLines() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Farming Experienced · Milk Cows", p(15, 25), 0);
		s.update("jobs", "Mining Experienced · Mine Sandara Iron", p(0, 201), 0);
		String milk = Trackable.idOf("jobs", "Farming Experienced · Milk Cows");
		JobsPanelModel.Model m = JobsPanelModel.build(s.rows(false), id -> false, id -> null, null, List.of(), 0,
				id -> id.equals(milk) ? Activity.ACTIVE : Activity.NONE);
		List<Activity> acts = m.lines().stream().map(JobsPanelModel.Line::activity).toList();
		assertEquals(1, acts.stream().filter(a -> a == Activity.ACTIVE).count());
		assertEquals(Activity.NONE, m.lines().get(0).activity()); // the industry heading
	}
}
