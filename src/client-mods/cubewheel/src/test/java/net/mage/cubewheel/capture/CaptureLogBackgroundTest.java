package net.mage.cubewheel.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Capture written on a background thread: same lines, one open file, flushed about once a second and on quit. */
class CaptureLogBackgroundTest {
	@TempDir Path dir;

	static final class Manual implements Executor {
		final Queue<Runnable> tasks = new ArrayDeque<>();

		@Override public void execute(Runnable r) {
			tasks.add(r);
		}

		void runAll() {
			while (!tasks.isEmpty()) tasks.poll().run();
		}
	}

	static final String COMPONENT = "{\"text\":\"Hi \",\"color\":\"gold\",\"extra\":[{\"text\":\"<b>&'\",\"bold\":true},"
			+ "{\"translate\":\"x\",\"with\":[1,0.5,2.0]}]}";

	List<String> lines(Path file) throws Exception {
		return Files.readAllLines(file, StandardCharsets.UTF_8);
	}

	@Test void aComponentTreeIsWrittenExactlyAsItsStringForm() throws Exception {
		Path a = dir.resolve("a"), b = dir.resolve("b");
		CaptureLog fromString = new CaptureLog(a);
		CaptureLog fromTree = new CaptureLog(b);
		fromString.toggle();
		fromTree.toggle();
		fromString.chat(COMPONENT, "Hi <b>&'", true, 0);
		// As the client does it now: the codec's tree, never printed and parsed back.
		fromTree.chat(JsonParser.parseString(COMPONENT), "Hi <b>&'", true, 0);
		fromString.chat("not json {", "x", false, 0);
		fromTree.chat(CaptureLog.json("not json {"), "x", false, 0);
		fromString.chat((String) null, "n", false, 0);
		fromTree.chat(CaptureLog.json(null), "n", false, 0);
		assertEquals(lines(a.resolve("1970-01-01.jsonl")), lines(b.resolve("1970-01-01.jsonl")));
		assertEquals(3, lines(b.resolve("1970-01-01.jsonl")).size());
	}

	@Test void linesWaitForTheWriterThreadAndKeepTheirOrder() throws Exception {
		Manual bg = new Manual();
		CaptureLog log = new CaptureLog(dir, bg);
		log.toggle();
		log.chat("{\"text\":\"one\"}", "one", 0);
		log.actionBar("two", 0);
		log.estimate("pquests:X", 3, 4, 0);
		Path file = dir.resolve("1970-01-01.jsonl");
		assertFalse(Files.exists(file)); // nothing done on the caller's thread
		assertEquals(3, bg.tasks.size());
		bg.runAll();
		log.tick(5_000); // a second since the last flush: one flush queued
		assertEquals(1, bg.tasks.size());
		log.tick(5_100); // not again within the second
		assertTrue(bg.tasks.size() == 1);
		bg.runAll();
		List<String> lines = lines(file);
		assertEquals(List.of("chat", "actionbar", "estimate"),
				lines.stream().map(l -> JsonParser.parseString(l).getAsJsonObject().get("kind").getAsString()).toList());
		log.tick(7_000); // nothing written since: no flush queued
		assertTrue(bg.tasks.isEmpty());
	}

	@Test void aNewDayOpensANewFile() throws Exception {
		Manual bg = new Manual();
		CaptureLog log = new CaptureLog(dir, bg);
		log.toggle();
		log.actionBar("day one", 0);
		log.actionBar("day two", 86_400_000L);
		log.toggle(); // off: flushes and closes
		bg.runAll();
		assertEquals(1, lines(dir.resolve("1970-01-01.jsonl")).size());
		assertEquals(1, lines(dir.resolve("1970-01-02.jsonl")).size());
	}

	@Test void flushWaitsForTheBackgroundThread() throws Exception {
		ExecutorService io = Executors.newSingleThreadExecutor();
		try {
			CaptureLog log = new CaptureLog(dir, io);
			log.toggle();
			for (int i = 0; i < 500; i++) log.actionBar("line " + i, 0);
			log.flush(); // as on quit
			List<String> lines = lines(dir.resolve("1970-01-01.jsonl"));
			assertEquals(500, lines.size());
			JsonObject last = JsonParser.parseString(lines.get(499)).getAsJsonObject();
			assertEquals("line 499", last.get("text").getAsString());
		} finally {
			io.shutdownNow();
		}
	}

	@Test void writeErrorsAreLoggedAndTheNextLineTriesAgain() throws Exception {
		Path blocked = dir.resolve("blocked");
		Files.writeString(blocked, "x"); // a file where the directory should be
		Manual bg = new Manual();
		CaptureLog log = new CaptureLog(blocked, bg);
		log.toggle();
		log.actionBar("lost", 0);
		bg.runAll(); // logged, not thrown
		Files.delete(blocked);
		log.actionBar("kept", 0);
		log.toggle(); // off: flushes and closes
		bg.runAll();
		assertEquals(1, lines(blocked.resolve("1970-01-01.jsonl")).size());
	}
}
