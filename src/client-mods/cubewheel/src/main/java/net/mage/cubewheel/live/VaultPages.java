package net.mage.cubewheel.live;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.mage.cubewheel.tracker.ContainerScanner.ItemView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * How many player vaults (/pv pages) each account has, read from the page buttons along the bottom of any /pv
 * page and persisted as JSON ({@code config/cubewheel-vaults.json}). Pure: no Minecraft/Fabric imports.
 *
 * <p>Seen 2026-10-01: "Page 1" … "Page 7"; locked ones say "You have not unlocked page 5" / "Unlock more pages
 * in /cubitshop".
 */
public final class VaultPages {
	private static final Pattern PAGE = Pattern.compile("^Page (\\d+)$");
	private static final Pattern LOCKED = Pattern.compile("(?i)not unlocked|unlock more pages");
	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MAP = new TypeToken<LinkedHashMap<String, Integer>>() {}.getType();

	/** A /pv page: 45 storage slots, then the row of page buttons. */
	public static final int STORAGE = 45;
	private static final Type FILLS = new TypeToken<LinkedHashMap<String, LinkedHashMap<Integer, Integer>>>() {}.getType();

	private final Path file;
	/** Used slots per page, as of the page's last open/close ({@code cubewheel-vaults-fill.json} beside the counts). */
	private final Path fillFile;
	private Map<String, Integer> counts = new LinkedHashMap<>();
	private Map<String, Map<Integer, Integer>> fills = new LinkedHashMap<>();

	public VaultPages(Path file) {
		this.file = file;
		String name = file.getFileName().toString();
		this.fillFile = file.resolveSibling(name.endsWith(".json") ? name.substring(0, name.length() - 5) + "-fill.json" : name + "-fill");
	}

	/** The open page: its button is the one unlocked lime wool (the others are ender pearls); empty if unclear. */
	public static OptionalInt current(List<ItemView> items) {
		if (items == null) return OptionalInt.empty();
		int found = -1;
		for (ItemView item : items) {
			Matcher m = PAGE.matcher(CowParser.strip(item.name()).trim());
			if (!m.matches() || !"minecraft:lime_wool".equals(item.id())) continue;
			boolean locked = false;
			if (item.lore() != null) for (String l : item.lore()) locked |= LOCKED.matcher(CowParser.strip(l)).find();
			if (locked) continue;
			if (found >= 0) return OptionalInt.empty();
			found = Integer.parseInt(m.group(1));
		}
		return found > 0 ? OptionalInt.of(found) : OptionalInt.empty();
	}

	/** Non-empty storage slots of a /pv page (the button row excluded). */
	public static int used(List<ItemView> items) {
		int n = 0;
		if (items != null) for (ItemView item : items) if (item.slot() < STORAGE) n++;
		return n;
	}

	/** Records how full {@code page} is; returns true if that changed. */
	public boolean fill(String player, int page, int used) {
		if (player == null || player.isBlank() || page <= 0) return false;
		Integer old = fills.computeIfAbsent(key(player), k -> new LinkedHashMap<>()).put(page, used);
		return old == null || old != used;
	}

	/** Used slots by page for the account (pages never opened are absent). */
	public Map<Integer, Integer> fill(String player) {
		Map<Integer, Integer> m = player == null ? null : fills.get(key(player));
		return m == null ? Map.of() : m;
	}

	/** The highest unlocked page among the menu's "Page N" buttons; empty if it has none. */
	public static OptionalInt unlocked(List<ItemView> items) {
		if (items == null) return OptionalInt.empty();
		boolean any = false;
		int best = 0;
		for (ItemView item : items) {
			Matcher m = PAGE.matcher(CowParser.strip(item.name()).trim());
			if (!m.matches()) continue;
			any = true;
			boolean locked = false;
			if (item.lore() != null) for (String l : item.lore()) locked |= LOCKED.matcher(CowParser.strip(l)).find();
			if (!locked) best = Math.max(best, Integer.parseInt(m.group(1)));
		}
		return any && best > 0 ? OptionalInt.of(best) : OptionalInt.empty();
	}

	private static String key(String player) {
		return player.trim().toLowerCase(Locale.ROOT);
	}

	/** The account's vault count; null = never seen. */
	public Integer count(String player) {
		return player == null ? null : counts.get(key(player));
	}

	/** Returns true if the count changed. */
	public boolean set(String player, int count) {
		if (player == null || player.isBlank() || count <= 0) return false;
		Integer old = counts.put(key(player), count);
		return old == null || old != count;
	}

	/** Loads the file; a missing or corrupt file loads empty. */
	public void load() {
		counts = new LinkedHashMap<>();
		if (!Files.exists(file)) return;
		try {
			Map<String, Integer> m = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP);
			if (m != null) m.forEach((k, v) -> { if (k != null && v != null) counts.put(key(k), v); });
		} catch (IOException | JsonParseException | IllegalStateException e) {
			LOG.warn("[cubewheel] could not read vault counts from {}: {}", file, e.toString());
		}
		fills = new LinkedHashMap<>();
		if (!Files.exists(fillFile)) return;
		try {
			Map<String, Map<Integer, Integer>> m = GSON.fromJson(Files.readString(fillFile, StandardCharsets.UTF_8), FILLS);
			if (m != null) m.forEach((k, v) -> { if (k != null && v != null) fills.put(key(k), new LinkedHashMap<>(v)); });
		} catch (IOException | JsonParseException | IllegalStateException e) {
			LOG.warn("[cubewheel] could not read vault fill from {}: {}", fillFile, e.toString());
		}
	}

	/** Writes the file; returns false (and logs) on IO errors. */
	public boolean save() {
		try {
			Path parent = file.getParent();
			if (parent != null) Files.createDirectories(parent);
			Files.writeString(file, GSON.toJson(counts, MAP), StandardCharsets.UTF_8);
			if (!fills.isEmpty()) Files.writeString(fillFile, GSON.toJson(fills, FILLS), StandardCharsets.UTF_8);
			return true;
		} catch (IOException | RuntimeException e) {
			LOG.warn("[cubewheel] could not save vault counts to {}: {}", file, e.toString());
			return false;
		}
	}
}
