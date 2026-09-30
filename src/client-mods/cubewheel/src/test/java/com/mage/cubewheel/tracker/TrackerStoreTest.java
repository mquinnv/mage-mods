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
	@Test void togglePinTwiceUnpins() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.togglePin("a:b");
		assertTrue(s.isPinned("a:b"));
		s.togglePin("a:b");
		assertFalse(s.isPinned("a:b"));
	}
}
