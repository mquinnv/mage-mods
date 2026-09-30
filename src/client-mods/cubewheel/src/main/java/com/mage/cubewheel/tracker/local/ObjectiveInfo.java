package com.mage.cubewheel.tracker.local;

import java.util.List;

/**
 * The objective text of one tracked menu item: its objective lines ("subs"), whether the lore says it
 * must be done in a special world, and whether it is a hand-in job. Pure: no Minecraft/Fabric imports.
 */
public record ObjectiveInfo(List<Sub> subs, boolean special, boolean handIn) {
	public static final ObjectiveInfo NONE = new ObjectiveInfo(List.of(), false, false);

	/** One objective line; {@code percent} is the per-line percentage shown before it, or null. */
	public record Sub(String text, Double percent) {}

	public ObjectiveInfo {
		subs = subs == null ? List.of() : List.copyOf(subs);
	}
}
