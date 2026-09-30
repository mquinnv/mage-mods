package com.mage.cubewheel.tracker.local;

import java.util.Set;

/**
 * Something the client saw the player do. Ids are registry ids ("minecraft:wheat"), names are display
 * names without decorations, groups are "crop", "ore", "logs" (blocks) or "mob", "monster", "player"
 * (entities). Pure: no Minecraft/Fabric imports.
 */
public sealed interface Signal permits Signal.BlockBroken, Signal.MobKilled, Signal.FishCaught {
	WorldInfo world();

	/** A block the player broke; {@code mature} only matters for crops. */
	record BlockBroken(String id, String name, Set<String> groups, boolean crop, boolean mature, WorldInfo world) implements Signal {
		public BlockBroken {
			groups = groups == null ? Set.of() : Set.copyOf(groups);
		}
	}

	/** {@code count} mobs killed by the player (1 unless a stack died). */
	record MobKilled(String typeId, String name, Set<String> groups, int count, WorldInfo world) implements Signal {
		public MobKilled {
			groups = groups == null ? Set.of() : Set.copyOf(groups);
		}
	}

	record FishCaught(WorldInfo world) implements Signal {}
}
