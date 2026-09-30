package net.mage.cubewheel.tracker.local;

import java.util.Set;

/**
 * Where the player is: singular lower-case world tokens ("sandara", "overworld"), whether one of them is
 * a configured special world, and whether anything was known at all. Pure: no Minecraft/Fabric imports.
 */
public record WorldInfo(Set<String> tokens, boolean special, boolean known) {
	public static final WorldInfo UNKNOWN = new WorldInfo(Set.of(), false, false);

	public WorldInfo {
		tokens = tokens == null ? Set.of() : Set.copyOf(tokens);
	}
}
