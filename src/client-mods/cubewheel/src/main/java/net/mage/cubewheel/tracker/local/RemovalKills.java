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
 * (own custom name, a linked or nearby name tag), else from a loot action bar (see {@link LootMatch}), else
 * from a fallback remembered at hit time (a Firefly Bottle used on it), else it stays unattributed. The loot
 * line often arrives before the removal (fireflies: ~0.5-1 s), so a line counts from the first local hit on
 * the entity (at most {@link #LOOT_BEFORE_MS} before the removal) until {@link #LOOT_WAIT_MS} after it; a
 * line seen before the removal names one removal only. Everything is bounded. Pure: no Minecraft/Fabric imports.
 */
public final class RemovalKills {
	public static final int WINDOW_TICKS = 30;
	/** A loot message at most this old at the removal (and not older than the first hit) is used at once. */
	public static final long LOOT_BEFORE_MS = 3000;
	public static final long LOOT_WAIT_MS = 1500;
	public static final int MAX_LOOT_LINES = 16;
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

	private static final class Seen {
		final List<String> items;
		final long at;
		boolean used;

		Seen(List<String> items, long at) {
			this.items = items;
			this.at = at;
		}
	}

	private final Map<Integer, Optional<Hint>> hints = new LinkedHashMap<>();
	private final Set<Integer> settled = new LinkedHashSet<>();
	private final Deque<Pending> pending = new ArrayDeque<>();
	private final Deque<Seen> lootLines = new ArrayDeque<>();
	private final Map<Integer, Long> firstHit = new LinkedHashMap<>();
	private final Map<Integer, String> fallbacks = new LinkedHashMap<>();

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

	/**
	 * The local player hit (or used an item on) entity {@code id} at {@code at} ms. The first hit is kept:
	 * hitting again after the loot appeared must not hide that loot.
	 */
	public void hit(int id, long at) {
		if (firstHit.putIfAbsent(id, at) != null) return;
		trim(firstHit.keySet(), MAX_HINTS);
	}

	/** Loot to assume for {@code id} when no loot line names it (a Firefly Bottle used on it: "Firefly"). */
	public void fallback(int id, String lootItem) {
		if (lootItem == null) return;
		fallbacks.remove(id);
		fallbacks.put(id, lootItem);
		trim(fallbacks.keySet(), MAX_HINTS);
	}

	public Optional<String> fallback(int id) {
		return Optional.ofNullable(fallbacks.get(id));
	}

	/** "Firefly" for a held "Bottomless Firefly Bottle" or "Firefly Bottle" (any decoration), else null. */
	public static String bottleLoot(String heldItemName) {
		if (heldItemName == null) return null;
		String s = heldItemName.replaceAll("§.", "").toLowerCase(java.util.Locale.ROOT);
		return s.contains("firefly bottle") ? "Firefly" : null;
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
	 * A claimed removal without a name: the items of the latest unused loot message seen since the first
	 * local hit on it and at most {@link #LOOT_BEFORE_MS} ago (that message is then used up), else empty
	 * and it waits up to {@link #LOOT_WAIT_MS} for the next one.
	 */
	public Optional<List<String>> awaitLoot(Removed r, long now) {
		Long hitAt = firstHit.get(r.entityId());
		long from = Math.max(now - LOOT_BEFORE_MS, hitAt == null ? Long.MIN_VALUE : hitAt);
		for (Iterator<Seen> it = lootLines.descendingIterator(); it.hasNext(); ) {
			Seen seen = it.next();
			if (seen.at < from) break;
			if (seen.used || seen.at > now) continue;
			seen.used = true;
			return Optional.of(seen.items);
		}
		pending.addLast(new Pending(r, now));
		while (pending.size() > MAX_PENDING) pending.removeFirst();
		return Optional.empty();
	}

	/** An action-bar message: if it holds loot, it names every pending removal (returned, no longer pending). */
	public List<Resolved> onActionBar(String text, long now) {
		List<String> loot = LootLine.items(text);
		if (loot.isEmpty()) return List.of();
		Seen seen = new Seen(List.copyOf(loot), now);
		lootLines.addLast(seen);
		while (lootLines.size() > MAX_LOOT_LINES) lootLines.removeFirst();
		List<Resolved> out = new ArrayList<>();
		for (Pending p : pending) {
			if (now - p.at() <= LOOT_WAIT_MS) out.add(new Resolved(p.removed(), seen.items));
		}
		pending.clear();
		if (!out.isEmpty()) seen.used = true;
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
		lootLines.clear();
		firstHit.clear();
		fallbacks.clear();
	}

	private static void trim(Set<Integer> keys, int max) {
		Iterator<Integer> it = keys.iterator();
		for (int n = keys.size(); n > max && it.hasNext(); n--) {
			it.next();
			it.remove();
		}
	}
}
