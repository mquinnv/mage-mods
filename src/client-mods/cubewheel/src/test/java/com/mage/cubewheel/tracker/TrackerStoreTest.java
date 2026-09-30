package com.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackerStoreTest {
	@TempDir Path dir;
	static ProgressExtractor.Progress p(double c, double m) { return new ProgressExtractor.Progress(c, m); }
	@Test void hudShowsPinnedThenNearIncomplete() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Low", p(1, 10), 0);
		s.update("jobs", "Near", p(9, 10), 0);
		s.update("jobs", "Done", p(10, 10), 0);
		s.update("pquests", "Pinned", p(2, 10), 0);
		s.togglePin(Trackable.idOf("pquests", "Pinned"));
		assertEquals(List.of("Pinned", "Near"), s.hudEntries(0.8, 6).stream().map(Trackable::name).toList());
		assertEquals(List.of("Pinned"), s.hudEntries(0.8, 1).stream().map(Trackable::name).toList());
	}
	@Test void updateReplacesAndPersists() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "A", p(1, 10), 0);
		s.update("jobs", "A", p(5, 10), 100);
		s.togglePin("jobs:A");
		s.save();
		TrackerStore t = new TrackerStore(dir.resolve("t.json")); t.load();
		assertEquals(1, t.all().size());
		assertEquals(5, t.all().get(0).current(), 1e-9);
		assertTrue(t.isPinned("jobs:A"));
	}
	@Test void countSeenSinceCountsFreshEntries() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Old", p(1, 10), 0);
		s.update("jobs", "New", p(1, 10), 5_000);
		s.update("pquests", "Newer", p(1, 10), 9_000);
		assertEquals(2, s.countSeenSince(5_000));
		assertEquals(0, s.countSeenSince(10_000));
	}
	@Test void forgetKeepsPinned() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Old", p(1, 10), 0);
		s.update("jobs", "OldPinned", p(1, 10), 0);
		s.togglePin("jobs:OldPinned");
		s.update("jobs", "New", p(1, 10), 1_000_000);
		assertEquals(1, s.forgetOlderThan(1_000_000, 500_000));
		assertEquals(Set.of("OldPinned", "New"), s.all().stream().map(Trackable::name).collect(Collectors.toSet()));
	}
	@Test void loadCorruptIsEmpty() throws Exception {
		Files.writeString(dir.resolve("t.json"), "][");
		TrackerStore s = new TrackerStore(dir.resolve("t.json")); s.load();
		assertTrue(s.all().isEmpty());
	}
	@Test void loadMissingIsEmpty() {
		TrackerStore s = new TrackerStore(dir.resolve("nope.json")); s.load();
		assertTrue(s.all().isEmpty());
	}
	@Test void loadToleratesNulls() throws Exception {
		Files.writeString(dir.resolve("t.json"),
			"{\"items\":[null,{\"id\":null,\"name\":\"x\"},{\"id\":\"jobs:A\",\"source\":\"jobs\",\"name\":\"A\",\"current\":1,\"max\":2,\"seenAt\":0}],\"pins\":[null,\"jobs:A\"]}");
		TrackerStore s = new TrackerStore(dir.resolve("t.json")); s.load();
		assertEquals(1, s.all().size());
		assertTrue(s.isPinned("jobs:A"));
		Files.writeString(dir.resolve("t.json"), "{\"items\":null,\"pins\":null}");
		s.load();
		assertTrue(s.all().isEmpty());
	}
	@Test void updateReportsOnlyRealChanges() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		assertTrue(s.update("jobs", "A", p(1, 10), 0));        // new
		assertFalse(s.update("jobs", "A", p(1, 10), 1_000));   // same values, seen again shortly after
		assertEquals(0, s.all().get(0).seenAt());              // unchanged entry keeps its timestamp
		assertTrue(s.update("jobs", "A", p(2, 10), 2_000));    // value changed
		assertTrue(s.update("jobs", "A", p(2, 10), 2_000 + TrackerStore.SEEN_REFRESH_MS)); // age worth persisting
		assertEquals(2_000 + TrackerStore.SEEN_REFRESH_MS, s.all().get(0).seenAt());
	}
	@Test void saveFailureReturnsFalse() throws Exception {
		Files.writeString(dir.resolve("file"), "x");
		TrackerStore s = new TrackerStore(dir.resolve("file").resolve("t.json"));
		s.update("jobs", "A", p(1, 10), 0);
		assertFalse(s.save());
		assertTrue(new TrackerStore(dir.resolve("ok.json")).save());
	}
	@Test void togglePinTwiceUnpins() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.togglePin("a:b");
		assertTrue(s.isPinned("a:b"));
		s.togglePin("a:b");
		assertFalse(s.isPinned("a:b"));
	}
}
