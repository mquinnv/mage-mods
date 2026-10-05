package net.mage.cubewheel.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.mage.cubewheel.tracker.ProgressExtractor;
import net.mage.cubewheel.tracker.TrackerStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Saves off the client thread: coalesced, written whole through a temporary file, flushed on quit. */
class BackgroundSaverTest {
	@TempDir Path dir;

	/** An executor that runs its tasks only when told, standing in for the background thread. */
	static final class Manual implements Executor {
		final Queue<Runnable> tasks = new ArrayDeque<>();

		@Override public void execute(Runnable r) {
			tasks.add(r);
		}

		void runAll() {
			while (!tasks.isEmpty()) tasks.poll().run();
		}
	}

	@Test void savesWaitingTogetherAreWrittenOnce() throws Exception {
		Manual bg = new Manual();
		Path file = dir.resolve("t.json");
		BackgroundSaver s = new BackgroundSaver(file, "test", bg);
		AtomicInteger built = new AtomicInteger();
		for (int i = 1; i <= 5; i++) {
			String v = "v" + i;
			s.saveLater(() -> { built.incrementAndGet(); return v; });
		}
		assertEquals(1, bg.tasks.size()); // one queued task for the burst
		assertTrue(s.pending());
		assertFalse(Files.exists(file)); // nothing on the caller's thread
		bg.runAll();
		assertEquals("v5", Files.readString(file));
		assertEquals(1, built.get()); // only the newest content was worked out
		assertFalse(s.pending());
		s.saveLater(() -> "v6");
		assertEquals(1, bg.tasks.size()); // a new save after the write queues again
		bg.runAll();
		assertEquals("v6", Files.readString(file));
	}

	@Test void replacesTheFileWholeAndLeavesNoTemporaryFile() throws Exception {
		Path file = dir.resolve("sub").resolve("t.json");
		BackgroundSaver s = new BackgroundSaver(file, "test", Runnable::run);
		assertTrue(s.saveNow("first"));
		s.saveLater(() -> "second");
		assertEquals("second", Files.readString(file));
		assertFalse(Files.exists(file.resolveSibling("t.json.tmp")));
	}

	@Test void aFailedWriteKeepsThePreviousFile() throws Exception {
		Path file = dir.resolve("t.json");
		BackgroundSaver s = new BackgroundSaver(file, "test", Runnable::run);
		assertTrue(s.saveNow("good"));
		s.saveLater(() -> { throw new IllegalStateException("serializer blew up"); });
		assertEquals("good", Files.readString(file));
		Files.writeString(dir.resolve("blocker"), "x");
		assertFalse(new BackgroundSaver(dir.resolve("blocker").resolve("t.json"), "test", Runnable::run).saveNow("x"));
	}

	@Test void flushWritesTheWaitingSaveOnThisThread() throws Exception {
		Manual bg = new Manual();
		Path file = dir.resolve("t.json");
		BackgroundSaver s = new BackgroundSaver(file, "test", bg);
		s.saveLater(() -> "waiting");
		s.flush();
		assertEquals("waiting", Files.readString(file));
		bg.runAll(); // the queued task finds nothing left to do
		assertEquals("waiting", Files.readString(file));
		s.flush(); // nothing waiting: no-op
		assertEquals("waiting", Files.readString(file));
	}

	@Test void anOlderSaveNeverOverwritesANewerOne() throws Exception {
		Manual bg = new Manual();
		Path file = dir.resolve("t.json");
		BackgroundSaver s = new BackgroundSaver(file, "test", bg);
		s.saveLater(() -> "old");
		assertTrue(s.saveNow("new")); // e.g. the disconnect flush
		assertFalse(s.pending()); // the older waiting save was dropped
		bg.runAll();
		assertEquals("new", Files.readString(file));
	}

	@Test void trackerStoreSavesInTheBackgroundAndFlushes() throws Exception {
		Path file = dir.resolve("tracker.json");
		TrackerStore store = new TrackerStore(file);
		store.update("jobs", "A", new ProgressExtractor.Progress(1, 10), 0);
		store.saveInBackground();
		store.update("jobs", "B", new ProgressExtractor.Progress(2, 10), 0); // after the copy: not in that save
		store.flushSaves(); // the copy may already be written by the background thread; this waits for it either way
		TrackerStore read = new TrackerStore(file);
		read.load();
		assertEquals(1, read.all().size());
		assertEquals("A", read.all().get(0).name());
		assertTrue(store.save());
		read.load();
		assertEquals(2, read.all().size());
	}

	@Test void aSaveThatCannotBeQueuedDoesNotBlockLaterOnes() throws Exception {
		Path file = dir.resolve("t.json");
		AtomicInteger calls = new AtomicInteger();
		Manual bg = new Manual();
		Executor flaky = r -> {
			if (calls.incrementAndGet() == 1) throw new java.util.concurrent.RejectedExecutionException("shut down");
			bg.execute(r);
		};
		BackgroundSaver s = new BackgroundSaver(file, "test", flaky);
		s.saveLater(() -> "lost");
		assertFalse(s.pending()); // cleared, not stuck
		s.saveLater(() -> "later");
		assertEquals(1, bg.tasks.size());
		bg.runAll();
		assertEquals("later", Files.readString(file));
	}
}
