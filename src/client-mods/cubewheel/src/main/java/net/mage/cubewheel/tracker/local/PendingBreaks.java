package net.mage.cubewheel.tracker.local;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Own breaks counted immediately but not yet confirmed by the server. When the client's block
 * prediction is settled, the server's state for that position arrives; if it equals the state before
 * the break, the server put the block back and the contributions are taken back: a refusal (claim,
 * anti-cheat), or in a mana world a "fake-break", which is what every block there does under the pickaxe
 * except a resource node on its last hit (Michael 2026-10-08; capture: 189 chorus flowers counted, 20
 * restored, 150 of 170 credited by the server). Icehaven's ice crystals were never restored (capture
 * 2026-10-04: 10 breaks, 10 kept), so nothing a mana world credits is lost. Pure: no Minecraft/Fabric
 * imports.
 */
public final class PendingBreaks {
	public static final int MAX = 64;
	public static final int EXPIRE_TICKS = 100; // 5 s: generous for lag, still short-lived

	/** What the server's state for a position means for the pending break. */
	public enum Verdict {
		/** Nothing to undo: not a restore (air, replant) or nothing pending. */
		NONE,
		/** Restored the previous state: a refusal or a fake-break, take the contributions back. */
		REVERSE
	}

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
		Settled s = settle(pos, stateId);
		return s.verdict() == Verdict.REVERSE ? Optional.of(s.contributions()) : Optional.empty();
	}

	/** The verdict, with the contributions it concerns (empty for {@link Verdict#NONE}). */
	public record Settled(Verdict verdict, List<LocalCounter.Contribution> contributions) {}

	/** Like {@link #onSync}, as a verdict with the contributions it concerns. */
	public Settled settle(Pos pos, int stateId) {
		Pending p = pending.remove(pos);
		if (p == null || p.preStateId() != stateId) return new Settled(Verdict.NONE, List.of());
		return new Settled(Verdict.REVERSE, p.contributions());
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
