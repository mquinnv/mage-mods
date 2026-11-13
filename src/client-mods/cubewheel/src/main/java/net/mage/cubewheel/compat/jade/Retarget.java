package net.mage.cubewheel.compat.jade;

import java.util.Optional;
import java.util.function.IntFunction;
import net.mage.cubewheel.tracker.local.ModelMob;

/**
 * Where Jade's tooltip should point for a look, given the entity Jade's own ray found ({@code jadeEntity}, null when
 * it found a block or nothing) and the entity vanilla's crosshair found ({@code vanillaEntity}, null otherwise).
 * Jade hides {@code minecraft:interaction} by default (its {@code hide-entities} list), and model bones and tags are
 * hidden or zero-size, so for a model mob Jade's ray normally passes through to the block behind. Vanilla's pick does
 * hit the interaction, so it is the route that does the work; Jade's entity is used first for players who unhid
 * interactions. Pure: no Jade or Minecraft imports.
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
}
