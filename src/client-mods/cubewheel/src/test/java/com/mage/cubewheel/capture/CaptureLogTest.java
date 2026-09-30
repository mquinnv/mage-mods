package com.mage.cubewheel.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mage.cubewheel.tracker.ContainerScanner.ItemView;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CaptureLogTest {
	@TempDir Path dir;

	@Test void enabledLogAppendsChatAndContainerLines() throws Exception {
		Path captures = dir.resolve("cubewheel-captures");
		CaptureLog log = new CaptureLog(captures);
		assertFalse(log.enabled());
		log.toggle();
		assertTrue(log.enabled());
		log.chat("{\"text\":\"hi\"}", "hi", 0);
		log.container("Jobs", List.of(new ItemView(3, "minecraft:carrot", "Carrot Grind", List.of("1,200/1,500"))), 0);

		List<String> lines = Files.readAllLines(captures.resolve("1970-01-01.jsonl"), StandardCharsets.UTF_8);
		assertEquals(2, lines.size());
		JsonObject chat = JsonParser.parseString(lines.get(0)).getAsJsonObject();
		assertEquals("chat", chat.get("kind").getAsString());
		assertEquals(0, chat.get("t").getAsLong());
		assertEquals("hi", chat.get("text").getAsString());
		assertEquals("hi", chat.getAsJsonObject("json").get("text").getAsString());
		JsonObject container = JsonParser.parseString(lines.get(1)).getAsJsonObject();
		assertEquals("container", container.get("kind").getAsString());
		assertEquals("Jobs", container.get("title").getAsString());
		JsonObject item = container.getAsJsonArray("items").get(0).getAsJsonObject();
		assertEquals(3, item.get("slot").getAsInt());
		assertEquals("minecraft:carrot", item.get("id").getAsString());
		assertEquals("Carrot Grind", item.get("name").getAsString());
		assertEquals("1,200/1,500", item.getAsJsonArray("lore").get(0).getAsString());
	}

	@Test void nonJsonChatPayloadIsStoredAsString() throws Exception {
		CaptureLog log = new CaptureLog(dir);
		log.toggle();
		log.chat("literal{hi}", "hi", 0);
		JsonObject chat = JsonParser.parseString(Files.readAllLines(dir.resolve("1970-01-01.jsonl")).get(0)).getAsJsonObject();
		assertEquals("literal{hi}", chat.get("json").getAsString());
	}

	@Test void dateFileUsesUtcDayOfNow() throws Exception {
		CaptureLog log = new CaptureLog(dir);
		log.toggle();
		log.chat("{}", "x", 86_400_000L * 2 - 1);
		assertTrue(Files.exists(dir.resolve("1970-01-02.jsonl")));
	}

	@Test void disabledLogWritesNothing() {
		Path captures = dir.resolve("cubewheel-captures");
		CaptureLog log = new CaptureLog(captures);
		log.chat("{}", "x", 0);
		log.container("Jobs", List.of(), 0);
		log.toggle();
		log.toggle();
		log.chat("{}", "x", 0);
		assertFalse(Files.exists(captures));
	}

	@Test void ioErrorsAreSwallowed() throws Exception {
		Path blocker = dir.resolve("file");
		Files.writeString(blocker, "not a dir");
		CaptureLog log = new CaptureLog(blocker.resolve("sub"));
		log.toggle();
		log.chat("{}", "x", 0); // must not throw
		log.container("Jobs", List.of(), 0);
	}
}
