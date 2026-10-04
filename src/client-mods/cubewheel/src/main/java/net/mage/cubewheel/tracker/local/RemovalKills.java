package net.mage.cubewheel.tracker.local;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Kills that show up only as a removal: ManaCube's custom-model mobs (tigers in Tangleroot) are an
 * unnamed hitbox the server removes without a death event. A removal counts once when the local player
 * hit the entity within {@link #WINDOW_TICKS} and it was still close (the caller decides both), unless the
 * id was already settled by a death, a stack drop or a sibling hitbox of the same model ({@link #siblings}).
 * The kill is named from a hint remembered at hit time (own custom name, a linked or nearby name tag), else
 * from a loot action bar (see {@link LootMatch}), else from a fallback remembered at hit time (a Firefly
 * Bottle used on it), else it stays unnamed (a model hitbox then still counts as a monster). The loot
 * line often arrives before the removal (fireflies: ~0.5-1 s), so a line counts from the first local hit on
 * the entity (at most {@link #LOOT_BEFORE_MS} before the removal) until {@link #LOOT_WAIT_MS} after it; a
 * line seen before the removal names one removal only. Everything is bounded. Pure: no Minecraft/Fabric imports.
 */
public final class RemovalKills {
	public static final int WINDOW_TICKS = 30;
	/**
	 * A model hitbox (Sandara rattlesnakes: an interaction riding a cloud, and invisible slimes) is removed 2.0-4.6 s
	 * after the last hit on it (capture 2026-10-01, 4 of 19 past 4 s), so its window is longer. Hits on a sibling
	 * hitbox of the same model also refresh it (see {@link #siblings}).
	 */
	public static final int HITBOX_WINDOW_TICKS = 120;
	/** A loot message at most this old at the removal (and not older than the first hit) is used at once. */
	public static final long LOOT_BEFORE_MS = 5000;
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

	/**
	 * A name for a hit entity, found at hit time; {@code source} explains it for capture, {@code sourceId} is the
	 * entity that carried it (a name tag; -1 for the entity's own name or unknown).
	 */
	public record Hint(String name, Method method, String source, int sourceId) {
		public Hint(String name, Method method, String source) {
			this(name, method, source, -1);
		}
	}

	/**
	 * A removal kill still to be named; {@code modelHitbox}: a custom-model mob's hitbox (see {@link ModelHitbox}), so
	 * it counts as a monster kill even if nothing names it.
	 */
	public record Removed(int entityId, String typeId, String name, WorldInfo world, boolean modelHitbox) {
		public Removed(int entityId, String typeId, String name, WorldInfo world) {
			this(entityId, typeId, name, world, false);
		}
	}

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
	private final Map<Integer, Set<Integer>> models = new LinkedHashMap<>();
	private final Set<Integer> attacked = new LinkedHashSet<>();

	/** Remembers what was found for hit entity {@code id} ({@code hint} may be null: looked, found nothing). */
	public void remember(int id, Hint hint) {
		hints.remove(id);
		hints.put(id, Optional.ofNullable(hint));
		trim(hints.keySet(), MAX_HINTS);
	}

	/**
	 * Remembers which model hit entity {@code id} belongs to: ids of entities only that model has (its own cloud
	 * vehicle, the cloud carrying the model's bones). They tell its sibling hitboxes.
	 */
	public void model(int id, Collection<Integer> keys) {
		models.remove(id);
		Set<Integer> k = new LinkedHashSet<>();
		if (keys != null) for (Integer key : keys) if (key != null) k.add(key);
		if (!k.isEmpty()) models.put(id, Set.copyOf(k));
		trim(models.keySet(), MAX_HINTS);
	}

	/**
	 * Other remembered hit entities of the same model as {@code id}: sharing a model key, and not named by two
	 * different tags. A shared name tag alone never links them: the nearest tag within 3 blocks can be a
	 * neighbour's (two Mana Wolves side by side), and a slime can pick up a neighbouring model's bone cloud, which
	 * its own tag then tells apart. One model counts once; a hit on any of them keeps the others' hit recent.
	 */
	public Set<Integer> siblings(int id) {
		Set<Integer> mine = models.getOrDefault(id, Set.of());
		if (mine.isEmpty()) return Set.of();
		int myTag = tag(id);
		Set<Integer> out = new LinkedHashSet<>();
		for (Map.Entry<Integer, Set<Integer>> e : models.entrySet()) {
			int other = e.getKey();
			if (other == id || Collections.disjoint(mine, e.getValue())) continue;
			int otherTag = tag(other);
			if (myTag >= 0 && otherTag >= 0 && myTag != otherTag) continue;
			out.add(other);
		}
		return out;
	}

	/** The id of the name tag that named hit entity {@code id}, or -1. */
	private int tag(int id) {
		return hint(id).map(Hint::sourceId).orElse(-1);
	}

	/**
	 * A tag found by looking again when {@code id} was removed names it only if the tag itself is tied to this
	 * hitbox's model: {@code tagTies} (the tag's id, the entity it rides, and the hitbox when one rides the other)
	 * meets the hitbox or its model keys from hit time (it rides the hitbox, its cloud or the bone cloud). The
	 * nearest tag at removal may be a neighbour's (the mob's own tag can go in the same packet) while this model's
	 * clouds are still there, so nearness proves nothing. A tag already counted is never used again. Otherwise the
	 * kill stays unnamed (generic for a model hitbox).
	 */
	public boolean acceptReprobe(int id, Hint hint, Collection<Integer> tagTies) {
		if (hint == null) return false;
		if (hint.method() == Method.OWN) return true; // its own custom name: nobody else's
		if (tagTies == null) return false;
		if (hint.sourceId() >= 0 && settled.contains(hint.sourceId())) return false;
		Set<Integer> mine = new LinkedHashSet<>(models.getOrDefault(id, Set.of()));
		mine.add(id);
		return !Collections.disjoint(mine, tagTies);
	}

	/**
	 * Does name {@code hint} count removed entity {@code id} as a kill? Its own name always does; a name from another
	 * entity (a tag) only if the player attacked it, or used a Firefly Bottle on it: right-clicking a ModelEngine
	 * NPC, mount or crate by its name tag, then seeing it go, is no kill.
	 */
	public boolean nameCounts(int id, Hint hint) {
		return hint != null && (hint.method() == Method.OWN || attacked(id) || fallback(id).isPresent());
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
	 * The local player attacked (or damaged) entity {@code id} at {@code at} ms. The first hit is kept:
	 * hitting again after the loot appeared must not hide that loot.
	 */
	public void hit(int id, long at) {
		used(id, at);
		attacked.remove(id);
		attacked.add(id);
		trim(attacked, MAX_HINTS);
	}

	/**
	 * The local player used an item on entity {@code id} at {@code at} ms (right-click): it opens the loot window
	 * like a hit, but is no attack, so its removal is never a monster kill by itself (see {@link #generic}).
	 */
	public void used(int id, long at) {
		if (firstHit.putIfAbsent(id, at) != null) return;
		trim(firstHit.keySet(), MAX_HINTS);
	}

	/** Did the local player attack entity {@code id} (not just use an item on it)? */
	public boolean attacked(int id) {
		return attacked.contains(id);
	}

	/**
	 * Does an unnamed removal of {@code id} count as a monster kill? Only a model hitbox ({@link ModelHitbox}) the
	 * player attacked: right-clicking a ModelEngine NPC, mount or crate and seeing it go is no kill, nor is a Firefly
	 * Bottle used on it (a catch).
	 */
	public boolean generic(int id, boolean modelHitbox) {
		return modelHitbox && attacked(id) && fallback(id).isEmpty();
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
	 * {@link #claim(int, boolean, boolean)}, and when it counts, its model counts with it ({@link #settleModel}):
	 * siblings the player hit within the hitbox window ({@code inWindow}) never count again.
	 */
	public boolean claim(int id, boolean recentLocalHit, boolean near, IntPredicate inWindow) {
		if (!claim(id, recentLocalHit, near)) return false;
		settleModel(id, inWindow);
		return true;
	}

	/**
	 * The model of claimed entity {@code id} was counted: its sibling hitboxes last hit within the window
	 * ({@code inWindow}) and its name tag never count again (a Viper's slime and its interaction were both claimed
	 * with the same tag, 2026-10-01). Call again after the hint changed (a re-probe on removal).
	 */
	public void settleModel(int id, IntPredicate inWindow) {
		for (int sibling : siblings(id)) if (inWindow == null || inWindow.test(sibling)) settle(sibling);
		hint(id).filter(h -> h.sourceId() >= 0).ifPresent(h -> settle(h.sourceId()));
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

	/** Is the claimed removal of entity {@code id} still waiting for a loot line? */
	public boolean awaitingLoot(int id) {
		for (Pending p : pending) {
			if (p.removed().entityId() == id) return true;
		}
		return false;
	}

	public void clear() {
		hints.clear();
		models.clear();
		attacked.clear();
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
