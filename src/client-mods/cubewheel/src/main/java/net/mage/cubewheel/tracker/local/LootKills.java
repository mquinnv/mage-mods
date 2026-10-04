package net.mage.cubewheel.tracker.local;

import net.mage.cubewheel.tracker.TrackerStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Kills seen only as their loot line: an ability weapon (Phoenix Staff, magic book) deals its damage from the server's
 * ability code, so the client sees no attack and no death or removal it can call ours, only the action-bar loot line
 * ("+7 Mana | +1 Moose Skin") that ManaCube shows the looter alone (capture 2026-10-04).
 * <p>
 * Reading the line: ManaCube's action bar is a running list of parts (loot entries, the tool's status, mining mana),
 * re-sent whenever one changes, each part shown for about 3 s (the same "+3 Mana | +6 Quarry Coal" seen again 2.9 s
 * later, 2026-10-01). So a loot entry ("+2 Moose Skin", amount and item) seen again within {@link #LINGER_MS} of its
 * first sighting is the same entry, and a part list growing ("... | +4 Mana | +1 Moose Skin") adds only what is new.
 * A message is a kill line when it brings a new drop (any entry other than Mana) and holds a Mana entry: "+2 Mana"
 * alone is mining, and a drop with no mana ("+1 Sad Firefly") a catch.
 * <p>
 * Not counting twice: every kill counted locally (death, stack, removal) is reported with {@link #counted}. A kill line
 * is held for {@link #GRACE_MS}; when its window ends it is dropped if a reported kill not yet paired lies within
 * {@link #GRACE_MS} of it on either side (the line can come 1.2 s before the removal, or 25 ms after it), else it is
 * returned by {@link #expire} as a kill of its own. Two genuine kills with identical entries inside the linger merge
 * into one (an undercount; rare), and a local kill counted more than {@link #GRACE_MS} after its line counts twice.
 * Everything is bounded. Pure: no Minecraft/Fabric imports.
 */
public final class LootKills {
	/** How long a kill line waits for a locally counted kill to claim it (and how far apart they may be). */
	public static final long GRACE_MS = 2000;
	/** A loot entry seen again this long after its first sighting is the same entry, shown again. */
	public static final long LINGER_MS = 3500;
	public static final int MAX_LINES = 32;
	public static final int MAX_KILLS = 32;
	public static final int MAX_ENTRIES = 64;
	private static final String MANA = "Mana";

	/** A kill known only from its loot line: the drops ({@code loot}, for {@link LootMatch}), where and when it was seen. */
	public record Kill(List<String> loot, WorldInfo world, long at) {}

	/** What {@link #credit} added, and the kill target the drops named (empty: a generic monster kill). */
	public record Credited(Optional<String> target, List<LocalCounter.Contribution> added) {}

	private static final class Seen {
		final long first;
		int count;

		Seen(long first, int count) {
			this.first = first;
			this.count = count;
		}
	}

	private static final class Counted {
		final long at;
		boolean used;

		Counted(long at) {
			this.at = at;
		}
	}

	private final Map<String, Seen> entries = new LinkedHashMap<>();
	private final Deque<Kill> lines = new ArrayDeque<>();
	private final Deque<Counted> kills = new ArrayDeque<>();

	/**
	 * An action-bar message at {@code now} in {@code world}. True when it is a new kill line (now held for
	 * {@link #GRACE_MS}).
	 */
	public boolean onActionBar(String text, WorldInfo world, long now) {
		List<LootLine.Entry> found = LootLine.entries(text);
		if (found.isEmpty()) return false;
		entries.values().removeIf(s -> now - s.first > LINGER_MS || now < s.first);
		Map<LootLine.Entry, Integer> here = new LinkedHashMap<>();
		boolean mana = false;
		for (LootLine.Entry e : found) {
			here.merge(e, 1, Integer::sum);
			mana |= MANA.equalsIgnoreCase(e.item());
		}
		List<String> drops = new ArrayList<>();
		for (Map.Entry<LootLine.Entry, Integer> e : here.entrySet()) {
			String key = e.getKey().amount() + " " + e.getKey().item();
			int n = e.getValue();
			Seen seen = entries.get(key);
			int added;
			if (seen == null) {
				added = n;
				entries.put(key, new Seen(now, n));
			} else {
				added = Math.max(0, n - seen.count);
				seen.count = Math.max(seen.count, n);
			}
			if (!MANA.equalsIgnoreCase(e.getKey().item())) for (int i = 0; i < added; i++) drops.add(e.getKey().item());
		}
		trim(entries, MAX_ENTRIES);
		if (drops.isEmpty() || !mana) return false;
		lines.addLast(new Kill(List.copyOf(drops), world, now));
		while (lines.size() > MAX_LINES) lines.removeFirst();
		return true;
	}

	/** A kill was counted locally at {@code now} (any path): it claims one kill line within {@link #GRACE_MS}. */
	public void counted(long now) {
		kills.addLast(new Counted(now));
		while (kills.size() > MAX_KILLS) kills.removeFirst();
	}

	/**
	 * Kill lines whose grace window ended at {@code now}: each is paired with the nearest unpaired local kill within
	 * {@link #GRACE_MS} of it (dropped), else returned as a kill of its own. Oldest first.
	 */
	public List<Kill> expire(long now) {
		List<Kill> out = new ArrayList<>();
		for (Iterator<Kill> it = lines.iterator(); it.hasNext(); ) {
			Kill line = it.next();
			if (now - line.at() < GRACE_MS) break; // lines are in arrival order
			it.remove();
			Counted best = null;
			for (Counted k : kills) {
				long d = Math.abs(k.at - line.at());
				if (k.used || d > GRACE_MS) continue;
				if (best == null || d < Math.abs(best.at - line.at())) best = k;
			}
			if (best != null) best.used = true;
			else out.add(line);
		}
		// A kill can still claim a line up to GRACE_MS after it, which is decided GRACE_MS after that line.
		kills.removeIf(k -> now - k.at > 2 * GRACE_MS);
		return out;
	}

	public void clear() {
		entries.clear();
		lines.clear();
		kills.clear();
	}

	/**
	 * Credits a loot-only kill like a removal named by its loot ({@link LootMatch}, method c): one unit to each rule the
	 * drops name, plus one to each generic monster rule ({@link LocalCounter#genericKill}, the named ones excluded).
	 * Drops that name no rule still make it one generic monster kill: the line proves a kill.
	 */
	public static Credited credit(Kill kill, TrackerStore store, Collection<String> worldTokens, long now) {
		if (kill == null || store == null) return new Credited(Optional.empty(), List.of());
		Optional<LootMatch.Credit> named = LootMatch.match(kill.loot(), store.activeRules(worldTokens), kill.world());
		List<String> ids = named.map(LootMatch.Credit::ruleIds).orElse(List.of());
		List<LocalCounter.Contribution> added = LocalCounter.lootKill(ids, true, kill.world(), store, worldTokens, now);
		return new Credited(named.map(LootMatch.Credit::target), added);
	}

	private static void trim(Map<String, Seen> map, int max) {
		Iterator<String> it = map.keySet().iterator();
		for (int n = map.size(); n > max && it.hasNext(); n--) {
			it.next();
			it.remove();
		}
	}
}
