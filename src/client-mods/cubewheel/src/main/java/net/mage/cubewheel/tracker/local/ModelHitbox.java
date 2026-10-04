package net.mage.cubewheel.tracker.local;

import java.util.Locale;
import java.util.Set;

/**
 * Is a removed entity the hitbox of a ManaCube custom-model mob (ModelEngine)? Sandara's rattlesnakes and vipers
 * (capture 2026-10-01) are a {@code minecraft:interaction} riding its own {@code area_effect_cloud} plus invisible
 * slimes, next to the model's parts ({@code item_display} bones riding a cloud) and a name tag; they are removed
 * without a death event. Such a removal is a monster kill even when nothing names it. Items, displays, armor
 * stands, projectiles, vehicles, players and owned animals never are. The adapter gathers the facts; this decides.
 * Pure: no Minecraft/Fabric imports.
 */
public final class ModelHitbox {
	public static final String INTERACTION = "minecraft:interaction";
	public static final String CLOUD = "minecraft:area_effect_cloud";

	/** Exact types that are never a model's hitbox. */
	private static final Set<String> NEVER = Set.of("item", "experience_orb", "armor_stand", "player", "falling_block",
			"tnt", "end_crystal", "lightning_bolt", "marker", "leash_knot", "painting", "item_frame", "glow_item_frame");

	/** Projectiles: thrown or shot things a player can hit (a breeze's wind charge, deflected). */
	private static final Set<String> PROJECTILES = Set.of("trident", "snowball", "egg", "ender_pearl", "potion",
			"splash_potion", "lingering_potion", "experience_bottle", "llama_spit", "shulker_bullet", "firework_rocket",
			"eye_of_ender", "fishing_bobber", "dragon_fireball", "wither_skull");

	/**
	 * What the adapter saw of a removed entity: its type, its vehicle's type (null if none), whether it is invisible,
	 * a Mob, owned (a tamed pet), and whether model parts (an item_display riding a cloud) are within 3 blocks.
	 */
	public record Facts(String typeId, String vehicleTypeId, boolean invisible, boolean mob, boolean owned,
			boolean modelPartsNear) {}

	private ModelHitbox() {}

	/** An interaction riding a cloud, or an invisible unowned Mob with model parts near; never an excluded type. */
	public static boolean isModelHitbox(Facts f) {
		if (f == null || f.typeId() == null || excluded(f.typeId()) || f.owned()) return false;
		if (INTERACTION.equals(f.typeId())) return CLOUD.equals(f.vehicleTypeId());
		return f.mob() && f.invisible() && f.modelPartsNear();
	}

	/** Projectiles and vehicles: hitting one and seeing it removed is never a kill (a deflected wind charge). */
	public static boolean neverAKill(String typeId) {
		if (typeId == null) return false;
		String path = path(typeId);
		return PROJECTILES.contains(path) || path.contains("arrow") || path.contains("wind_charge")
				|| path.contains("fireball") || path.endsWith("boat") || path.endsWith("raft") || path.contains("minecart");
	}

	private static boolean excluded(String typeId) {
		String path = path(typeId);
		return NEVER.contains(path) || path.endsWith("_display") || neverAKill(typeId);
	}

	private static String path(String typeId) {
		return typeId.substring(typeId.indexOf(':') + 1).toLowerCase(Locale.ROOT);
	}
}
