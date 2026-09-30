package com.mage.cubewheel.homes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Per-server home name cache persisted as JSON. Pure: no Minecraft/Fabric imports. */
public final class HomesCache {
	static final class Entry {
		List<String> homes = new ArrayList<>();
		long fetchedAt;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MAP_TYPE = new TypeToken<Map<String, Entry>>() {}.getType();

	private final Path file;
	private Map<String, Entry> entries = new HashMap<>();

	public HomesCache(Path file) {
		this.file = file;
	}

	private static String key(String host) {
		return host == null ? "" : host.toLowerCase(Locale.ROOT);
	}

	/** Returns the entry with a non-null list, creating it when absent. */
	private Entry entry(String host) {
		Entry e = entries.computeIfAbsent(key(host), k -> new Entry());
		if (e.homes == null) e.homes = new ArrayList<>();
		return e;
	}

	public List<String> get(String host) {
		Entry e = entries.get(key(host));
		return e == null || e.homes == null ? List.of() : List.copyOf(e.homes);
	}

	public long fetchedAt(String host) {
		Entry e = entries.get(key(host));
		return e == null ? 0 : e.fetchedAt;
	}

	public void put(String host, List<String> homes, long now) {
		Entry e = entry(host);
		e.homes = new ArrayList<>(homes);
		e.fetchedAt = now;
	}

	public void add(String host, String name) {
		Entry e = entry(host);
		if (!e.homes.contains(name)) e.homes.add(name);
	}

	public void remove(String host, String name) {
		Entry e = entries.get(key(host));
		if (e != null && e.homes != null) e.homes.remove(name);
	}

	public boolean isStale(String host, long now, long maxAgeMs) {
		long at = fetchedAt(host);
		return at == 0 || now - at > maxAgeMs;
	}

	/** Loads the cache; a missing, unreadable or corrupt file yields an empty cache. */
	public void load() {
		Map<String, Entry> loaded = null;
		try {
			loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP_TYPE);
		} catch (IOException | RuntimeException ignored) {
			// missing or corrupt: fall through to empty
		}
		Map<String, Entry> clean = new HashMap<>();
		if (loaded != null) {
			for (Map.Entry<String, Entry> me : loaded.entrySet()) {
				if (me.getKey() == null || me.getValue() == null) continue;
				Entry e = me.getValue();
				if (e.homes == null) e.homes = new ArrayList<>();
				clean.put(key(me.getKey()), e);
			}
		}
		entries = clean;
	}

	public void save() {
		try {
			Path parent = file.getParent();
			if (parent != null) Files.createDirectories(parent);
			Files.writeString(file, GSON.toJson(entries, MAP_TYPE), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
			// best effort; the cache is re-fetchable
		}
	}
}
