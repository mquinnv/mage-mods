package net.mage.cubewheel.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CaptureLogModelTest {
	@TempDir Path dir;

	private JsonObject onlyLine() throws Exception {
		List<String> lines = Files.readAllLines(dir.resolve("1970-01-01.jsonl"), StandardCharsets.UTF_8);
		assertEquals(1, lines.size());
		return JsonParser.parseString(lines.get(0)).getAsJsonObject();
	}

	@Test void modelShapeLineHasEveryPart() throws Exception {
		CaptureLog log = new CaptureLog(dir);
		log.toggle();
		log.modelShape(10, "minecraft:interaction", List.of(
				new CaptureLog.Part(11, "minecraft:wolf", null, List.of(12), true, true, null, null, 1.5, 64.0, -3.25),
				new CaptureLog.Part(12, "minecraft:text_display", 11, List.of(), false, false, null, "Mana Wolf", 1.5, 65.0, -3.25),
				new CaptureLog.Part(13, "minecraft:item_display", null, List.of(), false, false, "bone", null, 0, 0, 0)), 0);

		JsonObject line = onlyLine();
		assertEquals("model_shape", line.get("kind").getAsString());
		assertEquals(10, line.get("hit").getAsInt());
		assertEquals("minecraft:interaction", line.get("hitType").getAsString());
		JsonArray parts = line.getAsJsonArray("parts");
		assertEquals(3, parts.size());
		JsonObject base = parts.get(0).getAsJsonObject();
		assertEquals(11, base.get("entity").getAsInt());
		assertTrue(base.get("invisible").getAsBoolean());
		assertTrue(base.get("mob").getAsBoolean());
		assertFalse(base.has("vehicle"));
		assertEquals(12, base.getAsJsonArray("passengers").get(0).getAsInt());
		JsonObject tag = parts.get(1).getAsJsonObject();
		assertEquals(11, tag.get("vehicle").getAsInt());
		assertEquals("Mana Wolf", tag.get("text").getAsString());
		assertEquals(65.0, tag.get("y").getAsDouble());
		assertEquals(-3.25, tag.get("z").getAsDouble());
		assertEquals("bone", parts.get(2).getAsJsonObject().get("customName").getAsString());
	}

	@Test void soundLineHasKeyAndPosition() throws Exception {
		CaptureLog log = new CaptureLog(dir);
		log.toggle();
		log.sound("minecraft:entity.wolf.growl", "subtitles.entity.wolf.growl", "hostile", 100.5, 70.0, -20.25,
				List.of(new CaptureLog.Nearby(11, "minecraft:wolf", null, null, null, List.of(), 1.25)), 0);

		JsonObject line = onlyLine();
		assertEquals("sound", line.get("kind").getAsString());
		assertEquals("minecraft:entity.wolf.growl", line.get("id").getAsString());
		assertEquals("subtitles.entity.wolf.growl", line.get("subtitleKey").getAsString());
		assertEquals("hostile", line.get("source").getAsString());
		assertEquals(100.5, line.get("x").getAsDouble());
		assertEquals(70.0, line.get("y").getAsDouble());
		assertEquals(-20.25, line.get("z").getAsDouble());
		JsonObject near = line.getAsJsonArray("nearby").get(0).getAsJsonObject();
		assertEquals("minecraft:wolf", near.get("type").getAsString());
		assertEquals(1.25, near.get("distance").getAsDouble());
	}

	@Test void soundWithoutSubtitleWritesNullKey() throws Exception {
		CaptureLog log = new CaptureLog(dir);
		log.toggle();
		log.sound("minecraft:block.stone.step", null, "block", 0, 0, 0, List.of(), 0);
		assertTrue(onlyLine().get("subtitleKey").isJsonNull());
	}
}
