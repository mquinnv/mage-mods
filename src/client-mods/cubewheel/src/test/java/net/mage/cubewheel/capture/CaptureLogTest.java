package net.mage.cubewheel.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mage.cubewheel.tracker.ContainerScanner.ItemView;
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

	@Test void localCountingKinds() throws Exception {
		CaptureLog log = new CaptureLog(dir);
		log.world("minecraft:overworld", List.of("World: Sandara"), List.of("overworld", "sandara"), true, 0);
		assertFalse(Files.exists(dir.resolve("1970-01-01.jsonl"))); // off: nothing written
		log.toggle();
		log.world("minecraft:overworld", List.of("World: Sandara"), List.of("overworld", "sandara"), true, 0);
		log.local("break", "minecraft:wheat", "Wheat", List.of("overworld"), List.of("pquests:Honest Work"), 1, 0);
		log.estimate("pquests:Honest Work", 212, 220, 0);
		List<String> lines = Files.readAllLines(dir.resolve("1970-01-01.jsonl"));
		assertEquals(3, lines.size());
		JsonObject world = JsonParser.parseString(lines.get(0)).getAsJsonObject();
		assertEquals("world", world.get("kind").getAsString());
		assertEquals("minecraft:overworld", world.get("dimension").getAsString());
		assertEquals("World: Sandara", world.getAsJsonArray("sidebar").get(0).getAsString());
		assertTrue(world.get("special").getAsBoolean());
		JsonObject local = JsonParser.parseString(lines.get(1)).getAsJsonObject();
		assertEquals("local", local.get("kind").getAsString());
		assertEquals("break", local.get("signal").getAsString());
		assertEquals("minecraft:wheat", local.get("id").getAsString());
		assertEquals("pquests:Honest Work", local.getAsJsonArray("matched").get(0).getAsString());
		assertEquals(1, local.get("units").getAsLong());
		JsonObject est = JsonParser.parseString(lines.get(2)).getAsJsonObject();
		assertEquals("estimate", est.get("kind").getAsString());
		assertEquals(212, est.get("counted").getAsLong());
		assertEquals(220, est.get("actual").getAsDouble(), 1e-9);
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

	private List<JsonObject> lines(Path file) throws Exception {
		return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
			.map(l -> JsonParser.parseString(l).getAsJsonObject()).toList();
	}

	@Test void overlayChatIsFlaggedAndRepeatsSkipped() throws Exception {
		CaptureLog log = new CaptureLog(dir);
		log.toggle();
		log.chat("{}", "plain", false, 0);
		log.chat("{}", "+5 Farmer", true, 0);
		log.chat("{}", "+5 Farmer", true, 0);   // same as previous overlay: skipped
		log.chat("{}", "plain", false, 0);      // chat lines are never deduped
		log.chat("{}", "+5 Farmer", true, 0);   // previous overlay was still "+5 Farmer": skipped
		log.chat("{}", "+6 Farmer", true, 0);
		List<JsonObject> out = lines(dir.resolve("1970-01-01.jsonl"));
		assertEquals(4, out.size());
		assertFalse(out.get(0).has("overlay"));
		assertTrue(out.get(1).get("overlay").getAsBoolean());
		assertEquals("chat", out.get(1).get("kind").getAsString());
		assertEquals("+6 Farmer", out.get(3).get("text").getAsString());
	}

	@Test void actionBarWritesOnlyChangesAndSkipsNull() throws Exception {
		CaptureLog log = new CaptureLog(dir);
		log.actionBar("x", 0);                 // disabled: nothing
		log.toggle();
		log.actionBar(null, 0);
		log.actionBar("Farmer 1,200/1,500", 0);
		log.actionBar("Farmer 1,200/1,500", 50);
		log.actionBar("Farmer 1,210/1,500", 100);
		List<JsonObject> out = lines(dir.resolve("1970-01-01.jsonl"));
		assertEquals(2, out.size());
		assertEquals("actionbar", out.get(0).get("kind").getAsString());
		assertEquals("Farmer 1,200/1,500", out.get(0).get("text").getAsString());
		assertEquals(100, out.get(1).get("t").getAsLong());
	}

	@Test void bossBarsWriteOnlyChanges() throws Exception {
		CaptureLog log = new CaptureLog(dir);
		log.toggle();
		log.bossBars(List.of(), 0);            // nothing shown initially: no line
		log.bossBars(List.of(new CaptureLog.BossBar("Quest 3/10", 0.3f)), 0);
		log.bossBars(List.of(new CaptureLog.BossBar("Quest 3/10", 0.3f)), 50);
		log.bossBars(List.of(), 100);          // bars gone: recorded
		List<JsonObject> out = lines(dir.resolve("1970-01-01.jsonl"));
		assertEquals(2, out.size());
		assertEquals("bossbars", out.get(0).get("kind").getAsString());
		JsonObject bar = out.get(0).getAsJsonArray("bars").get(0).getAsJsonObject();
		assertEquals("Quest 3/10", bar.get("name").getAsString());
		assertEquals(0.3f, bar.get("progress").getAsFloat(), 1e-6);
		assertEquals(0, out.get(1).getAsJsonArray("bars").size());
	}

	@Test void reEnablingRecordsCurrentStateAgain() throws Exception {
		CaptureLog log = new CaptureLog(dir);
		log.toggle();
		log.actionBar("a", 0);
		log.toggle();
		log.toggle();
		log.actionBar("a", 0);
		assertEquals(2, lines(dir.resolve("1970-01-01.jsonl")).size());
	}

	@Test void sidebarWritesTitleAndLinesOnlyOnChange() throws Exception {
		CaptureLog log = new CaptureLog(dir);
		log.sidebar("MANACUBE", List.of("Money: $1"), 0); // disabled: nothing
		log.toggle();
		log.sidebar("MANACUBE", List.of("Money: $2.89M", "Skills: Lvl 1851"), 0);
		log.sidebar("MANACUBE", List.of("Money: $2.89M", "Skills: Lvl 1851"), 50);
		log.sidebar("MANACUBE", List.of("Money: $2.90M", "Skills: Lvl 1851"), 100);
		log.sidebar(null, List.of(), 150); // sidebar gone
		List<JsonObject> out = lines(dir.resolve("1970-01-01.jsonl"));
		assertEquals(3, out.size());
		assertEquals("sidebar", out.get(0).get("kind").getAsString());
		assertEquals("MANACUBE", out.get(0).get("title").getAsString());
		assertEquals("Skills: Lvl 1851", out.get(0).getAsJsonArray("lines").get(1).getAsString());
		assertEquals("Money: $2.90M", out.get(1).getAsJsonArray("lines").get(0).getAsString());
		assertTrue(out.get(2).get("title").isJsonNull());
		assertEquals(0, out.get(2).getAsJsonArray("lines").size());
	}

	@Test void containerRecordsTheLastCommandSentBeforeIt() throws Exception {
		CaptureLog log = new CaptureLog(dir);
		log.toggle();
		log.container("Menu", List.of(), 0);                 // no command yet: no field
		log.noteCommand("prestige", 1_000);                 // typed or sent without the slash
		log.container("Prestige", List.of(), 1_400);
		log.noteCommand("/pquests", 2_000);
		log.container("Quests", List.of(), 2_250);
		List<JsonObject> out = lines(dir.resolve("1970-01-01.jsonl"));
		assertFalse(out.get(0).has("afterCommand"));
		assertEquals("/prestige", out.get(1).get("afterCommand").getAsString());
		assertEquals(400, out.get(1).get("afterCommandMs").getAsLong());
		assertEquals("/pquests", out.get(2).get("afterCommand").getAsString());
		assertEquals(250, out.get(2).get("afterCommandMs").getAsLong());
	}

	@Test void commandsAreRememberedWhileCaptureIsOff() throws Exception {
		CaptureLog log = new CaptureLog(dir);
		log.noteCommand("/prestige", 0);
		log.toggle();
		log.container("Prestige", List.of(), 10);
		assertEquals("/prestige", lines(dir.resolve("1970-01-01.jsonl")).get(0).get("afterCommand").getAsString());
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
