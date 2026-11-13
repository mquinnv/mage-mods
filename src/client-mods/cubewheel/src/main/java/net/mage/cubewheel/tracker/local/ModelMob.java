package net.mage.cubewheel.tracker.local;

import java.util.Objects;
import java.util.Set;

/**
 * One ManaCube custom-model mob, grouped from its parts by {@link ModelMobs#group}: its bare name (no health, no
 * stack count), the text display that named it ({@code tagEntity}, null if none), the {@code minecraft:interaction}
 * it is hit through ({@code hitbox}, the stable id a look resolves to) and that interaction's position, the ids
 * of every entity that belongs to it ({@code parts}, the hitbox included), and the box the drawn model fills
 * ({@code bounds}, relative to the hitbox's position, so a look can test it at the hitbox's live position) and
 * that box before its margin ({@code core}: the interaction's box and the bones' raw extent).
 * Pure: no Minecraft/Fabric imports.
 */
public record ModelMob(String name, Integer tagEntity, int hitbox, double x, double y, double z, Set<Integer> parts,
		ModelBounds bounds, ModelBounds core) {
	public ModelMob {
		parts = Set.copyOf(parts);
		Objects.requireNonNull(bounds, "bounds");
		Objects.requireNonNull(core, "core");
	}
}
