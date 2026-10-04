package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.mage.cubewheel.tracker.local.ObjectiveInfo;
import net.mage.cubewheel.tracker.local.WorldScope;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The store's per-entry {@link WorldScope} cache: reused while nothing it depends on changes, redone when it does. */
class TrackerStoreScopeCacheTest {
	@TempDir Path dir;
	static final List<String> WORLDS = List.of("wolfhaven", "tangleroots", "sandara");
	static final String ID = "pquests:Haven Harvester";

	static ObjectiveInfo obj(String line) {
		return new ObjectiveInfo(List.of(new ObjectiveInfo.Sub(line, null)), false, false);
	}

	TrackerStore store() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("pquests", "Haven Harvester", new ProgressExtractor.Progress(64, 100), 0);
		s.setObjective(ID, obj("Harvest or Mine 10,000 Wolfhaven Resources"));
		return s;
	}

	static Trackable item(TrackerStore s) {
		return s.all().get(0);
	}

	@Test void sameInputsGiveTheCachedScope() {
		TrackerStore s = store();
		WorldScope.Scope first = s.scope(item(s), WORLDS);
		assertEquals(Set.of("wolfhaven"), first.worlds());
		assertSame(first, s.scope(item(s), WORLDS));
		// An equal list (not the same object) is the same world names.
		assertSame(first, s.scope(item(s), new ArrayList<>(WORLDS)));
		assertEquals(WorldScope.of(item(s).name(), s.objective(ID).orElse(null), WORLDS), first);
	}

	@Test void aNewObjectiveIsReadAgain() {
		TrackerStore s = store();
		assertEquals(Set.of("wolfhaven"), s.scope(item(s), WORLDS).worlds());
		s.setObjective(ID, obj("Harvest or Mine 10,000 Sandara Resources"));
		assertEquals(Set.of("sandara"), s.scope(item(s), WORLDS).worlds());
	}

	@Test void otherWorldNamesAreReadAgain() {
		TrackerStore s = store();
		assertEquals(Set.of("wolfhaven"), s.scope(item(s), WORLDS).worlds());
		assertEquals(WorldScope.Scope.NONE, s.scope(item(s), List.of("sandara")));
		List<String> edited = new ArrayList<>(List.of("sandara"));
		assertEquals(WorldScope.Scope.NONE, s.scope(item(s), edited));
		edited.add("wolfhaven"); // the config's list edited in place
		assertEquals(Set.of("wolfhaven"), s.scope(item(s), edited).worlds());
		assertEquals(WorldScope.Scope.NONE, s.scope(item(s), null));
	}

	@Test void reloadingForgetsTheCache() {
		TrackerStore s = store();
		s.save();
		WorldScope.Scope before = s.scope(item(s), WORLDS);
		s.load();
		assertEquals(before, s.scope(item(s), WORLDS));
	}

	@Test void wantedKeysFollowTheWorldNames() {
		assertEquals(Set.of(), WorldScope.of("Slay Tigers in Tangleroots", null, List.of()).worlds());
		assertEquals(Set.of("tangleroot"), WorldScope.of("Slay Tigers in Tangleroots", null, List.of("Tangleroots")).worlds());
		List<String> names = new ArrayList<>(List.of("Sandara"));
		assertEquals(Set.of(), WorldScope.of("Slay Tigers in Tangleroots", null, names).worlds());
		names.add("Tangleroots"); // edited in place: the cached keys must not be reused
		assertEquals(Set.of("tangleroot"), WorldScope.of("Slay Tigers in Tangleroots", null, names).worlds());
	}
}
