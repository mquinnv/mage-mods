package net.mage.cubewheel.cooldown;

import net.mage.cubewheel.cooldown.ItemAbilities.Ability;
import net.mage.cubewheel.cooldown.ItemAbilities.Action;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Item ability countdowns, keyed by item name and ability. Pure: no Minecraft/Fabric imports.
 */
public final class CooldownTracker {
	/** A running countdown: "Samurai Katana" or, for items with several abilities, "Axe (Shift + Right Click)". */
	public record Entry(String label, long endsAt) {}

	private final Map<String, Entry> running = new LinkedHashMap<>();

	/**
	 * The player did {@code action} with the item {@code name} whose lore gave {@code abilities}. While sneaking
	 * a sneak-only ability of that action wins; otherwise (or if there is none) the plain one applies. A
	 * countdown that is still running is not restarted (the server ignores uses during a cooldown). Returns
	 * true if a countdown started.
	 */
	public boolean trigger(String name, List<Ability> abilities, Action action, boolean sneaking, long now) {
		if (name == null || name.isBlank() || abilities == null || abilities.isEmpty()) return false;
		Ability chosen = null;
		for (Ability a : abilities) {
			if (a.action() != action) continue;
			if (a.sneak() && sneaking) {
				chosen = a;
				break;
			}
			if (!a.sneak() && chosen == null) chosen = a;
		}
		if (chosen == null) return false;
		running.values().removeIf(e -> e.endsAt() <= now);
		String key = name + "|" + chosen.action() + "|" + chosen.sneak() + "|" + chosen.section();
		if (running.containsKey(key)) return false;
		boolean several = abilities.size() > 1 && !chosen.section().isEmpty();
		running.put(key, new Entry(several ? name + " (" + chosen.section() + ")" : name, now + chosen.cooldownMs()));
		return true;
	}

	/** Running countdowns, soonest-ending first. */
	public List<Entry> active(long now) {
		List<Entry> out = new ArrayList<>();
		for (Entry e : running.values()) {
			if (e.endsAt() > now) out.add(e);
		}
		out.sort(Comparator.comparingLong(Entry::endsAt).thenComparing(Entry::label));
		return out;
	}

	/** Forgets everything (leaving the server). */
	public void clear() {
		running.clear();
	}

	/**
	 * Tells when the player finished eating or drinking: fed each tick with whether a food/drink is being used,
	 * its remaining use ticks and the item; returns the item on the tick after a use that ran down to 0-1
	 * remaining ticks stopped (a use let go early returns null).
	 */
	public static final class ConsumeDetector<T> {
		private T tracked;
		private int lastRemaining;

		public T tick(boolean usingConsumable, int remaining, T item) {
			if (usingConsumable) {
				tracked = item;
				lastRemaining = remaining;
				return null;
			}
			T done = tracked != null && lastRemaining <= 1 ? tracked : null;
			tracked = null;
			return done;
		}
	}

	/** Rising-edge detector (e.g. starting to sneak). */
	public static final class Edge {
		private boolean last;

		public boolean rose(boolean now) {
			boolean r = now && !last;
			last = now;
			return r;
		}
	}
}
