package net.mage.cubewheel.tracker.local;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;

/**
 * Nearby deaths no player's hit explains, kept briefly so a loot-only kill line can name its mob. An ability weapon
 * (the magic book) kills with damage that names no attacker, so its death has no hit ({@code no_hit}) and the only
 * proof it was ours is the loot line ManaCube shows the looter alone, about 70 ms after the death (capture
 * 2026-11-06: "Mooshroom 40⺛" died, then "+3 Mana | +5 Large Mushroom"). The loot ("Large Mushroom") does not name
 * the mob, so without the death "Slay 100 Mooshrooms" was never counted.
 * <p>
 * {@link #claim} takes the death nearest a line, from {@link #BEFORE_MS} before it to {@link #AFTER_MS} after it,
 * once. Everything is bounded. Pure: no Minecraft/Fabric imports.
 *
 * @param <T> what is known of the dead mob (its kill signal, taken when it died)
 */
public final class UnclaimedDeaths<T> {
	/** A death this long before a kill line can be that line's mob. */
	public static final long BEFORE_MS = 1500;
	/** A death this long after a kill line can be that line's mob. */
	public static final long AFTER_MS = 500;
	public static final int MAX = 16;

	private record Death<T>(T mob, long at) {}

	private final Deque<Death<T>> deaths = new ArrayDeque<>();

	public void died(T mob, long at) {
		if (mob == null) return;
		deaths.addLast(new Death<>(mob, at));
		while (deaths.size() > MAX) deaths.removeFirst();
	}

	/** The unclaimed death nearest {@code lineAt} inside the window, removed so it names only one line. */
	public Optional<T> claim(long lineAt) {
		Death<T> best = null;
		for (Death<T> d : deaths) {
			long before = lineAt - d.at();
			if (before > BEFORE_MS || -before > AFTER_MS) continue;
			if (best == null || Math.abs(before) < Math.abs(lineAt - best.at())) best = d;
		}
		if (best == null) return Optional.empty();
		deaths.remove(best);
		return Optional.of(best.mob());
	}

	/** Forgets deaths too old for any line still held (a line is decided {@link LootKills#AFTER_MS} after it). */
	public void expire(long now) {
		for (Iterator<Death<T>> it = deaths.iterator(); it.hasNext(); ) {
			if (now - it.next().at() > BEFORE_MS + LootKills.AFTER_MS + AFTER_MS) it.remove();
		}
	}

	public void clear() {
		deaths.clear();
	}
}
