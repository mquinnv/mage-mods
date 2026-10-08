package net.mage.cubewheel.tracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.mage.cubewheel.io.BackgroundSaver;
import net.mage.cubewheel.tracker.ContainerScanner.ItemView;
import net.mage.cubewheel.tracker.local.FishCatchParser;
import net.mage.cubewheel.tracker.local.WorldInfo;
import net.mage.cubewheel.tracker.local.WorldResolver;
import net.mage.cubewheel.tracker.local.WorldScope;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where each custom fish is caught, read from the Fishing Log (/fish; capture 2026-10-08). Every species item there
 * carries "Found in: Icehaven &amp; Cold Biomes", "Found in: Wolfhaven &amp; Plains, Forest, Mountains", "Found in:
 * Morend &amp; the End" or "Found in: Everywhere": the mana world it lives in, and the overworld biomes that stand in
 * for it. An undiscovered slot ("Undiscovered", iron bars) states a habitat without a name, so it is skipped. Species
 * are keyed as {@link FishCatchParser#speciesKey} ("Hermit Crab" in the log, "HermitCrab" in a job listing). Persists
 * as JSON so the Jobs panel knows the habitats without the log being open ({@code file} null: no persistence). Pure:
 * no Minecraft/Fabric imports.
 */
public final class FishLog {
	/**
	 * A species' habitat: {@code world} is the mana world's key ({@link WorldResolver#key}, "icehaven"), "" when the
	 * fish is found everywhere; {@code biomes} the overworld part as written ("Cold Biomes", "the End").
	 */
	public record Habitat(String name, String world, String biomes) {
		public boolean everywhere() {
			return world == null || world.isEmpty();
		}
	}

	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MAP = new TypeToken<Map<String, Habitat>>() {}.getType();
	/** "Found in: Icehaven & Cold Biomes"; the world is the part before "&", the biomes after it. */
	private static final Pattern FOUND_IN = Pattern.compile("(?i)^found in:\\s*(.+?)\\s*$");
	private static final Pattern EVERYWHERE = Pattern.compile("(?i)^everywhere$");
	private static final String UNDISCOVERED = "undiscovered";

	private final Map<String, Habitat> species = new LinkedHashMap<>();
	private final Path file;
	private final BackgroundSaver saver;

	public FishLog(Path file) {
		this.file = file;
		this.saver = file == null ? null : new BackgroundSaver(file, "fish log");
	}

	/** The habitat {@code lore} states for the species named {@code name}; empty for other items and undiscovered slots. */
	public static Optional<Habitat> parse(String name, List<String> lore) {
		if (name == null || lore == null) return Optional.empty();
		String n = ContainerScanner.strip(name).trim();
		if (n.isEmpty() || n.toLowerCase(Locale.ROOT).equals(UNDISCOVERED)) return Optional.empty();
		for (String raw : lore) {
			Matcher m = FOUND_IN.matcher(ContainerScanner.strip(raw).trim());
			if (!m.matches()) continue;
			String where = m.group(1);
			if (EVERYWHERE.matcher(where).matches()) return Optional.of(new Habitat(n, "", "Everywhere"));
			int amp = where.indexOf('&');
			String world = amp < 0 ? where : where.substring(0, amp).trim();
			String biomes = amp < 0 ? "" : where.substring(amp + 1).trim();
			String key = WorldResolver.key(world);
			if (key.isEmpty()) return Optional.empty();
			return Optional.of(new Habitat(n, key, biomes.isEmpty() ? world : biomes));
		}
		return Optional.empty();
	}

	/** Records every species habitat {@code items} state; returns how many were new or changed. */
	public int scan(List<ItemView> items) {
		if (items == null) return 0;
		int changed = 0;
		for (ItemView item : items) {
			if (item == null) continue;
			Optional<Habitat> h = parse(item.name(), item.lore());
			if (h.isEmpty()) continue;
			String key = FishCatchParser.speciesKey(h.get().name());
			if (key.isEmpty()) continue;
			Habitat old = species.put(key, h.get());
			if (!h.get().equals(old)) changed++;
		}
		return changed;
	}

	/** The habitat of {@code speciesName} ("HermitCrab", "Hermit Crab", "hermit crabs" are one species), if the log showed it. */
	public Optional<Habitat> habitat(String speciesName) {
		String key = FishCatchParser.speciesKey(speciesName);
		return key.isEmpty() ? Optional.empty() : Optional.ofNullable(species.get(key));
	}

	public int size() {
		return species.size();
	}

	/**
	 * How a species relates to the world you are in: CURRENT in its own mana world, OTHER in a different one (the
	 * job cannot be worked on there), NEUTRAL everywhere else: outside the mana worlds its overworld biomes count,
	 * and a fish found everywhere is never out of place.
	 */
	public static WorldScope.Relevance relevance(Habitat h, WorldInfo at) {
		if (h == null || h.everywhere() || at == null || !at.known() || !at.special()) return WorldScope.Relevance.NEUTRAL;
		return at.tokens().contains(h.world()) ? WorldScope.Relevance.CURRENT : WorldScope.Relevance.OTHER;
	}

	public void load() {
		if (file == null || !Files.isRegularFile(file)) return;
		try {
			Map<String, Habitat> m = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP);
			if (m == null) return;
			m.forEach((k, v) -> {
				if (k == null || v == null || v.name() == null) return;
				species.put(k, new Habitat(v.name(), v.world() == null ? "" : v.world(), v.biomes() == null ? "" : v.biomes()));
			});
		} catch (IOException | JsonParseException | IllegalStateException e) {
			LOG.warn("[cubewheel] could not read {}: {}", file, e.toString());
		}
	}

	/** Saves on CubeWheel's background thread (a copy is taken here). */
	public void saveInBackground() {
		if (saver == null) return;
		Map<String, Habitat> copy = new LinkedHashMap<>(species);
		saver.saveLater(() -> GSON.toJson(copy, MAP));
	}

	/** Saves on this thread; true if written. */
	public boolean save() {
		return saver != null && saver.saveNow(GSON.toJson(species, MAP));
	}
}
