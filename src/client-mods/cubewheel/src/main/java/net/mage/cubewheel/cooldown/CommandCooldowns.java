package net.mage.cubewheel.cooldown;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.mage.cubewheel.boosters.BoosterParser;
import net.mage.cubewheel.hud.Durations;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cooldowns of server commands such as /heal, learned from the server's replies. A configured command
 * ({@code cooldowns.commands}: command -> placeholder length) that was just sent is "pending" for
 * {@link #REPLY_WINDOW_MS}; the next server line in that window decides:
 * <ul>
 *   <li>its success line ({@link #SUCCESS}: "You have been healed.") starts a countdown of the known length
 *   (learned, else the placeholder) and remembers the time; a success while the countdown still runs means the
 *   real cooldown is shorter: it restarts and the length becomes now - last success;</li>
 *   <li>a refusal, i.e. a server line naming the command word and a duration ("... wait 2m 30s ... /heal ..."; the
 *   real wording is unknown), sets the remaining time, and with a known last success learns
 *   length = (now - last success) + remaining;</li>
 *   <li>nothing within the window: nothing starts (the command may have failed for another reason).</li>
 * </ul>
 * Player chat never counts ({@link BoosterParser#isPlayerChat}). Learned lengths persist as JSON, whole seconds per
 * command ({@code {"/heal": 450}}; {@code file} may be null: no persistence). Pure: no Minecraft/Fabric imports.
 */
public final class CommandCooldowns {
	/** How long after a sent command its reply may arrive. */
	public static final long REPLY_WINDOW_MS = 3_000;
	public static final long MIN_LEARNED_MS = 1_000;
	public static final long MAX_LEARNED_MS = 24 * 3_600_000L;

	/** A panel row: the command ("/heal"), its label ("Heal"), when the countdown ends and the icon's item id. */
	public record Row(String command, String label, long endsAt, String icon) {}

	/** Success lines per command; a command without one can only be started by a refusal. */
	static final Map<String, Pattern> SUCCESS = Map.of(
			"/heal", Pattern.compile("(?i)^you have been healed\\.?$"));
	static final Map<String, String> ICONS = Map.of("/heal", "minecraft:golden_apple");
	static final String DEFAULT_ICON = "minecraft:paper";

	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MAP = new TypeToken<Map<String, Long>>() {}.getType();
	/** "2m 30s", "45 seconds", "1 hr": every part is summed. Longer unit spellings first, so "min" is not "m". */
	private static final Pattern DURATION = Pattern.compile(
			"(\\d+)\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)(?![a-z])", Pattern.CASE_INSENSITIVE);

	private final Path file;
	private final Map<String, Long> placeholders = new LinkedHashMap<>();
	private final Map<String, Long> learned = new LinkedHashMap<>();
	private final Map<String, Long> endsAt = new LinkedHashMap<>();
	private final Map<String, Long> lastSuccess = new LinkedHashMap<>();
	private String pending;
	private long pendingAt;

	public CommandCooldowns(Path file) {
		this.file = file;
	}

	/** Replaces the configured commands (command -> placeholder length, "5m"); unparseable lengths are skipped. */
	public void configure(Map<String, String> commands) {
		placeholders.clear();
		if (commands == null) return;
		for (Map.Entry<String, String> e : commands.entrySet()) {
			String k = key(e.getKey());
			OptionalLong ms = Durations.parse(e.getValue());
			if (k == null || ms.isEmpty() || ms.getAsLong() <= 0) continue;
			placeholders.put(k, ms.getAsLong());
		}
	}

	/** "/heal" for " /Heal me ", "heal", "HEAL extra"; null for nothing. */
	public static String key(String command) {
		if (command == null) return null;
		String c = command.trim();
		if (c.startsWith("/")) c = c.substring(1);
		int space = c.indexOf(' ');
		if (space >= 0) c = c.substring(0, space);
		c = c.trim().toLowerCase(Locale.ROOT);
		return c.isEmpty() ? null : "/" + c;
	}

	/** "Heal" for "/heal". */
	public static String label(String command) {
		String word = command.startsWith("/") ? command.substring(1) : command;
		return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}

	public static String icon(String command) {
		return ICONS.getOrDefault(command, DEFAULT_ICON);
	}

	/** Known length in ms: learned, else the placeholder, else 0 (not configured, nothing learned). */
	public long cooldownMs(String command) {
		String k = key(command);
		if (k == null) return 0;
		Long ms = learned.get(k);
		if (ms == null) ms = placeholders.get(k);
		return ms == null ? 0 : ms;
	}

	/** A command was sent (typed or from the wheel); only configured ones are watched for a reply. */
	public void sent(String commandWithSlash, long now) {
		String k = key(commandWithSlash);
		if (k == null || !placeholders.containsKey(k)) return;
		pending = k;
		pendingAt = now;
	}

	/** A chat line; returns true if a learned length changed (then {@link #save}). */
	public boolean chat(String text, long now) {
		if (pending == null || text == null) return false;
		if (now - pendingAt > REPLY_WINDOW_MS || now < pendingAt) {
			pending = null;
			return false;
		}
		if (BoosterParser.isPlayerChat(text)) return false;
		String s = BoosterParser.clean(text);
		String cmd = pending;
		Pattern success = SUCCESS.get(cmd);
		if (success != null && success.matcher(s).matches()) {
			pending = null;
			return succeeded(cmd, now);
		}
		long remaining = refusal(cmd, s);
		if (remaining > 0) {
			pending = null;
			return refused(cmd, remaining, now);
		}
		return false;
	}

	private boolean succeeded(String cmd, long now) {
		boolean changed = false;
		Long last = lastSuccess.get(cmd);
		Long ends = endsAt.get(cmd);
		if (last != null && ends != null && ends > now) changed = learn(cmd, now - last); // shorter than we thought
		lastSuccess.put(cmd, now);
		endsAt.put(cmd, now + cooldownMs(cmd));
		return changed;
	}

	private boolean refused(String cmd, long remaining, long now) {
		endsAt.put(cmd, now + remaining);
		Long last = lastSuccess.get(cmd);
		return last != null && learn(cmd, (now - last) + remaining);
	}

	/** Keeps {@code ms} (whole seconds) as the command's learned length when plausible; true if it changed. */
	private boolean learn(String cmd, long ms) {
		long s = Math.round(ms / 1000.0) * 1000;
		if (s < MIN_LEARNED_MS || s > MAX_LEARNED_MS) return false;
		Long old = learned.put(cmd, s);
		return old == null || old != s;
	}

	/** The remaining ms a server line states for {@code cmd} (it names the command word and a duration), else 0. */
	private static long refusal(String cmd, String line) {
		String word = cmd.substring(1);
		if (!Pattern.compile("(?i)(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])").matcher(line).find()) return 0;
		Matcher m = DURATION.matcher(line);
		long total = 0;
		while (m.find()) {
			long n = Long.parseLong(m.group(1));
			total += switch (Character.toLowerCase(m.group(2).charAt(0))) {
				case 'h' -> n * 3_600_000L;
				case 'm' -> n * 60_000L;
				default -> n * 1_000L;
			};
		}
		return total;
	}

	/** Running countdowns, soonest first. */
	public List<Row> rows(long now) {
		endsAt.values().removeIf(e -> e <= now);
		List<Row> out = new ArrayList<>();
		endsAt.forEach((c, e) -> out.add(new Row(c, label(c), e, icon(c))));
		out.sort(Comparator.comparingLong(Row::endsAt).thenComparing(Row::command));
		return out;
	}

	/** Learned lengths in ms (read-only copy). */
	public Map<String, Long> learned() {
		return new LinkedHashMap<>(learned);
	}

	public void load() {
		if (file == null || !Files.isRegularFile(file)) return;
		try {
			Map<String, Long> m = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP);
			if (m == null) return;
			m.forEach((k, v) -> {
				String c = key(k);
				if (c != null && v != null && v * 1000 >= MIN_LEARNED_MS && v * 1000 <= MAX_LEARNED_MS) learned.put(c, v * 1000);
			});
		} catch (IOException | JsonParseException | IllegalStateException e) {
			LOG.warn("[cubewheel] could not read {}: {}", file, e.toString());
		}
	}

	public void save() {
		if (file == null) return;
		try {
			Map<String, Long> m = new LinkedHashMap<>();
			learned.forEach((c, ms) -> m.put(c, ms / 1000));
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(m, MAP), StandardCharsets.UTF_8);
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOG.warn("[cubewheel] could not write {}: {}", file, e.toString());
		}
	}
}
