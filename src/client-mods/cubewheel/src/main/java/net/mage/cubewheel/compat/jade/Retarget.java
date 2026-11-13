package net.mage.cubewheel.compat.jade;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.IntFunction;
import net.mage.cubewheel.tracker.local.ModelBounds;
import net.mage.cubewheel.tracker.local.ModelMob;

/**
 * Where Jade's tooltip should point for a look, given the entity Jade's own ray found ({@code jadeEntity}, null when
 * it found a block or nothing) and the entity vanilla's crosshair found ({@code vanillaEntity}, null otherwise).
 * Jade hides {@code minecraft:interaction} by default (its {@code hide-entities} list), and model bones and tags are
 * hidden or zero-size, so for a model mob Jade's ray normally passes through to the block behind. Vanilla's pick does
 * hit the interaction, so it is the route that does the work; Jade's entity is used first for players who unhid
 * interactions. The interaction is small next to the drawn model, so when neither hit is a model mob,
 * {@link #rayPick} tests the look against each mob's model box. Pure: no Jade or Minecraft imports.
 */
final class Retarget {
	private Retarget() {}

	/** Point the tooltip at entity {@code hitbox}; {@code viaVanilla} says which hit located it. */
	record Redirect(int hitbox, boolean viaVanilla) {}

	/** The redirect to make, or empty to leave Jade's accessor as it is. */
	static Optional<Redirect> decide(Integer jadeEntity, Integer vanillaEntity, IntFunction<Optional<ModelMob>> mobs) {
		if (jadeEntity != null) {
			Optional<ModelMob> mob = mobs.apply(jadeEntity);
			if (mob.isPresent()) {
				int hitbox = mob.get().hitbox();
				return hitbox == jadeEntity ? Optional.empty() : Optional.of(new Redirect(hitbox, false));
			}
		}
		if (vanillaEntity != null) {
			Optional<ModelMob> mob = mobs.apply(vanillaEntity);
			if (mob.isPresent()) return Optional.of(new Redirect(mob.get().hitbox(), true));
		}
		return Optional.empty();
	}

	/** A model mob's hitbox id, its model box and that box's core (no margin), at the hitbox's live position. */
	record Candidate(int hitbox, ModelBounds box, ModelBounds core) {}

	/** The model a look lands on and how far along the look. */
	record Pick(int hitbox, double distance) {}

	/**
	 * The nearest model box the ray from (ox, oy, oz) along (dx, dy, dz) enters within {@code range}, if it is nearer
	 * than {@code occluder} (the distance to what vanilla's crosshair hit, a block or another entity; infinity for
	 * nothing), so a model behind a wall is never picked. When the eye is inside a box (standing close to the mob),
	 * the box would catch every look, so only a ray that also enters its core counts, at the core's distance.
	 */
	static Optional<Pick> rayPick(double ox, double oy, double oz, double dx, double dy, double dz, double range,
			double occluder, List<Candidate> candidates) {
		Pick best = null;
		for (Candidate c : candidates) {
			OptionalDouble t = c.box().contains(ox, oy, oz) ? c.core().clip(ox, oy, oz, dx, dy, dz, range)
					: c.box().clip(ox, oy, oz, dx, dy, dz, range);
			if (t.isEmpty() || t.getAsDouble() >= occluder) continue;
			if (best == null || t.getAsDouble() < best.distance()) best = new Pick(c.hitbox(), t.getAsDouble());
		}
		return Optional.ofNullable(best);
	}
}
