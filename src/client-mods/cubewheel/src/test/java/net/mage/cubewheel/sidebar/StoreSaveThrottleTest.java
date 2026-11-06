package net.mage.cubewheel.sidebar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import net.mage.cubewheel.tracker.ProgressExtractor;
import net.mage.cubewheel.tracker.TrackerStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoreSaveThrottleTest {
	@TempDir Path dir;

	private TrackerStore store(String file) {
		TrackerStore s = new TrackerStore(dir.resolve(file));
		s.update("jobs", "Miner", new ProgressExtractor.Progress(1, 10), 0);
		return s;
	}

	@Test void dirtyOldStoreIsSavedWhenAnotherStoreTakesOver() {
		StoreSaveThrottle t = new StoreSaveThrottle(10_000);
		TrackerStore a = store("a.json");
		TrackerStore b = store("b.json");
		t.markDirty(a);
		assertFalse(Files.exists(dir.resolve("a.json")));
		t.markDirty(b); // gamemode flipped before the throttle fired
		assertTrue(Files.exists(dir.resolve("a.json")), "A's change reaches A's file");
		assertFalse(Files.exists(dir.resolve("b.json")), "B waits for its own throttle");
	}

	@Test void flushWritesTheDirtyStoreNotTheCurrentOne() throws Exception {
		StoreSaveThrottle t = new StoreSaveThrottle(10_000);
		TrackerStore a = store("a.json");
		t.markDirty(a);
		t.flush();
		assertTrue(Files.exists(dir.resolve("a.json")));
		Files.delete(dir.resolve("a.json"));
		t.flush();
		assertFalse(Files.exists(dir.resolve("a.json")), "clean after a flush");
	}

	@Test void tickSavesInTheBackgroundOnlyAfterTheInterval() {
		StoreSaveThrottle t = new StoreSaveThrottle(10_000);
		TrackerStore a = store("a.json");
		t.markDirty(a);
		t.tick(20_000_000_000L);
		a.flushSaves();
		assertTrue(Files.exists(dir.resolve("a.json")));
	}
}
