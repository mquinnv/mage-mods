package net.mage.cubewheel.tracker.local;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;

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

	/** The mobs as last grouped. */
	public List<ModelMob> mobs() {
		return mobs;
	}

	/** Why {@link #matchSound} did or did not name a mob. */
	public enum SoundDecision {
		/** No mob within the window. */
		NO_CANDIDATE,
		/** Two mobs about equally close. */
		AMBIGUOUS,
		/** The name's first sound: it taught the voice and stays vanilla. */
		LEARNED_VOICE,
		/** The name's learned voice is another type. */
		OTHER_VOICE,
		/** Matched: rename it. */
		RENAMED
	}

	/**
	 * The decision for a sound, with the mob it is about ({@code mob}, the closest within the window, or for
	 * {@link SoundDecision#NO_CANDIDATE} the closest overall; null when there is none) and the sound's offset from
	 * that mob's position (NaN without a mob).
	 */
	public record SoundMatch(SoundDecision decision, ModelMob mob, double dx, double dy, double dz) {
		public boolean renamed() {
			return decision == SoundDecision.RENAMED;
		}
	}

	/** {@link #nearSound(String, double, double, double, IntFunction)} with the snapshot positions only. */
	public Optional<ModelMob> nearSound(String soundType, double x, double y, double z) {
		return nearSound(soundType, x, y, z, id -> null);
	}

	/**
	 * The mob a sound of entity type {@code soundType} ("minecraft:polar_bear") at x/y/z belongs to: the closest mob
	 * in the window, unless another is nearly as close (ambiguous), unless this is the name's first sound (taught,
	 * not matched), unless the name's learned voice is a different type. {@code livePosition} gives a hitbox's
	 * current x/y/z by its entity id (null when it is not loaded, then the once-a-second snapshot is used): a
	 * chasing mob moves several blocks between snapshots.
	 */
	public Optional<ModelMob> nearSound(String soundType, double x, double y, double z, IntFunction<double[]> livePosition) {
		SoundMatch m = matchSound(soundType, x, y, z, livePosition);
		return m.renamed() ? Optional.of(m.mob()) : Optional.empty();
	}

	/** As {@link #nearSound(String, double, double, double, IntFunction)}, saying why. */
	public SoundMatch matchSound(String soundType, double x, double y, double z, IntFunction<double[]> livePosition) {
		ModelMob best = null, nearest = null;
		double bestD = Double.MAX_VALUE, secondD = Double.MAX_VALUE, nearestD = Double.MAX_VALUE;
		double[] bestPos = null, nearestPos = null;
		for (ModelMob m : mobs) {
			double[] pos = livePosition == null ? null : livePosition.apply(m.hitbox());
			if (pos == null) pos = new double[] {m.x(), m.y(), m.z()};
			double dx = x - pos[0], dz = z - pos[2];
			double dy = y - pos[1];
			double d = Math.sqrt(dx * dx + dz * dz);
			double d3 = d * d + dy * dy;
			if (d3 < nearestD) {
				nearestD = d3;
				nearest = m;
				nearestPos = pos;
			}
			if (dy < BELOW || dy > ABOVE) continue;
			if (d > HORIZONTAL) continue;
			if (d < bestD) {
				secondD = bestD;
				bestD = d;
				best = m;
				bestPos = pos;
			} else if (d < secondD) {
				secondD = d;
			}
		}
		if (best == null) return match(SoundDecision.NO_CANDIDATE, nearest, nearestPos, x, y, z);
		if (secondD - bestD <= TIE) return match(SoundDecision.AMBIGUOUS, best, bestPos, x, y, z);
		String voice = voices.get(best.name());
		if (voice == null) {
			voices.put(best.name(), soundType);
			return match(SoundDecision.LEARNED_VOICE, best, bestPos, x, y, z);
		}
		return match(voice.equals(soundType) ? SoundDecision.RENAMED : SoundDecision.OTHER_VOICE, best, bestPos, x, y, z);
	}

	private static SoundMatch match(SoundDecision decision, ModelMob mob, double[] pos, double x, double y, double z) {
		if (mob == null) return new SoundMatch(decision, null, Double.NaN, Double.NaN, Double.NaN);
		return new SoundMatch(decision, mob, x - pos[0], y - pos[1], z - pos[2]);
	}
}
