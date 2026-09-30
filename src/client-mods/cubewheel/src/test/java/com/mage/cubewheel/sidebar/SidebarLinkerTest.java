package com.mage.cubewheel.sidebar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mage.cubewheel.tracker.ContainerScanner;
import com.mage.cubewheel.tracker.ContainerScanner.ItemView;
import com.mage.cubewheel.tracker.ProgressExtractor.Progress;
import com.mage.cubewheel.tracker.Trackable;
import com.mage.cubewheel.tracker.TrackerStore;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SidebarLinkerTest {
	@TempDir Path dir;

	static final Map<String, String> LINKS = Map.of("Skills", "(?i)skill level");

	/** The real /prestige rank item (captured 2026-09-30) seen an hour ago. */
	private TrackerStore storeWithPrestigeRank(long seenAt) {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		ContainerScanner.scan("prestige", List.of(new ItemView(14, "minecraft:paper", "Rank [✪4]",
				List.of("", "OBJECTIVE", "Reach 2,500 Skill Level [Lvl 1851/2,500]", "", "➟ Click to rankup"))), store, seenAt);
		return store;
	}

	private static Trackable find(TrackerStore store, String name) {
		return store.all().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
	}

	@Test void skillsValueUpdatesPrestigeObjective() {
		TrackerStore store = storeWithPrestigeRank(0);
		int n = SidebarLinker.apply(Map.of("Skills", 1860.0), LINKS, store, 3_600_000);
		assertEquals(1, n);
		Trackable t = find(store, "Rank [✪4] · Reach 2,500 Skill Level");
		assertEquals(1860, t.current(), 1e-9);
		assertEquals(2500, t.max(), 1e-9);
		assertEquals(3_600_000, t.seenAt()); // fresh
	}

	@Test void keepsMaxAndStillMarksFresh() {
		TrackerStore store = storeWithPrestigeRank(0);
		assertEquals(1, SidebarLinker.apply(Map.of("Skills", 1700.0), LINKS, store, 5_000));
		Trackable t = find(store, "Rank [✪4] · Reach 2,500 Skill Level");
		assertEquals(1851, t.current(), 1e-9);
		assertEquals(5_000, t.seenAt());
	}

	@Test void completeEntriesAreLeftAlone() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		store.update("prestige", "Rank [✪2] · Reach 1,000 Skill Level", new Progress(1000, 1000), 0);
		assertEquals(0, SidebarLinker.apply(Map.of("Skills", 1860.0), LINKS, store, 10));
		assertEquals(0, store.all().get(0).seenAt());
		assertEquals(1000, store.all().get(0).current(), 1e-9);
	}

	@Test void reachingMaxCompletesTheEntry() {
		TrackerStore store = storeWithPrestigeRank(0);
		SidebarLinker.apply(Map.of("Skills", 2600.0), LINKS, store, 10);
		assertTrue(find(store, "Rank [✪4] · Reach 2,500 Skill Level").complete());
	}

	@Test void unrelatedKeysAndNamesDoNothing() {
		TrackerStore store = storeWithPrestigeRank(0);
		store.update("jobs", "Carrot Grind", new Progress(10, 100), 0);
		assertEquals(0, SidebarLinker.apply(Map.of("Money", 2.89e6), LINKS, store, 10));
		Map<String, String> links = new LinkedHashMap<>(LINKS);
		links.put("Mana", "(?i)nothing matches this");
		assertEquals(0, SidebarLinker.apply(Map.of("Mana", 5.0), links, store, 10));
		assertEquals(10, find(store, "Carrot Grind").current(), 1e-9);
	}

	@Test void keyMatchIsCaseInsensitiveAndBadRegexIsSkipped() {
		TrackerStore store = storeWithPrestigeRank(0);
		Map<String, String> links = new LinkedHashMap<>();
		links.put("Broken", "(unclosed");
		links.put("skills", "(?i)skill level");
		assertEquals(1, SidebarLinker.apply(Map.of("Broken", 1.0, "Skills", 1900.0), links, store, 10));
	}

	@Test void nullsAreTolerated() {
		TrackerStore store = storeWithPrestigeRank(0);
		assertEquals(0, SidebarLinker.apply(null, LINKS, store, 1));
		assertEquals(0, SidebarLinker.apply(Map.of("Skills", 1.0), null, store, 1));
		assertEquals(0, SidebarLinker.apply(Map.of("Skills", 1.0), LINKS, null, 1));
	}

	@Test void changedKeysComparesSnapshots() {
		Map<String, Double> before = Map.of("Money", 1.0, "Skills", 1851.0);
		Map<String, Double> after = Map.of("Money", 2.0, "Skills", 1851.0, "Souls", 5.0);
		assertEquals(Map.of("Money", 2.0, "Souls", 5.0), SidebarLinker.changed(before, after));
		assertEquals(after, SidebarLinker.changed(Map.of(), after));
	}

	@Test void saveThrottleWritesAtMostEveryInterval() {
		SidebarLinker.SaveThrottle t = new SidebarLinker.SaveThrottle(10_000);
		assertEquals(false, t.shouldSave(0));   // nothing dirty
		t.markDirty();
		assertEquals(true, t.shouldSave(0));
		t.markDirty();
		assertEquals(false, t.shouldSave(5_000));
		assertEquals(true, t.shouldSave(10_000));
		assertEquals(false, t.shouldSave(20_000)); // clean again
	}
}
