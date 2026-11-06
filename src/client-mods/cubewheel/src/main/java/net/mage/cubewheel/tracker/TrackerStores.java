package net.mage.cubewheel.tracker;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import net.mage.cubewheel.sidebar.Gamemode;

/**
 * One {@link TrackerStore} per gamemode, so Skyblock progress never mixes with Survival. Michael 2026-11-06:
 * Survival and Other share the original {@code cubewheel-tracker.json}; Skyblock gets
 * {@code cubewheel-tracker-skyblock.json}. A store is created and loaded on first {@link #of}.
 */
public final class TrackerStores {
	private final Path configDir;
	private final Map<Gamemode, TrackerStore> loaded = new EnumMap<>(Gamemode.class);

	public TrackerStores(Path configDir) {
		this.configDir = configDir;
	}

	/** Asked a dozen times a frame, from the render and client threads alike. */
	public synchronized TrackerStore of(Gamemode mode) {
		Gamemode key = mode == Gamemode.SKYBLOCK ? Gamemode.SKYBLOCK : Gamemode.SURVIVAL;
		TrackerStore s = loaded.get(key);
		if (s == null) {
			String file = key == Gamemode.SKYBLOCK ? "cubewheel-tracker-skyblock.json" : "cubewheel-tracker.json";
			s = new TrackerStore(configDir.resolve(file));
			s.load();
			loaded.put(key, s);
		}
		return s;
	}

	/** Visits the stores {@link #of} has created so far (a snapshot, so {@code f} may call {@link #of}). */
	public void forEachLoaded(Consumer<TrackerStore> f) {
		java.util.List<TrackerStore> snapshot;
		synchronized (this) {
			snapshot = java.util.List.copyOf(loaded.values());
		}
		snapshot.forEach(f);
	}
}
