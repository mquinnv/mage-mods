package net.mage.cubewheel.tracker.local;

import net.mage.cubewheel.tracker.Trackable;
import net.mage.cubewheel.tracker.TrackerStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies one signal to every matching active rule of the store. Pure: no Minecraft/Fabric imports. */
public final class LocalCounter {
	/** A kill never counts more than this many units (a stacked mob dying). */
	public static final int MAX_UNITS_PER_SIGNAL = 64;

	/** Objective units added to one entry by one signal, so a rejection can take exactly them back. */
	public record Contribution(String id, long units) {}

	private LocalCounter() {}

	/**
	 * Adds the signal to every matching entry; returns what was added (empty if nothing matched). Named rules
	 * ("Rattle Snakes") are credited before group/any ones ("Monsters"), each in the store's entry order, so the
	 * progress popup lists the most specific entry first, the same way every time.
	 */
	public static List<Contribution> onSignal(Signal signal, TrackerStore store, Collection<String> worldTokens, long now) {
		return onSignal(signal, store, worldTokens, now, Set.of());
	}

	private static List<Contribution> onSignal(Signal signal, TrackerStore store, Collection<String> worldTokens, long now,
			Set<String> exclude) {
		if (signal == null || store == null) return List.of();
		long units = units(signal);
		if (units <= 0) return List.of();
		List<Map.Entry<String, CounterRule>> rules = new ArrayList<>(store.activeRules(worldTokens).entrySet());
		rules.sort(Comparator.comparing(e -> !(e.getValue().what() instanceof CounterRule.Named))); // stable
		List<Contribution> out = new ArrayList<>();
		for (Map.Entry<String, CounterRule> e : rules) {
			if (exclude.contains(e.getKey())) continue;
			if (RuleMatcher.matches(e.getValue(), signal) && store.addEstimate(e.getKey(), units, now)) {
				out.add(new Contribution(e.getKey(), units));
			}
		}
		return out;
	}

	/**
	 * A monster kill nothing names (a custom-model mob's hitbox removed with no name tag and no loot line, see
	 * {@link ModelHitbox}): one unit to every group/any kill rule ("Slay Monsters in Sandara", "Slay 1,000 Sandara
	 * Monsters"), never a named one, skipping {@code exclude}d ids. Returns what was added.
	 */
	public static List<Contribution> genericKill(WorldInfo w, TrackerStore store, Collection<String> worldTokens, long now,
			Set<String> exclude) {
		Signal.MobKilled unnamed = new Signal.MobKilled("", "", GENERIC_KILL_GROUPS, 1, w);
		return onSignal(unnamed, store, worldTokens, now, exclude == null ? Set.of() : exclude);
	}

	/**
	 * A kill named from its loot ({@link LootMatch}): one unit to each of {@code namedIds}, then, when it was a
	 * custom-model mob's hitbox ({@code generic}), one to each generic kill rule too ({@link #genericKill}). Each entry
	 * counts once. Returns what was added, named entries first.
	 */
	public static List<Contribution> lootKill(List<String> namedIds, boolean generic, WorldInfo w, TrackerStore store,
			Collection<String> worldTokens, long now) {
		List<Contribution> out = new ArrayList<>(credit(namedIds, store, now));
		if (generic && store != null) {
			out.addAll(genericKill(w, store, worldTokens, now, namedIds == null ? Set.of() : Set.copyOf(namedIds)));
		}
		return out;
	}

	/** A custom-model mob counts as a monster ("Slay Monsters" objectives). */
	private static final Set<String> GENERIC_KILL_GROUPS = Set.of("mob", "monster");

	/** One unit to each of {@code ids} (a kill named from its loot, see {@link LootMatch}); returns what was added. */
	public static List<Contribution> credit(Collection<String> ids, TrackerStore store, long now) {
		if (ids == null || store == null) return List.of();
		List<Contribution> out = new ArrayList<>();
		for (String id : ids) {
			if (store.addEstimate(id, 1, now)) out.add(new Contribution(id, 1));
		}
		return out;
	}

	/**
	 * You finished {@code quest} (see {@link QuestCompleted}): every incomplete entry's "Complete the …"
	 * objective is estimated done until the next menu read. On a multi-objective entry that objective gets one
	 * unit under its {@link TrackerStore#subKey} (its HUD row shows "~1/1"); a single-objective entry gets the
	 * units left to its max, so it shows at its target ("✓?"). Objectives the menu already shows done, or already
	 * estimated done, are left alone. Returns what was added.
	 */
	public static List<Contribution> questCompleted(String quest, TrackerStore store, long now) {
		if (quest == null || store == null) return List.of();
		List<Contribution> out = new ArrayList<>();
		for (Trackable t : store.all()) {
			if (t.complete()) continue;
			ObjectiveInfo info = store.objective(t.id()).orElse(null);
			if (info == null || info.handIn() || info.subs().isEmpty()) continue;
			if (info.subs().size() > 1) {
				for (int i = 0; i < info.subs().size(); i++) {
					ObjectiveInfo.Sub sub = info.subs().get(i);
					if (sub.percent() != null && sub.percent() >= 100) continue;
					String key = TrackerStore.subKey(t.id(), i);
					if (!QuestCompleted.matches(sub.text(), quest) || store.counted(key) > 0) continue;
					if (store.addEstimate(key, 1, now)) out.add(new Contribution(key, 1));
				}
				continue;
			}
			if (!QuestCompleted.matches(info.subs().get(0).text(), quest)) continue;
			long left = (long) Math.ceil(t.max() - t.current()) - store.counted(t.id());
			if (left > 0 && store.addEstimate(t.id(), left, now)) out.add(new Contribution(t.id(), left));
		}
		return out;
	}

	public static void reverse(List<Contribution> contributions, TrackerStore store) {
		if (contributions == null || store == null) return;
		for (Contribution c : contributions) store.reverseEstimate(c.id(), c.units());
	}

	private static long units(Signal signal) {
		if (signal instanceof Signal.MobKilled m) return Math.min(MAX_UNITS_PER_SIGNAL, Math.max(0, m.count()));
		return 1;
	}
}
