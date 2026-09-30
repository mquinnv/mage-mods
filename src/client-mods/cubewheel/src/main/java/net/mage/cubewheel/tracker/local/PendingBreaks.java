package net.mage.cubewheel.tracker.local;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Own breaks counted immediately but not yet confirmed by the server. When the client's block
 * prediction is settled, the server's state for that position arrives; if it equals the state before
 * the break, the server rejected it (claim, anti-cheat) and the contributions are taken back. Pure: no
 * Minecraft/Fabric imports.
 */
public final class PendingBreaks {
	public static final int MAX = 64;
	public static final int EXPIRE_TICKS = 100; // 5 s: generous for lag, still short-lived

	private record Pending(int preStateId, List<LocalCounter.Contribution> contributions, long tick) {}

	private final Map<Pos, Pending> pending = new LinkedHashMap<>();

	public void record(Pos pos, int preStateId, List<LocalCounter.Contribution> contributions, long tick) {
		if (pos == null || contributions == null || contributions.isEmpty()) return;
		pending.remove(pos);
		pending.put(pos, new Pending(preStateId, List.copyOf(contributions), tick));
		while (pending.size() > MAX) {
			Iterator<Pos> it = pending.keySet().iterator();
			it.next();
			it.remove();
		}
	}

	/**
	 * The server's verified state for {@code pos}: returns the contributions to reverse when it equals the
	 * pre-break state (a rejection). Any settled position is forgotten.
	 */
	public Optional<List<LocalCounter.Contribution>> onSync(Pos pos, int stateId) {
		Pending p = pending.remove(pos);
		if (p == null || p.preStateId() != stateId) return Optional.empty();
		return Optional.of(p.contributions());
	}

	public void expire(long tick) {
		pending.values().removeIf(p -> tick - p.tick() > EXPIRE_TICKS);
	}

	public boolean isEmpty() {
		return pending.isEmpty();
	}

	public int size() {
		return pending.size();
	}

	public void clear() {
		pending.clear();
	}
}
