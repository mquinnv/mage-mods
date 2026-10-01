package net.mage.cubewheel.tracker.local;

import java.util.Set;

/**
 * Something the client saw the player do. Ids are registry ids ("minecraft:wheat"), names are display
 * names without decorations, groups are "crop", "ore", "logs" (blocks) or "mob", "monster", "player"
 * (entities). Pure: no Minecraft/Fabric imports.
 */
public sealed interface Signal permits Signal.BlockBroken, Signal.MobKilled, Signal.FishCaught, Signal.Sheared, Signal.Milked {
	WorldInfo world();

	/**
	 * A block the player broke; {@code mature} only matters for crops. {@code trivial}: replaceable or
	 * instabreak (grass, flowers, ferns), which "any resource/block" objectives do not count.
	 */
	record BlockBroken(String id, String name, Set<String> groups, boolean crop, boolean mature, boolean trivial,
			WorldInfo world) implements Signal {
		public BlockBroken {
			groups = groups == null ? Set.of() : Set.copyOf(groups);
		}

		/** A non-trivial block. */
		public BlockBroken(String id, String name, Set<String> groups, boolean crop, boolean mature, WorldInfo world) {
			this(id, name, groups, crop, mature, false, world);
		}
	}

	/** {@code count} mobs killed by the player (1 unless a stack died). */
	record MobKilled(String typeId, String name, Set<String> groups, int count, WorldInfo world) implements Signal {
		public MobKilled {
			groups = groups == null ? Set.of() : Set.copyOf(groups);
		}
	}

	/**
	 * A fish the player caught. {@code species}/{@code rarity} come from ManaCube's catch chat line
	 * ("You caught a 52.2cm Common Flounder"); both are null for a vanilla bobber reel-in, which then only
	 * counts toward "any fish" objectives.
	 */
	record FishCaught(String species, String rarity, WorldInfo world) implements Signal {
		/** A catch of unknown species (vanilla bobber reel-in). */
		public FishCaught(WorldInfo world) {
			this(null, null, world);
		}
	}

	/** A shearable entity the player sheared (the server confirmed it: its sheared flag synced to true). */
	record Sheared(String typeId, String name, WorldInfo world) implements Signal {}

	/** A mob the player milked with an empty bucket (the server kept the swap). */
	record Milked(String typeId, String name, WorldInfo world) implements Signal {}
}
