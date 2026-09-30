package net.mage.cubewheel.tracker.local;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Shears used by the local player on a shearable entity, waiting for the server to confirm the shear
 * (the entity's sheared flag syncing to true). The used-on entity waits {@link #TARGET_TICKS}; unsheared
 * shearables near it (an area shears tool) wait {@link #AREA_TICKS}. Only ids recorded here can ever be
 * confirmed, each at most once, so sheep sheared by other players never count. Bounded to
 * {@link #CAPACITY} entries (oldest dropped). Pure: no Minecraft/Fabric imports.
 */
public final class PendingShears {
	public static final int TARGET_TICKS = 40;
	public static final int AREA_TICKS = 20;
	public static final int CAPACITY = 64;

	/** What the world says about a pending entity this tick. */
	public enum State { SHEARED, NOT_YET, GONE }

	public enum Result { CONFIRMED, EXPIRED, GONE }

	/** A pending entity settled this tick; {@code area} for a nearby entity rather than the one used on. */
	public record Outcome(int entityId, Result result, boolean area) {}

	private record Entry(long deadline, boolean area) {}

	private final Map<Integer, Entry> pending = new LinkedHashMap<>();

	/**
	 * Shears used on {@code targetId} at {@code tick}; {@code nearbyIds} are the unsheared shearables around
	 * it at that moment. A re-used id restarts its wait; the target never becomes an area entry.
	 */
	public void use(int targetId, Collection<Integer> nearbyIds, long tick) {
		put(targetId, new Entry(tick + TARGET_TICKS, false));
		if (nearbyIds == null) return;
		for (Integer id : nearbyIds) {
			if (id == null || id == targetId) continue;
			Entry old = pending.get(id);
			if (old != null && !old.area()) continue; // still waiting as a target: keep the longer wait
			put(id, new Entry(tick + AREA_TICKS, true));
		}
	}

	private void put(int id, Entry e) {
		pending.remove(id);
		pending.put(id, e);
		while (pending.size() > CAPACITY) {
			Iterator<Integer> it = pending.keySet().iterator();
			it.next();
			it.remove();
		}
	}

	/**
	 * Checks every pending id (only those) against {@code state}: sheared ones are confirmed, removed ones
	 * and ones past their wait are dropped. Returns what settled, in recording order.
	 */
	public List<Outcome> tick(long tick, IntFunction<State> state) {
		if (pending.isEmpty()) return List.of();
		List<Outcome> out = new ArrayList<>();
		Iterator<Map.Entry<Integer, Entry>> it = pending.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Entry> e = it.next();
			State s = state.apply(e.getKey());
			Result r = s == State.SHEARED ? Result.CONFIRMED
					: s == State.GONE || s == null ? Result.GONE
					: tick >= e.getValue().deadline() ? Result.EXPIRED : null;
			if (r == null) continue;
			out.add(new Outcome(e.getKey(), r, e.getValue().area()));
			it.remove();
		}
		return out;
	}

	public boolean isEmpty() {
		return pending.isEmpty();
	}

	public int size() {
		return pending.size();
	}

	public boolean pending(int id) {
		return pending.containsKey(id);
	}

	public void clear() {
		pending.clear();
	}
}
