package net.mage.cubewheel.tracker.local;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loot entries in an action-bar message: "+5 Mana | +2  Tiger Hide" -> ["Mana", "Tiger Hide"]. An entry
 * is "+", an amount, then words up to a separator ("|", "»", the next "+") or the end. Legacy colour codes
 * are ignored. Pure: no Minecraft/Fabric imports.
 */
public final class LootLine {
	private static final Pattern COLOUR = Pattern.compile("§.");
	private static final Pattern ENTRY = Pattern.compile("\\+\\s*\\d[\\d,.]*\\s+(\\p{L}[\\p{L}'\\- ]*?)\\s*(?=[|»+]|$)");

	private LootLine() {}

	public static List<String> items(String text) {
		if (text == null || text.indexOf('+') < 0) return List.of();
		Matcher m = ENTRY.matcher(COLOUR.matcher(text).replaceAll(""));
		List<String> out = new ArrayList<>();
		while (m.find()) {
			String item = m.group(1).trim().replaceAll("\\s+", " ");
			if (!item.isEmpty()) out.add(item);
		}
		return out;
	}
}
