package net.mage.cubewheel.tracker;

import java.util.Locale;

/**
 * Which jobs industry the held item is for, from its item id: pickaxes and shovels Mining, hoes and axes Farming
 * (ManaCube files log jobs under Farming), weapons Hunting, the fishing rod Fishing; null for anything else, which
 * leaves the Jobs panel on what it last showed. ManaCube's custom tools keep their vanilla ids ("Relic Hoe" is a
 * netherite hoe). Pure: no Minecraft/Fabric imports.
 */
public final class ToolIndustry {
	private ToolIndustry() {}

	public static String of(String itemId) {
		if (itemId == null) return null;
		String id = itemId.toLowerCase(Locale.ROOT);
		int colon = id.indexOf(':');
		if (colon >= 0) id = id.substring(colon + 1);
		if (id.endsWith("_pickaxe") || id.endsWith("_shovel")) return "Mining";
		if (id.endsWith("_hoe") || id.endsWith("_axe")) return "Farming";
		if (id.endsWith("_sword") || id.equals("bow") || id.equals("crossbow") || id.equals("trident") || id.equals("mace"))
			return "Hunting";
		if (id.equals("fishing_rod")) return "Fishing";
		return null;
	}
}
