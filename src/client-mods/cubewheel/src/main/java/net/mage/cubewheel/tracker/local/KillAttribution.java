package net.mage.cubewheel.tracker.local;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers, per entity, the last player that damaged it (from damage-event cause ids and own attacks).
 * A death counts for the local player if that player was the last to hit it within
 * {@link #MEMORY_TICKS}, like vanilla's lastHurtByPlayer that plugins read through getKiller(). Hits are kept
 * {@link #RETAIN_TICKS} for removal kills, whose hitbox window is longer ({@link RemovalKills#HITBOX_WINDOW_TICKS}).
 * Bounded to {@link #MAX} entities. Pure: no Minecraft/Fabric imports.
 */
public final class KillAttribution {
	public static final int MAX = 512;
	public static final int MEMORY_TICKS = 100;
	/** How long a hit is kept at all: the longest window anything asks about. */
	public static final int RETAIN_TICKS = Math.max(MEMORY_TICKS, RemovalKills.HITBOX_WINDOW_TICKS);

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

	/** Why a death was or was not credited to the local player (also written to capture). */
	public enum Verdict { LOCAL, OTHER_PLAYER, EXPIRED, NO_HIT }

	/** {@code entityId} died: true if the local player gets the kill. The entry is consumed either way. */
	public boolean onDeath(int entityId, int localId, long tick) {
		return onDeathVerdict(entityId, localId, tick) == Verdict.LOCAL;
	}

	/** {@code entityId} died: who gets it. The entry is consumed either way. */
	public Verdict onDeathVerdict(int entityId, int localId, long tick) {
		Hit hit = lastHit.remove(entityId);
		if (hit == null) return Verdict.NO_HIT;
		if (hit.playerId() != localId) return Verdict.OTHER_PLAYER;
		return tick - hit.tick() <= MEMORY_TICKS ? Verdict.LOCAL : Verdict.EXPIRED;
	}

	/** Was {@code playerId} the last player to hit {@code entityId}, within {@link #MEMORY_TICKS}? Not consumed. */
	public boolean hitBy(int entityId, int playerId, long tick) {
		Hit hit = lastHit.get(entityId);
		return hit != null && hit.playerId() == playerId && tick - hit.tick() <= MEMORY_TICKS;
	}

	/** Was {@code playerId} the last player to hit {@code entityId}, at most {@code window} ticks ago? Not consumed. */
	public boolean hitByWithin(int entityId, int playerId, long tick, int window) {
		Hit hit = lastHit.get(entityId);
		return hit != null && hit.playerId() == playerId && tick - hit.tick() <= window;
	}

	/** Is any recent hit on {@code entityId} remembered? */
	public boolean tracks(int entityId) {
		return lastHit.containsKey(entityId);
	}

	public void expire(long tick) {
		lastHit.values().removeIf(h -> tick - h.tick() > RETAIN_TICKS);
	}

	public int size() {
		return lastHit.size();
	}

	public void clear() {
		lastHit.clear();
	}
}
