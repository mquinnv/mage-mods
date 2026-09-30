package com.mage.cubewheel.tracker.local;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers, per entity, the last player that damaged it (from damage-event cause ids and own attacks).
 * A death counts for the local player if that player was the last to hit it within
 * {@link #MEMORY_TICKS}, like vanilla's lastHurtByPlayer that plugins read through getKiller().
 * Bounded to {@link #MAX} entities. Pure: no Minecraft/Fabric imports.
 */
public final class KillAttribution {
	public static final int MAX = 512;
	public static final int MEMORY_TICKS = 100;

	private record Hit(int playerId, long tick) {}

	private final Map<Integer, Hit> lastHit = new LinkedHashMap<>();

	/** {@code entityId} was damaged by player {@code playerId} (call only for player causes). */
	public void onDamage(int entityId, int playerId, long tick) {
		lastHit.remove(entityId);
		lastHit.put(entityId, new Hit(playerId, tick));
		while (lastHit.size() > MAX) {
			Iterator<Integer> it = lastHit.keySet().iterator();
			it.next();
			it.remove();
		}
	}

	/** {@code entityId} died: true if the local player gets the kill. The entry is consumed either way. */
	public boolean onDeath(int entityId, int localId, long tick) {
		Hit hit = lastHit.remove(entityId);
		return hit != null && hit.playerId() == localId && tick - hit.tick() <= MEMORY_TICKS;
	}

	public void expire(long tick) {
		lastHit.values().removeIf(h -> tick - h.tick() > MEMORY_TICKS);
	}

	public int size() {
		return lastHit.size();
	}

	public void clear() {
		lastHit.clear();
	}
}
