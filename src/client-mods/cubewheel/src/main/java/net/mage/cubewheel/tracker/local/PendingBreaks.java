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

	/** What the server's state for a position means for the pending break. */
	public enum Verdict {
		/** Nothing to undo: not a restore (air, replant) or nothing pending. */
		NONE,
		/** Restored the previous state: a refusal, take the contributions back. */
		REVERSE,
		/** Restored the previous state in a special (mana) world: a resource node, kept. */
		KEPT_NODE
	}

	private record Pending(int preStateId, List<LocalCounter.Contribution> contributions, long tick, boolean special) {}

	private final Map<Pos, Pending> pending = new LinkedHashMap<>();

	public void record(Pos pos, int preStateId, List<LocalCounter.Contribution> contributions, long tick) {
		record(pos, preStateId, contributions, tick, false);
	}

	/**
	 * {@code special}: the break happened in a special (mana) world, where resource nodes (ice, ...) are
	 * mined by a plugin that cancels the vanilla break and leaves or instantly restores the block (capture
	 * 2026-10-04, Icehaven ice), so a restore there is not a refusal.
	 */
	public void record(Pos pos, int preStateId, List<LocalCounter.Contribution> contributions, long tick,
			boolean special) {
		if (pos == null || contributions == null || contributions.isEmpty()) return;
		pending.remove(pos);
		pending.put(pos, new Pending(preStateId, List.copyOf(contributions), tick, special));
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
		Settled s = settle(pos, stateId);
		return s.verdict() == Verdict.REVERSE ? Optional.of(s.contributions()) : Optional.empty();
	}

	/** The verdict, with the contributions it concerns (empty for {@link Verdict#NONE}). */
	public record Settled(Verdict verdict, List<LocalCounter.Contribution> contributions) {}

	/** Like {@link #onSync} but also reports a special-world node that was kept. */
	public Settled settle(Pos pos, int stateId) {
		Pending p = pending.remove(pos);
		if (p == null || p.preStateId() != stateId) return new Settled(Verdict.NONE, List.of());
		return new Settled(p.special() ? Verdict.KEPT_NODE : Verdict.REVERSE, p.contributions());
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
