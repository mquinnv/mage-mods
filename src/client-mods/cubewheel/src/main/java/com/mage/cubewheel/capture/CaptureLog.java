package com.mage.cubewheel.capture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
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
import java.util.Objects;
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

	/** One boss bar as shown: plain name and target progress (0..1). */
	public record BossBar(String name, float progress) {}

	private final Path dir;
	private boolean enabled;
	// Dedupe state, reset whenever capture is switched on so the current state is recorded again.
	private String lastOverlayChat;
	private String lastActionBar;
	private List<BossBar> lastBossBars = List.of();

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
		lastOverlayChat = null;
		lastActionBar = null;
		lastBossBars = List.of();
	}

	/** A regular chat game message; see {@link #chat(String, String, boolean, long)}. */
	public void chat(String json, String text, long now) {
		chat(json, text, false, now);
	}

	/**
	 * {@code json} is the serialized component; kept as nested JSON when it parses, else as a string.
	 * Overlay (action-bar) game messages get {@code "overlay": true} and are skipped when their text
	 * equals the previous overlay message.
	 */
	public void chat(String json, String text, boolean overlay, long now) {
		if (!enabled) return;
		if (overlay) {
			if (Objects.equals(text, lastOverlayChat)) return;
			lastOverlayChat = text;
		}
		JsonObject o = new JsonObject();
		o.addProperty("t", now);
		o.addProperty("kind", "chat");
		if (overlay) o.addProperty("overlay", true);
		o.add("json", parseOrString(json));
		o.addProperty("text", text);
		append(o, now);
	}

	/** Current action-bar text as held by the HUD; written only when it changed. Null (nothing shown yet) is ignored. */
	public void actionBar(String text, long now) {
		if (!enabled || text == null || text.equals(lastActionBar)) return;
		lastActionBar = text;
		JsonObject o = new JsonObject();
		o.addProperty("t", now);
		o.addProperty("kind", "actionbar");
		o.addProperty("text", text);
		append(o, now);
	}

	/** Boss bars currently shown; written only when the list changed (an initially empty list is not written). */
	public void bossBars(List<BossBar> bars, long now) {
		if (!enabled) return;
		List<BossBar> current = bars == null ? List.of() : List.copyOf(bars);
		if (current.equals(lastBossBars)) return;
		lastBossBars = current;
		JsonObject o = new JsonObject();
		o.addProperty("t", now);
		o.addProperty("kind", "bossbars");
		JsonArray arr = new JsonArray();
		for (BossBar b : current) {
			JsonObject j = new JsonObject();
			j.addProperty("name", b.name());
			j.addProperty("progress", b.progress());
			arr.add(j);
		}
		o.add("bars", arr);
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
		if (json == null) return JsonNull.INSTANCE;
		try {
			return JsonParser.parseString(json);
		} catch (JsonParseException e) {
			return new JsonPrimitive(json);
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
