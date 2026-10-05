package net.mage.cubewheel.capture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.mage.cubewheel.tracker.ContainerScanner.ItemView;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Capture mode: while enabled, appends raw chat messages and container contents as JSON lines to
 * {@code dir/<yyyy-MM-dd>.jsonl} (UTC) so parsers can be tuned against real server output.
 * No Minecraft/Fabric imports; IO errors are logged, never thrown.
 *
 * <p>With an {@code executor} (in game: {@link net.mage.cubewheel.io.BackgroundSaver#IO}) lines are serialized and
 * written on that one thread, to a day file kept open, and flushed at most once a second ({@link #tick}) and on
 * {@link #flush}; the client thread only builds each line's JSON tree, which nothing touches after. Without one
 * (tests) each line is written and flushed on the calling thread.
 */
public final class CaptureLog {
	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

	/** One boss bar as shown: plain name and target progress (0..1). */
	public record BossBar(String name, float progress) {}

	/**
	 * An entity near a hit mob: entity id, type id, custom name and text-display text (null if none), the
	 * entity it rides (null if none), its passengers, and its distance from the hit mob's box in blocks.
	 */
	public record Nearby(int entity, String type, String customName, String text, Integer vehicle, List<Integer> passengers,
			double distance) {}

	private final Path dir;
	/** Where lines are written; null = on the calling thread, flushed per line. */
	private final Executor executor;
	/** The open day file and its writer: touched only on the writing thread (or under {@link #writeLock}). */
	private Path openFile;
	private Writer out;
	private final Object writeLock = new Object();
	/** Lines written since the last flush (background mode); set on the writer thread, read by {@link #tick}. */
	private final AtomicBoolean unflushed = new AtomicBoolean();
	private long lastFlushAt;
	private boolean enabled;
	// Dedupe state, reset whenever capture is switched on so the current state is recorded again.
	private String lastOverlayChat;
	private String lastActionBar;
	private List<BossBar> lastBossBars = List.of();
	private String lastSidebar = "";
	// Not reset by toggle(): the command sent just before switching capture on still explains the next menu.
	private String lastCommand;
	private long lastCommandAt;

	public CaptureLog(Path dir) {
		this(dir, null);
	}

	/** Writes on {@code executor} (a single thread); null writes on the calling thread. */
	public CaptureLog(Path dir, Executor executor) {
		this.dir = dir;
		this.executor = executor;
	}

	public Path dir() {
		return dir;
	}

	public boolean enabled() {
		return enabled;
	}

	public void toggle() {
		enabled = !enabled;
		if (!enabled) close(); // writes what is buffered and lets the file go
		lastOverlayChat = null;
		lastActionBar = null;
		lastBossBars = List.of();
		lastSidebar = "";
	}

	/**
	 * Remembers the last command the player (or a CubeWheel refresh) sent, even while capture is off, so
	 * each container line can say which command opened it ({@code afterCommand}, {@code afterCommandMs}).
	 */
	public void noteCommand(String command, long now) {
		if (command == null || command.isBlank()) return;
		String c = command.trim();
		lastCommand = c.startsWith("/") ? c : "/" + c;
		lastCommandAt = now;
	}

	/**
	 * The scoreboard sidebar as drawn (title and lines, plain text); written only when it changed. A
	 * missing sidebar is a null title with no lines (recorded when a shown sidebar disappears).
	 */
	public void sidebar(String title, List<String> lines, long now) {
		if (!enabled) return;
		List<String> current = lines == null ? List.of() : lines;
		String key = title == null && current.isEmpty() ? "" : String.valueOf(title) + "\n" + String.join("\n", current);
		if (key.equals(lastSidebar)) return;
		lastSidebar = key;
		JsonObject o = new JsonObject();
		o.addProperty("t", now);
		o.addProperty("kind", "sidebar");
		o.addProperty("title", title);
		JsonArray arr = new JsonArray();
		current.forEach(arr::add);
		o.add("lines", arr);
		append(o, now);
	}

	/** The world as local counting resolved it (dimension id, raw sidebar lines, tokens, special). */
	public void world(String dimension, List<String> sidebar, List<String> tokens, boolean special, long now) {
		if (!enabled) return;
		JsonObject o = new JsonObject();
		o.addProperty("t", now);
		o.addProperty("kind", "world");
		o.addProperty("dimension", dimension);
		o.add("sidebar", array(sidebar));
		o.add("tokens", array(tokens));
		o.addProperty("special", special);
		append(o, now);
	}

	/**
	 * A local-counting signal: {@code signal} is break, kill, fish or reject; {@code matched} the tracker
	 * ids it advanced (or took back), {@code units} per id.
	 */
	public void local(String signal, String id, String name, List<String> world, List<String> matched, long units, long now) {
		local(signal, id, name, null, world, matched, units, now);
	}

	/**
	 * As above, with a free-text {@code detail} explaining the signal (kill attribution verdict, an
	 * attacked entity's passengers, a stack name change "5x Tiger -> 4x Tiger"); omitted when null.
	 */
	public void local(String signal, String id, String name, String detail, List<String> world, List<String> matched,
			long units, long now) {
		if (!enabled) return;
		JsonObject o = new JsonObject();
		o.addProperty("t", now);
		o.addProperty("kind", "local");
		o.addProperty("signal", signal);
		o.addProperty("id", id);
		o.addProperty("name", name);
		o.add("world", array(world));
		o.add("matched", array(matched));
		o.addProperty("units", units);
		if (detail != null) o.addProperty("detail", detail);
		append(o, now);
	}

	/**
	 * Entities around a mob the local player hit (written once per mob): a {@code local} line with
	 * {@code "signal": "nearby"}, the hit entity's id, type and raw name, and {@code nearby} entries.
	 */
	public void nearby(int entity, String id, String name, List<Nearby> near, List<String> world, long now) {
		if (!enabled) return;
		JsonObject o = new JsonObject();
		o.addProperty("t", now);
		o.addProperty("kind", "local");
		o.addProperty("signal", "nearby");
		o.addProperty("entity", entity);
		o.addProperty("id", id);
		o.addProperty("name", name);
		o.add("world", array(world));
		JsonArray arr = new JsonArray();
		if (near != null) {
			for (Nearby n : near) {
				if (n == null) continue;
				JsonObject j = new JsonObject();
				j.addProperty("entity", n.entity());
				j.addProperty("type", n.type());
				if (n.customName() != null) j.addProperty("customName", n.customName());
				if (n.text() != null) j.addProperty("text", n.text());
				if (n.vehicle() != null) j.addProperty("vehicle", n.vehicle());
				if (n.passengers() != null && !n.passengers().isEmpty()) {
					JsonArray p = new JsonArray();
					n.passengers().forEach(p::add);
					j.add("passengers", p);
				}
				j.addProperty("distance", Math.round(n.distance() * 100) / 100.0);
				arr.add(j);
			}
		}
		o.add("nearby", arr);
		append(o, now);
	}

	/** A snap-back: {@code counted} units estimated locally vs {@code actual} per the next authoritative read. */
	public void estimate(String id, long counted, double actual, long now) {
		if (!enabled) return;
		JsonObject o = new JsonObject();
		o.addProperty("t", now);
		o.addProperty("kind", "estimate");
		o.addProperty("id", id);
		o.addProperty("counted", counted);
		o.addProperty("actual", actual);
		append(o, now);
	}

	private static JsonArray array(List<String> values) {
		JsonArray arr = new JsonArray();
		if (values != null) values.forEach(arr::add);
		return arr;
	}

	/**
	 * As {@link #chat(String, String, boolean, long)} with the component's JSON tree itself (not parsed back from a
	 * string); it is written as it is and must not be changed afterwards.
	 */
	public void chat(JsonElement json, String text, boolean overlay, long now) {
		if (!enabled) return;
		if (overlay) {
			if (Objects.equals(text, lastOverlayChat)) return;
			lastOverlayChat = text;
		}
		JsonObject o = new JsonObject();
		o.addProperty("t", now);
		o.addProperty("kind", "chat");
		if (overlay) o.addProperty("overlay", true);
		o.add("json", json == null ? JsonNull.INSTANCE : json);
		o.addProperty("text", text);
		append(o, now);
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
		chat(json(json), text, overlay, now);
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
		if (lastCommand != null) {
			o.addProperty("afterCommand", lastCommand);
			o.addProperty("afterCommandMs", now - lastCommandAt);
		}
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

	/** {@code json} as a JSON tree when it parses, else as a string; JSON null for null. */
	public static JsonElement json(String json) {
		if (json == null) return JsonNull.INSTANCE;
		try {
			return JsonParser.parseString(json);
		} catch (JsonParseException e) {
			return new JsonPrimitive(json);
		}
	}

	private void append(JsonObject line, long now) {
		Path file = dir.resolve(DAY.format(Instant.ofEpochMilli(now)) + ".jsonl");
		if (executor == null) {
			write(file, line, true);
			return;
		}
		try {
			executor.execute(() -> write(file, line, false));
		} catch (RuntimeException e) {
			LOG.warn("[cubewheel] capture write to {} failed: {}", file, e.toString());
		}
	}

	/** Appends {@code line} to {@code file}, (re)opening the writer when the day changed; flushes if {@code flush}. */
	private void write(Path file, JsonObject line, boolean flush) {
		synchronized (writeLock) {
			try {
				if (!file.equals(openFile) || out == null) {
					closeWriter();
					Files.createDirectories(dir);
					out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
					openFile = file;
				}
				out.write(GSON.toJson(line));
				out.write('\n');
				if (flush) out.flush();
				else unflushed.set(true);
			} catch (IOException | RuntimeException e) {
				LOG.warn("[cubewheel] capture write to {} failed: {}", file, e.toString());
				closeWriter(); // try a fresh writer for the next line
			}
		}
	}

	/**
	 * Client tick: in background mode, flushes what was written at most once a second, on the writing thread, so the
	 * file is never more than about a second behind.
	 */
	public void tick(long now) {
		if (executor == null || now - lastFlushAt < 1000 || !unflushed.get()) return;
		lastFlushAt = now;
		try {
			executor.execute(this::flushWriter);
		} catch (RuntimeException e) {
			LOG.warn("[cubewheel] capture flush failed: {}", e.toString());
		}
	}

	/** Writes every line handed over so far to disk, waiting up to a few seconds for the writing thread (quit). */
	public void flush() {
		flush(5_000);
	}

	/** As {@link #flush()}, waiting at most {@code timeoutMs} (a shutdown hook must not hang the exit). */
	public void flush(long timeoutMs) {
		if (executor == null) return;
		try {
			CompletableFuture.runAsync(this::flushWriter, executor).get(timeoutMs, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException | TimeoutException | RuntimeException e) {
			LOG.warn("[cubewheel] capture flush failed: {}", e.toString());
		}
	}

	/** Capture switched off: what is buffered goes to disk and the file is closed, on the writing thread. */
	private void close() {
		if (executor == null) {
			synchronized (writeLock) {
				closeWriter();
			}
			return;
		}
		try {
			executor.execute(() -> {
				synchronized (writeLock) {
					closeWriter();
				}
			});
		} catch (RuntimeException e) {
			LOG.warn("[cubewheel] capture close failed: {}", e.toString());
		}
	}

	private void flushWriter() {
		synchronized (writeLock) {
			unflushed.set(false);
			if (out == null) return;
			try {
				out.flush();
			} catch (IOException e) {
				LOG.warn("[cubewheel] capture write to {} failed: {}", openFile, e.toString());
				closeWriter();
			}
		}
	}

	/** Flushes and closes the open writer, if any (holding {@link #writeLock}). */
	private void closeWriter() {
		Writer w = out;
		out = null;
		openFile = null;
		unflushed.set(false);
		if (w == null) return;
		try {
			w.close();
		} catch (IOException e) {
			LOG.warn("[cubewheel] capture close failed: {}", e.toString());
		}
	}
}
