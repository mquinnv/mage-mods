package com.mage.cubewheel.tracker.local;

import com.mage.cubewheel.tracker.TrackerStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Applies one signal to every matching active rule of the store. Pure: no Minecraft/Fabric imports. */
public final class LocalCounter {
	/** A kill never counts more than this many units (a stacked mob dying). */
	public static final int MAX_UNITS_PER_SIGNAL = 64;

	/** Objective units added to one entry by one signal, so a rejection can take exactly them back. */
	public record Contribution(String id, long units) {}

	private LocalCounter() {}

	/** Adds the signal to every matching entry; returns what was added (empty if nothing matched). */
	public static List<Contribution> onSignal(Signal signal, TrackerStore store, Collection<String> worldTokens, long now) {
		if (signal == null || store == null) return List.of();
		long units = units(signal);
		if (units <= 0) return List.of();
		List<Contribution> out = new ArrayList<>();
		for (Map.Entry<String, CounterRule> e : store.activeRules(worldTokens).entrySet()) {
			if (RuleMatcher.matches(e.getValue(), signal) && store.addEstimate(e.getKey(), units, now)) {
				out.add(new Contribution(e.getKey(), units));
			}
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
