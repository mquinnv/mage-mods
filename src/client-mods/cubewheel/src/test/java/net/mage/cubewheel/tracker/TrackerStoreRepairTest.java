package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A store written before beta.93 may hold a prestige rank several times, once per level its cut-off counter was seen
 * at ("Rank [✪4] · Reach 5,000 Skill Level [Lvl 3814/5,000", "… [Lvl 3819/5,000"; Michael 2026-11-03: "why is the
 * level 5000 quest listed twice"). The sidebar kept every copy alive. Loading folds them into the one clean entry.
 */
class TrackerStoreRepairTest {
	@TempDir Path dir;

	static final String STALE_A = "prestige:Rank [✪4] · Reach 5,000 Skill Level [Lvl 3814/5,000";
	static final String STALE_B = "prestige:Rank [✪4] · Reach 5,000 Skill Level [Lvl 3819/5,000";
	static final String CLEAN = "prestige:Rank [✪4] · Reach 5,000 Skill Level";

	String saved() {
		return """
				{"items":[
				 {"id":"%s","source":"prestige","name":"Rank [✪4] · Reach 5,000 Skill Level [Lvl 3814/5,000","current":4100.0,"max":5000.0,"seenAt":1000},
				 {"id":"%s","source":"prestige","name":"Rank [✪4] · Reach 5,000 Skill Level [Lvl 3819/5,000","current":4106.0,"max":5000.0,"seenAt":2000},
				 {"id":"prestige:Rank [✪8] · Catch 5,000 Fish","source":"prestige","name":"Rank [✪8] · Catch 5,000 Fish","current":10.0,"max":5000.0,"seenAt":2000}
				],
				 "pins":["%s"],
				 "objectives":{"%s":{"subs":[{"text":"Reach 5,000 Skill Level [Lvl 3814/5,000"}],"special":false,"handIn":false}},
				 "estimates":{"%s":{"count":3,"baseline":4100.0,"since":1000,"lastAt":1500}},
				 "rewards":{"%s":["Rankup"]},
				 "due":{}}
				""".formatted(STALE_A, STALE_B, STALE_A, STALE_A, STALE_A, STALE_B);
	}

	@Test void loadFoldsCutOffCounterCopiesIntoOneCleanEntry() throws Exception {
		Path file = dir.resolve("t.json");
		Files.writeString(file, saved());
		TrackerStore s = new TrackerStore(file);
		s.load();
		List<Trackable> all = s.all();
		assertEquals(2, all.size(), all.toString());
		Trackable t = all.stream().filter(x -> x.id().equals(CLEAN)).findFirst().orElseThrow();
		assertEquals("Rank [✪4] · Reach 5,000 Skill Level", t.name());
		assertEquals(4106, t.current(), "the furthest reading wins");
		assertEquals(2000, t.seenAt(), "the latest sighting wins");
		assertTrue(s.isPinned(CLEAN), "the pin follows");
		assertFalse(s.isPinned(STALE_A));
		assertTrue(s.objective(CLEAN).isEmpty(), "the stale objective text is dropped; the next menu read restores it");
		assertEquals(List.of("Rankup"), s.rewards(CLEAN));
	}
}
