package net.mage.cubewheel.tracker;

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
	static List<String> names(List<Trackable> l) { return l.stream().map(Trackable::name).toList(); }

	TrackerStore hudStore() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Low", p(1, 10), 0);
		s.update("jobs", "Near", p(9, 10), 0);
		s.update("jobs", "Mid", p(5, 10), 0);
		s.update("jobs", "Done", p(10, 10), 0);
		s.update("pquests", "Pinned", p(2, 10), 0);
		s.update("pquests", "Pinned done", p(10, 10), 0);
		s.togglePin(Trackable.idOf("pquests", "Pinned"));
		s.togglePin(Trackable.idOf("pquests", "Pinned done"));
		return s;
	}

	@Test void hudShowsPinnedThenEveryIncompleteClosestFirst() {
		TrackerStore s = hudStore();
		// Pinned first, then all incomplete by fraction; complete entries ("Done", "Pinned done") never.
		assertEquals(List.of("Pinned", "Near", "Mid", "Low"), names(s.hudEntries(8)));
	}

	@Test void pinnedEntryUnpinsItselfWhenAMenuReadShowsItComplete() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		String id = Trackable.idOf("pquests", "Haven Harvester");
		s.update("pquests", "Haven Harvester", p(67, 100), 0);
		s.togglePin(id);
		assertEquals(List.of(), s.drainCompletions());
		s.update("pquests", "Haven Harvester", p(100, 100), 1_000);
		assertFalse(s.isPinned(id));
		assertEquals(List.of("Haven Harvester"), s.drainCompletions());
		assertEquals(List.of(), s.drainCompletions(), "announced once");
		assertEquals(List.of(), names(s.hudEntries(8)), "completed and unpinned: gone from the HUD");
	}

	@Test void alreadyCompletePinnedEntryIsUnpinnedOnTheNextReadWithoutAnnouncement() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		String id = Trackable.idOf("pquests", "Haven Harvester");
		s.update("pquests", "Haven Harvester", p(100, 100), 0);
		s.togglePin(id); // e.g. pinned before this rule existed
		s.update("pquests", "Haven Harvester", p(100, 100), 120_000);
		assertFalse(s.isPinned(id));
		assertEquals(List.of(), s.drainCompletions());
	}

	@Test void incompletePinStays() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		String id = Trackable.idOf("jobs", "A");
		s.update("jobs", "A", p(1, 10), 0);
		s.togglePin(id);
		s.update("jobs", "A", p(5, 10), 120_000);
		assertTrue(s.isPinned(id));
	}

	@Test void hudIsCappedAtMaxLines() {
		TrackerStore s = hudStore();
		assertEquals(List.of("Pinned", "Near", "Mid"), names(s.hudEntries(3)));
		assertEquals(List.of("Pinned"), names(s.hudEntries(1)));
	}

	@Test void hiddenEntriesNeverShowOnTheHudButStayListed() {
		TrackerStore s = hudStore();
		s.toggleHidden("jobs:Near");
		s.toggleHidden("pquests:Pinned"); // hidden wins over pinned
		assertTrue(s.isHidden("jobs:Near"));
		assertEquals(List.of("Mid", "Low"), names(s.hudEntries(8)));
		assertEquals(6, s.all().size()); // still in the picker
		s.toggleHidden("jobs:Near");
		assertFalse(s.isHidden("jobs:Near"));
		assertEquals(List.of("Near", "Mid", "Low"), names(s.hudEntries(8)));
	}

	@Test void hiddenIsPersistedAndLoadedTolerantly() throws Exception {
		TrackerStore s = hudStore();
		s.toggleHidden("jobs:Low");
		s.save();
		TrackerStore t = new TrackerStore(dir.resolve("t.json"));
		t.load();
		assertTrue(t.isHidden("jobs:Low"));
		assertFalse(t.isHidden("jobs:Mid"));
		Path old = dir.resolve("old.json"); // a file from before "hidden" existed, and one with junk in it
		Files.writeString(old, "{\"items\": [{\"id\": \"jobs:A\", \"source\": \"jobs\", \"name\": \"A\", \"current\": 1, \"max\": 2, \"seenAt\": 0}]}");
		TrackerStore o = new TrackerStore(old);
		o.load();
		assertEquals(List.of("A"), names(o.hudEntries(8)));
		Files.writeString(old, "{\"items\": [{\"id\": \"jobs:A\", \"source\": \"jobs\", \"name\": \"A\", \"current\": 1, \"max\": 2, \"seenAt\": 0}], \"hidden\": [null, \"jobs:A\"]}");
		o.load();
		assertTrue(o.isHidden("jobs:A"));
		assertTrue(o.hudEntries(8).isEmpty());
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
