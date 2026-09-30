package com.mage.cubewheel.tracker.local;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Blocks the player placed (most job and quest plugins do not count breaking those). A later break of
 * the same position in the same state is not counted; a planted crop that grew is a different state and
 * still counts. LRU-bounded. Pure: no Minecraft/Fabric imports.
 */
public final class PlacedBlocks {
	public static final int DEFAULT_CAPACITY = 4096;

	private final Map<Pos, Integer> placed;

	public PlacedBlocks(int capacity) {
		int cap = Math.max(1, capacity);
		this.placed = new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Pos, Integer> eldest) {
				return size() > cap;
			}
		};
	}

	public void placed(Pos pos, int stateId) {
		if (pos != null) placed.put(pos, stateId);
	}

	/** True (and forgotten) if {@code pos} was placed by the player and still has the placed state. */
	public boolean consumeIfPlaced(Pos pos, int stateId) {
		Integer id = placed.remove(pos);
		return id != null && id == stateId;
	}

	public void clear() {
		placed.clear();
	}
}
