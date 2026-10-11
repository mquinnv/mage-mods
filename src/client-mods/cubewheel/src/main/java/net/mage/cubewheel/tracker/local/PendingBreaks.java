package net.mage.cubewheel.tracker.local;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Own breaks counted immediately but not yet confirmed by the server. When the client's block
 * prediction is settled, the server's state for that position arrives; if it equals the state before
 * the break, the server put the block back. That is a refusal (claim, anti-cheat), or in a mana world a
 * "fake-break", which is what every block there does under the pickaxe (capture 2026-10-08: 98 blocks of
 * amethyst counted, 0 credited), and the contributions are taken back. Except a resource node a job names:
 * Morend's amethyst clusters are put straight back by the server too and credited all the same (capture
 * 2026-10-11: 29 clusters hit between two reads of the job, every one restored, 21 credited; Michael:
 * "amethyst cluster dynamic tracking still not working"). A block a rule names ("Mine 245 Morend Amethyst
 * Cluster") is kept when restored; a block only "resources" rules matched is not. Chorus flowers and ice
 * crystals mostly end as air and were never in question. Pure: no Minecraft/Fabric imports.
 */
public final class PendingBreaks {
	public static final int MAX = 64;
	public static final int EXPIRE_TICKS = 100; // 5 s: generous for lag, still short-lived

	/** What the server's state for a position means for the pending break. */
	public enum Verdict {
		/** Nothing to undo: not a restore (air, replant) or nothing pending. */
		NONE,
		/** Restored the previous state: a refusal or a fake-break, take the contributions back. */
		REVERSE,
		/** Restored the previous state, but a job names the block: a resource node the server regrows, kept. */
		KEPT_NODE
	}

	private record Pending(int preStateId, List<LocalCounter.Contribution> contributions, long tick, boolean node) {}

	private final Map<Pos, Pending> pending = new LinkedHashMap<>();

	public void record(Pos pos, int preStateId, List<LocalCounter.Contribution> contributions, long tick) {
		record(pos, preStateId, contributions, tick, false);
	}

	/** {@code node}: a mana-world block one of the credited rules names (see {@link #namedResource}); kept if restored. */
	public void record(Pos pos, int preStateId, List<LocalCounter.Contribution> contributions, long tick, boolean node) {
		if (pos == null || contributions == null || contributions.isEmpty()) return;
		pending.remove(pos);
		pending.put(pos, new Pending(preStateId, List.copyOf(contributions), tick, node));
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
		return new Settled(p.node() ? Verdict.KEPT_NODE : Verdict.REVERSE, p.contributions());
	}

	/**
	 * True when one of the rules {@code added} credited names its target ("Mine 245 Morend Amethyst Cluster"
	 * rather than "Mine resources in Morend"): the job itself says the block is a resource of the world.
	 */
	public static boolean namedResource(List<LocalCounter.Contribution> added, Map<String, CounterRule> rules) {
		if (added == null || rules == null) return false;
		for (LocalCounter.Contribution c : added) {
			CounterRule r = rules.get(c.id());
			if (r != null && r.what() instanceof CounterRule.Named) return true;
		}
		return false;
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
