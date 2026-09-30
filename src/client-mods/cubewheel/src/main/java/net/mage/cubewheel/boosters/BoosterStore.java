package net.mage.cubewheel.boosters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Active boosters with absolute end times, persisted as JSON so countdowns survive a restart. Keyed by type
 * (case-insensitive) and multiplier. Pure: no Minecraft/Fabric imports.
 */
public final class BoosterStore {
	/** One booster; {@code endsAt} is epoch milliseconds. */
	public record Booster(String type, double multiplier, long endsAt) {
		String key() {
			return key(type, multiplier);
		}

		static String key(String type, double multiplier) {
			return type.trim().toLowerCase(Locale.ROOT) + "|" + multiplier;
		}

		/** "2x Sell", "1.5x mcMMO". */
		public String label() {
			String x = multiplier == Math.rint(multiplier) ? Long.toString((long) multiplier)
					: String.format(Locale.ROOT, "%s", multiplier);
			return x + "x " + type;
		}
	}

	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST = new TypeToken<List<Booster>>() {}.getType();

	private final Path file;
	private final Map<String, Booster> boosters = new LinkedHashMap<>();

	public BoosterStore(Path file) {
		this.file = file;
	}

	/**
	 * Applies a parsed message; returns true if the stored boosters changed (then save). RECEIVED never
	 * shortens a booster that is still running (whether ManaCube stacks or queues is unknown; an "extended"
	 * message corrects it); EXTENDED sets the new remaining time; ENDED removes it.
	 */
	public boolean apply(BoosterParser.Message m, long now) {
		if (m == null || m.type() == null || m.type().isBlank()) return false;
		prune(now);
		String key = Booster.key(m.type(), m.multiplier());
		Booster old = boosters.get(key);
		switch (m.kind()) {
			case ENDED -> {
				return boosters.remove(key) != null;
			}
			case RECEIVED -> {
				long end = now + m.durationMs();
				if (old != null && old.endsAt() >= end) return false;
				boosters.put(key, new Booster(m.type().trim(), m.multiplier(), end));
				return true;
			}
			case EXTENDED -> {
				boosters.put(key, new Booster(m.type().trim(), m.multiplier(), now + m.durationMs()));
				return true;
			}
		}
		return false;
	}

	/** Boosters still running at {@code now}, soonest-ending first. */
	public List<Booster> active(long now) {
		List<Booster> out = new ArrayList<>();
		for (Booster b : boosters.values()) {
			if (b.endsAt() > now) out.add(b);
		}
		out.sort(Comparator.comparingLong(Booster::endsAt).thenComparing(Booster::type));
		return out;
	}

	private void prune(long now) {
		boosters.values().removeIf(b -> b.endsAt() <= now);
	}

	/** Loads the file, dropping expired and malformed entries; a missing or corrupt file loads empty. */
	public void load(long now) {
		boosters.clear();
		if (!Files.exists(file)) return;
		try {
			List<Booster> list = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LIST);
			if (list == null) return;
			for (Booster b : list) {
				if (b == null || b.type() == null || b.type().isBlank() || b.endsAt() <= now) continue;
				boosters.put(b.key(), b);
			}
		} catch (IOException | JsonParseException | IllegalStateException e) {
			LOG.warn("[cubewheel] could not read boosters from {}: {}", file, e.toString());
		}
	}

	/** Writes the boosters; returns false (and logs) on IO errors. */
	public boolean save() {
		try {
			Path parent = file.getParent();
			if (parent != null) Files.createDirectories(parent);
			Files.writeString(file, GSON.toJson(new ArrayList<>(boosters.values()), LIST), StandardCharsets.UTF_8);
			return true;
		} catch (IOException | RuntimeException e) {
			LOG.warn("[cubewheel] could not save boosters to {}: {}", file, e.toString());
			return false;
		}
	}
}
