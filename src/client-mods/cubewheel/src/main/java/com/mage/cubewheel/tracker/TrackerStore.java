package com.mage.cubewheel.tracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tracked progress items and pins, persisted as JSON. Pure: no Minecraft/Fabric imports. */
public final class TrackerStore {
	static final class Snapshot {
		List<Trackable> items = new ArrayList<>();
		List<String> pins = new ArrayList<>();
	}

	/** Minimum last-seen advance that counts as a change on its own (TrackerFormat.age shows "now" below this). */
	public static final long SEEN_REFRESH_MS = 60_000;

	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Comparator<Trackable> BY_FRACTION_THEN_NAME =
		Comparator.comparingDouble(Trackable::fraction).reversed().thenComparing(Trackable::name);

	private final Path file;
	private Map<String, Trackable> items = new LinkedHashMap<>();
	private Set<String> pins = new LinkedHashSet<>();

	public TrackerStore(Path file) {
		this.file = file;
	}

	/**
	 * Records progress; returns true if something worth persisting changed: a new item, a different
	 * current/max, or a last-seen time at least {@link #SEEN_REFRESH_MS} newer (the age display's
	 * granularity). Otherwise the stored entry, including its timestamp, is left untouched.
	 */
	public boolean update(String source, String name, ProgressExtractor.Progress p, long now) {
		String id = Trackable.idOf(source, name);
		Trackable old = items.get(id);
		if (old != null && old.current() == p.current() && old.max() == p.max()
				&& now - old.seenAt() < SEEN_REFRESH_MS) {
			return false;
		}
		items.put(id, new Trackable(id, source, name, p.current(), p.max(), now));
		return true;
	}

	/**
	 * A live value (e.g. from the sidebar) for every incomplete item whose name matches {@code names}:
	 * current becomes max(current, value) and the item counts as seen at {@code now}. Returns how many
	 * items were touched.
	 */
	public int applyLiveValue(Pattern names, double value, long now) {
		if (names == null || !Double.isFinite(value)) return 0;
		int touched = 0;
		for (Map.Entry<String, Trackable> e : items.entrySet()) {
			Trackable t = e.getValue();
			if (t.complete() || t.name() == null || !names.matcher(t.name()).find()) continue;
			e.setValue(new Trackable(t.id(), t.source(), t.name(), Math.max(t.current(), value), t.max(), now));
			touched++;
		}
		return touched;
	}

	/** How many items were last seen at or after {@code since}. */
	public int countSeenSince(long since) {
		int n = 0;
		for (Trackable t : items.values()) {
			if (t.seenAt() >= since) n++;
		}
		return n;
	}

	/** Sorted by fraction descending, then name. */
	public List<Trackable> all() {
		List<Trackable> out = new ArrayList<>(items.values());
		out.sort(BY_FRACTION_THEN_NAME);
		return out;
	}

	public boolean isPinned(String id) {
		return pins.contains(id);
	}

	public void togglePin(String id) {
		if (!pins.remove(id)) pins.add(id);
	}

	/** Pinned items first, then incomplete items at or above {@code nearThreshold}; capped at {@code maxLines}. */
	public List<Trackable> hudEntries(double nearThreshold, int maxLines) {
		List<Trackable> pinned = new ArrayList<>();
		List<Trackable> near = new ArrayList<>();
		for (Trackable t : items.values()) {
			if (pins.contains(t.id())) pinned.add(t);
			else if (t.fraction() >= nearThreshold && !t.complete()) near.add(t);
		}
		pinned.sort(BY_FRACTION_THEN_NAME);
		near.sort(BY_FRACTION_THEN_NAME);
		List<Trackable> out = new ArrayList<>(pinned);
		out.addAll(near);
		return out.size() > maxLines ? new ArrayList<>(out.subList(0, Math.max(0, maxLines))) : out;
	}

	/** Removes unpinned items last seen more than {@code ageMs} ago; returns how many were removed. */
	public int forgetOlderThan(long now, long ageMs) {
		int before = items.size();
		items.values().removeIf(t -> !pins.contains(t.id()) && now - t.seenAt() > ageMs);
		return before - items.size();
	}

	/** Loads the store; a missing, unreadable or corrupt file yields an empty store. */
	public void load() {
		Snapshot snap = null;
		try {
			snap = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Snapshot.class);
		} catch (IOException | RuntimeException ignored) {
			// missing or corrupt: fall through to empty
		}
		Map<String, Trackable> newItems = new LinkedHashMap<>();
		Set<String> newPins = new LinkedHashSet<>();
		if (snap != null) {
			if (snap.items != null) {
				for (Trackable t : snap.items) {
					if (t == null || t.id() == null || t.name() == null) continue;
					newItems.put(t.id(), t);
				}
			}
			if (snap.pins != null) {
				for (String pin : snap.pins) {
					if (pin != null) newPins.add(pin);
				}
			}
		}
		items = newItems;
		pins = newPins;
	}

	/** Best effort (the tracker is re-scannable): returns false and logs a warning if the file could not be written. */
	public boolean save() {
		Snapshot snap = new Snapshot();
		snap.items = new ArrayList<>(items.values());
		snap.pins = new ArrayList<>(pins);
		try {
			Path parent = file.getParent();
			if (parent != null) Files.createDirectories(parent);
			Files.writeString(file, GSON.toJson(snap), StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			LOG.warn("[cubewheel] could not save tracker to {}: {}", file, e.toString());
			return false;
		}
	}
}
