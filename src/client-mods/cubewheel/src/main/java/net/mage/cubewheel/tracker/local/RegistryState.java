package net.mage.cubewheel.tracker.local;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The model mobs near the player, as last grouped ({@link ModelMobs#group}), plus the voice each mob name has been
 * heard with. Pure state: no Minecraft/Fabric imports; the adapter that fills it is {@code ModelMobRegistry}.
 *
 * <p>A model mob makes its sound from a vanilla entity type ("minecraft:polar_bear" for a Mana Wolf) at its hitbox's
 * position, so {@link #nearSound} matches a sound to the mob standing there. The first sound at a mob is not
 * trusted: it only teaches that name's voice, and stays vanilla. Later sounds match only that voice, so a sound
 * of some other kind near the mob (a player's wolf, an arrow) is not renamed.
 */
public final class RegistryState {
	/** A sound within this many blocks of a mob's x/z (horizontally) can be its own. */
	static final double HORIZONTAL = 1.0;
	/** A sound this far below / above a mob's y can be its own. */
	static final double BELOW = -0.5;
	static final double ABOVE = 1.5;
	/** Two candidates this close in horizontal distance are too close to call. */
	static final double TIE = 0.25;

	private List<ModelMob> mobs = List.of();
	private final Map<Integer, ModelMob> byPart = new HashMap<>();
	private final Map<String, String> voices = new HashMap<>();

	public void replace(List<ModelMob> next) {
		mobs = List.copyOf(next);
		byPart.clear();
		for (ModelMob m : mobs) {
			for (int id : m.parts()) byPart.put(id, m);
		}
	}

	/** Forgets the mobs (leaving a mana world); learned voices stay, so a warp does not make them relearn. */
	public void clear() {
		mobs = List.of();
		byPart.clear();
	}

	/** Forgets the mobs and the learned voices (disconnect). */
	public void clearAll() {
		clear();
		voices.clear();
	}

	public boolean isEmpty() {
		return mobs.isEmpty();
	}

	/** The mob the entity {@code id} is a part of (its hitbox, bones, tag or cloud), if any. */
	public Optional<ModelMob> byEntity(int id) {
		return Optional.ofNullable(byPart.get(id));
	}

	/**
	 * The mob a sound of entity type {@code soundType} ("minecraft:polar_bear") at x/y/z belongs to: the closest mob
	 * in the window, unless another is nearly as close (ambiguous), unless this is the name's first sound (taught,
	 * not matched), unless the name's learned voice is a different type.
	 */
	public Optional<ModelMob> nearSound(String soundType, double x, double y, double z) {
		ModelMob best = null;
		double bestD = Double.MAX_VALUE;
		double secondD = Double.MAX_VALUE;
		for (ModelMob m : mobs) {
			double dx = x - m.x(), dz = z - m.z();
			double dy = y - m.y();
			if (dy < BELOW || dy > ABOVE) continue;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > HORIZONTAL) continue;
			if (d < bestD) {
				secondD = bestD;
				bestD = d;
				best = m;
			} else if (d < secondD) {
				secondD = d;
			}
		}
		if (best == null || secondD - bestD <= TIE) return Optional.empty();
		String voice = voices.get(best.name());
		if (voice == null) {
			voices.put(best.name(), soundType);
			return Optional.empty();
		}
		return voice.equals(soundType) ? Optional.of(best) : Optional.empty();
	}
}
