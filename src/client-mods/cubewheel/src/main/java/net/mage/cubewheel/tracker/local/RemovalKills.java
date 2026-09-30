package net.mage.cubewheel.tracker.local;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Kills that show up only as a removal: ManaCube's custom-model mobs (tigers in Tangleroot) are an
 * unnamed hitbox the server removes without a death event. A removal counts once when the local player
 * hit the entity within {@link #WINDOW_TICKS} and it was still close (the caller decides both), unless the
 * id was already settled by a death or a stack drop. The kill is named from a hint remembered at hit time
 * (own custom name, a linked or nearby name tag), else from the loot on the next action bar (see
 * {@link LootMatch}), else it stays unattributed. Everything is bounded. Pure: no Minecraft/Fabric imports.
 */
public final class RemovalKills {
	public static final int WINDOW_TICKS = 30;
	/** A loot message this recent is used at once: it and the removal can arrive in the same tick. */
	public static final long LOOKBACK_MS = 250;
	public static final long LOOT_WAIT_MS = 1500;
	public static final int MAX_HINTS = 256;
	public static final int MAX_SETTLED = 512;
	public static final int MAX_PENDING = 16;

	/** How a removal kill was named: a own custom name, b linked/nearby entity, c loot line, d none. */
	public enum Method {
		OWN('a'), LINKED('b'), LOOT('c'), NONE('d');

		public final char code;

		Method(char code) {
			this.code = code;
		}
	}

	/** A name for a hit entity, found at hit time; {@code source} explains it for capture. */
	public record Hint(String name, Method method, String source) {}

	/** A removal kill still to be named. */
	public record Removed(int entityId, String typeId, String name, WorldInfo world) {}

	/** A pending removal and the loot items that name it. */
	public record Resolved(Removed removed, List<String> loot) {}

	private record Pending(Removed removed, long at) {}

	private final Map<Integer, Optional<Hint>> hints = new LinkedHashMap<>();
	private final Set<Integer> settled = new LinkedHashSet<>();
	private final Deque<Pending> pending = new ArrayDeque<>();
	private List<String> lastLoot = List.of();
	private long lastLootAt = Long.MIN_VALUE;

	/** Remembers what was found for hit entity {@code id} ({@code hint} may be null: looked, found nothing). */
	public void remember(int id, Hint hint) {
		hints.remove(id);
		hints.put(id, Optional.ofNullable(hint));
		trim(hints.keySet(), MAX_HINTS);
	}

	/** Was entity {@code id} already looked at (so the nearby query runs once per entity)? */
	public boolean seen(int id) {
		return hints.containsKey(id);
	}

	public Optional<Hint> hint(int id) {
		Optional<Hint> h = hints.get(id);
		return h == null ? Optional.empty() : h;
	}

	/** {@code id} was counted (or refused) by a death or stack path: a later removal never counts. */
	public void settle(int id) {
		settled.remove(id);
		settled.add(id);
		trim(settled, MAX_SETTLED);
	}

	public boolean settled(int id) {
		return settled.contains(id);
	}

	/**
	 * Entity {@code id} was removed. True (once per id) when it counts as a kill: hit by the local player
	 * within {@link #WINDOW_TICKS} ({@code recentLocalHit}) and still close ({@code near}), not yet settled.
	 */
	public boolean claim(int id, boolean recentLocalHit, boolean near) {
		if (!recentLocalHit || !near || settled.contains(id)) return false;
		settle(id);
		return true;
	}

	/**
	 * A claimed removal without a name: the loot items if a loot message arrived within
	 * {@link #LOOKBACK_MS}, else empty and it waits up to {@link #LOOT_WAIT_MS} for the next one.
	 */
	public Optional<List<String>> awaitLoot(Removed r, long now) {
		if (!lastLoot.isEmpty() && now - lastLootAt <= LOOKBACK_MS) return Optional.of(lastLoot);
		pending.addLast(new Pending(r, now));
		while (pending.size() > MAX_PENDING) pending.removeFirst();
		return Optional.empty();
	}

	/** An action-bar message: if it holds loot, it names every pending removal (returned, no longer pending). */
	public List<Resolved> onActionBar(String text, long now) {
		List<String> loot = LootLine.items(text);
		if (loot.isEmpty()) return List.of();
		lastLoot = List.copyOf(loot);
		lastLootAt = now;
		List<Resolved> out = new ArrayList<>();
		for (Pending p : pending) {
			if (now - p.at() <= LOOT_WAIT_MS) out.add(new Resolved(p.removed(), lastLoot));
		}
		pending.clear();
		return out;
	}

	/** Removals that waited longer than {@link #LOOT_WAIT_MS} for loot (unattributed); no longer pending. */
	public List<Removed> expire(long now) {
		List<Removed> out = new ArrayList<>();
		for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
			Pending p = it.next();
			if (now - p.at() > LOOT_WAIT_MS) {
				out.add(p.removed());
				it.remove();
			}
		}
		return out;
	}

	public boolean awaitingLoot() {
		return !pending.isEmpty();
	}

	public void clear() {
		hints.clear();
		settled.clear();
		pending.clear();
		lastLoot = List.of();
		lastLootAt = Long.MIN_VALUE;
	}

	private static void trim(Set<Integer> keys, int max) {
		Iterator<Integer> it = keys.iterator();
		for (int n = keys.size(); n > max && it.hasNext(); n--) {
			it.next();
			it.remove();
		}
	}
}
