package net.mage.cubewheel.cooldown;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.mage.cubewheel.cooldown.McmmoParser.Ability;
import net.mage.cubewheel.cooldown.McmmoParser.Tool;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * mcMMO super-ability cooldowns. "X ACTIVATED" starts a countdown of the learned cooldown (or mcMMO's
 * default); "Your X ability is refreshed!" clears it, shows "ready" for {@link #READY_SHOWN_MS} and learns
 * the real cooldown (refresh time - activation time, whole seconds, kept only within
 * {@link #MIN_LEARNED_S}..{@link #MAX_LEARNED_S}); "too tired ... (Ns)" sets the remaining time of the
 * ability of the tool last readied (or the most recent activation). Learned cooldowns persist as JSON
 * ({@code file} may be null: no persistence). Pure: no Minecraft/Fabric imports.
 */
public final class McmmoCooldowns {
	public static final long READY_SHOWN_MS = 5_000;
	public static final int MIN_LEARNED_S = 10;
	public static final int MAX_LEARNED_S = 3_600;

	/** A panel row: {@code endsAt} of the countdown, or {@code ready} (then endsAt = when "ready" hides). */
	public record Row(Ability ability, long endsAt, boolean ready) {}

	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MAP = new TypeToken<Map<String, Integer>>() {}.getType();

	private final Path file;
	private final Map<Ability, Integer> learned = new EnumMap<>(Ability.class);
	private final Map<Ability, Long> endsAt = new EnumMap<>(Ability.class);
	private final Map<Ability, Long> activatedAt = new EnumMap<>(Ability.class);
	private final Map<Ability, Long> readyUntil = new EnumMap<>(Ability.class);
	private Ability lastActivated;
	private Tool lastReadied;

	public McmmoCooldowns(Path file) {
		this.file = file;
	}

	/** Applies a parsed message; returns true if a learned cooldown changed (then {@link #save}). */
	public boolean apply(McmmoParser.Message m, long now) {
		if (m instanceof McmmoParser.Activated a) activated(a.ability(), now);
		else if (m instanceof McmmoParser.Refreshed r) return refreshed(r.ability(), now);
		else if (m instanceof McmmoParser.Readied r) lastReadied = r.tool();
		else if (m instanceof McmmoParser.TooTired t) tooTired(t.seconds(), now);
		return false;
	}

	/** Cooldown in seconds for {@code a}: learned, else mcMMO's default. */
	public int cooldownS(Ability a) {
		Integer s = learned.get(a);
		return s != null ? s : a.defaultCooldownS;
	}

	private void activated(Ability a, long now) {
		endsAt.put(a, now + cooldownS(a) * 1000L);
		activatedAt.put(a, now);
		readyUntil.remove(a);
		lastActivated = a;
	}

	private boolean refreshed(Ability a, long now) {
		endsAt.remove(a);
		readyUntil.put(a, now + READY_SHOWN_MS);
		Long at = activatedAt.remove(a);
		if (at == null) return false;
		long s = Math.round((now - at) / 1000.0);
		if (s < MIN_LEARNED_S || s > MAX_LEARNED_S) return false;
		Integer old = learned.put(a, (int) s);
		return old == null || old != s;
	}

	private void tooTired(int seconds, long now) {
		Ability a = forTool(lastReadied);
		if (a == null) a = lastActivated;
		if (a == null || seconds <= 0) return;
		endsAt.put(a, now + seconds * 1000L);
		readyUntil.remove(a);
	}

	/** The ability a readied tool belongs to; null if none was readied. */
	private Ability forTool(Tool t) {
		if (t == null) return null;
		return switch (t) {
			case PICKAXE -> Ability.SUPER_BREAKER;
			case SHOVEL -> Ability.GIGA_DRILL_BREAKER;
			case AXE -> lastActivated == Ability.SKULL_SPLITTER ? Ability.SKULL_SPLITTER : Ability.TREE_FELLER;
			case HOE -> Ability.GREEN_TERRA;
			case SWORD -> Ability.SERRATED_STRIKES;
			case FISTS -> Ability.BERSERK;
		};
	}

	/** Running countdowns (soonest first), then abilities refreshed within {@link #READY_SHOWN_MS}. */
	public List<Row> rows(long now) {
		endsAt.values().removeIf(e -> e <= now);
		readyUntil.values().removeIf(e -> e <= now);
		List<Row> running = new ArrayList<>();
		endsAt.forEach((a, e) -> running.add(new Row(a, e, false)));
		running.sort(Comparator.comparingLong(Row::endsAt).thenComparing(r -> r.ability().label));
		List<Row> out = new ArrayList<>(running);
		readyUntil.forEach((a, e) -> out.add(new Row(a, e, true)));
		return out;
	}

	/** Learned cooldowns in seconds (read-only copy). */
	public Map<Ability, Integer> learned() {
		return new EnumMap<>(learned);
	}

	public void load() {
		if (file == null || !Files.isRegularFile(file)) return;
		try {
			Map<String, Integer> m = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP);
			if (m == null) return;
			m.forEach((k, v) -> Ability.byName(k).ifPresent(a -> {
				if (v != null && v >= MIN_LEARNED_S && v <= MAX_LEARNED_S) learned.put(a, v);
			}));
		} catch (IOException | JsonParseException | IllegalStateException e) {
			LOG.warn("[cubewheel] could not read {}: {}", file, e.toString());
		}
	}

	public void save() {
		if (file == null) return;
		try {
			Map<String, Integer> m = new LinkedHashMap<>();
			learned.forEach((a, s) -> m.put(a.label, s));
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(m, MAP), StandardCharsets.UTF_8);
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOG.warn("[cubewheel] could not write {}: {}", file, e.toString());
		}
	}
}
