package com.mage.cubewheel.capture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mage.cubewheel.tracker.ContainerScanner.ItemView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Capture mode: while enabled, appends raw chat messages and container contents as JSON lines to
 * {@code dir/<yyyy-MM-dd>.jsonl} (UTC) so parsers can be tuned against real server output.
 * No Minecraft/Fabric imports; IO errors are logged, never thrown.
 */
public final class CaptureLog {
	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

	private final Path dir;
	private boolean enabled;

	public CaptureLog(Path dir) {
		this.dir = dir;
	}

	public Path dir() {
		return dir;
	}

	public boolean enabled() {
		return enabled;
	}

	public void toggle() {
		enabled = !enabled;
	}

	/** {@code json} is the serialized component; kept as nested JSON when it parses, else as a string. */
	public void chat(String json, String text, long now) {
		if (!enabled) return;
		JsonObject o = new JsonObject();
		o.addProperty("t", now);
		o.addProperty("kind", "chat");
		o.add("json", parseOrString(json));
		o.addProperty("text", text);
		append(o, now);
	}

	public void container(String title, List<ItemView> items, long now) {
		if (!enabled) return;
		JsonObject o = new JsonObject();
		o.addProperty("t", now);
		o.addProperty("kind", "container");
		o.addProperty("title", title);
		JsonArray arr = new JsonArray();
		if (items != null) {
			for (ItemView item : items) {
				if (item == null) continue;
				JsonObject i = new JsonObject();
				i.addProperty("slot", item.slot());
				i.addProperty("id", item.id());
				i.addProperty("name", item.name());
				JsonArray lore = new JsonArray();
				if (item.lore() != null) item.lore().forEach(lore::add);
				i.add("lore", lore);
				arr.add(i);
			}
		}
		o.add("items", arr);
		append(o, now);
	}

	private static JsonElement parseOrString(String json) {
		if (json == null) return com.google.gson.JsonNull.INSTANCE;
		try {
			return JsonParser.parseString(json);
		} catch (JsonParseException e) {
			return new com.google.gson.JsonPrimitive(json);
		}
	}

	private void append(JsonObject line, long now) {
		Path file = dir.resolve(DAY.format(Instant.ofEpochMilli(now)) + ".jsonl");
		try {
			Files.createDirectories(dir);
			Files.writeString(file, GSON.toJson(line) + "\n", StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException | RuntimeException e) {
			LOG.warn("[cubewheel] capture write to {} failed: {}", file, e.toString());
		}
	}
}
