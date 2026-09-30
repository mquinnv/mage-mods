package net.mage.cubewheel.tracker.local;

import java.util.List;
import java.util.Locale;

/** Is a held item a pair of shears (vanilla or a custom tool)? Pure: no Minecraft/Fabric imports. */
public final class ShearTool {
	private ShearTool() {}

	/**
	 * Vanilla shears (a {@code ShearsItem}, or id {@code minecraft:shears}), or any item whose name or lore
	 * mentions "Shears" (ManaCube's custom tools).
	 */
	public static boolean isShears(boolean shearsItem, String itemId, String name, List<String> lore) {
		if (shearsItem || "minecraft:shears".equals(itemId)) return true;
		if (mentions(name)) return true;
		if (lore != null) {
			for (String l : lore) {
				if (mentions(l)) return true;
			}
		}
		return false;
	}

	private static boolean mentions(String s) {
		return s != null && s.toLowerCase(Locale.ROOT).contains("shears");
	}
}
