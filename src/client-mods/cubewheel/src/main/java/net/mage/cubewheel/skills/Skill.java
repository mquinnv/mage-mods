package net.mage.cubewheel.skills;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The 17 mcMMO skills ManaCube Survival lists in its {@code /mcmmo} "Skills Panel | Browsing..." menu, in menu slot
 * order (captured 2026-10-01). The menu names each as {@code SKILL ▪ [CATEGORY]} and shows {@code Level: 137/2500};
 * {@code defaultCap} and {@code iconId} come from that capture. Smelting is a child skill (it does not count to the
 * power level). Pure: no Minecraft/Fabric imports.
 */
public enum Skill {
	HERBALISM("Herbalism", Category.GATHERING, 2500, false, "minecraft:golden_hoe"),
	ACROBATICS("Acrobatics", Category.MISC, 500, false, "minecraft:rabbit_foot"),
	ALCHEMY("Alchemy", Category.MISC, 500, false, "minecraft:brewing_stand"),
	REPAIR("Repair", Category.MISC, 500, false, "minecraft:anvil"),
	SMELTING("Smelting", Category.MISC, 3000, true, "minecraft:blast_furnace"),
	MACES("Maces", Category.COMBAT, 1000, false, "minecraft:mace"),
	CROSSBOWS("Crossbows", Category.COMBAT, 1000, false, "minecraft:crossbow"),
	TRIDENT("Trident", Category.COMBAT, 1000, false, "minecraft:trident"),
	SWORDS("Swords", Category.COMBAT, 1000, false, "minecraft:netherite_sword"),
	MINING("Mining", Category.GATHERING, 2500, false, "minecraft:golden_pickaxe"),
	AXES("Axes", Category.COMBAT, 1000, false, "minecraft:netherite_axe"),
	WOODCUTTING("Woodcutting", Category.GATHERING, 2500, false, "minecraft:golden_axe"),
	ARCHERY("Archery", Category.COMBAT, 1000, false, "minecraft:bow"),
	EXCAVATION("Excavation", Category.GATHERING, 2500, false, "minecraft:golden_shovel"),
	TAMING("Taming", Category.COMBAT, 500, false, "minecraft:bone"),
	FISHING("Fishing", Category.GATHERING, 1000, false, "minecraft:fishing_rod"),
	/** The menu reuses the netherite axe icon for Unarmed. */
	UNARMED("Unarmed", Category.COMBAT, 1000, false, "minecraft:netherite_axe");

	public enum Category { GATHERING, COMBAT, MISC }

	private static final Pattern FORMATTING = Pattern.compile("§.");

	/** Title case: "Mining". */
	public final String label;
	/** Upper case as the menu and boss bars print it: "MINING". */
	public final String menuName;
	public final Category category;
	public final int defaultCap;
	/** True only for Smelting: not part of the power level. */
	public final boolean child;
	public final String iconId;

	Skill(String label, Category category, int defaultCap, boolean child, String iconId) {
		this.label = label;
		this.menuName = label.toUpperCase(Locale.ROOT);
		this.category = category;
		this.defaultCap = defaultCap;
		this.child = child;
		this.iconId = iconId;
	}

	/**
	 * "mining", "MINING", "§aMining" -> MINING; also accepts the singular of the plural names (SWORD, AXE, MACE,
	 * CROSSBOW) and the plural of TRIDENT; anything else (or null) is empty.
	 */
	public static Optional<Skill> byName(String name) {
		if (name == null) return Optional.empty();
		String n = FORMATTING.matcher(name).replaceAll("").trim().toUpperCase(Locale.ROOT);
		if (n.isEmpty()) return Optional.empty();
		for (Skill s : values()) {
			if (s.menuName.equals(n)) return Optional.of(s);
		}
		return switch (n) {
			case "TRIDENTS" -> Optional.of(TRIDENT);
			case "MACE" -> Optional.of(MACES);
			case "CROSSBOW" -> Optional.of(CROSSBOWS);
			case "SWORD" -> Optional.of(SWORDS);
			case "AXE" -> Optional.of(AXES);
			default -> Optional.empty();
		};
	}
}
