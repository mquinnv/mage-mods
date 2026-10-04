package net.mage.cubewheel.tracker.local;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loot entries in an action-bar message: "+5 Mana | +2  Tiger Hide" -> ["Mana", "Tiger Hide"]. An entry
 * is "+", an amount, then words up to a separator ("|", "»", the next "+") or the end. Legacy colour codes
 * are ignored, and so is the icon glyph ManaCube puts before a drop's name (real lines read "+2  Moose
 * Skin": a private-use or symbol character, capture 2026-10-04). Pure: no Minecraft/Fabric imports.
 */
public final class LootLine {
	private static final Pattern COLOUR = Pattern.compile("§.");
	private static final Pattern ENTRY = Pattern.compile(
			"\\+\\s*(\\d[\\d,.]*)[\\s\\p{Co}\\p{So}]+(\\p{L}[\\p{L}'\\- ]*?)\\s*(?=[|»+]|$)");

	/** One "+amount item" entry; {@code amount} as written, without thousands separators ("1,250" -> "1250"). */
	public record Entry(String amount, String item) {}

	private LootLine() {}

	public static List<String> items(String text) {
		return entries(text).stream().map(Entry::item).toList();
	}

	/** The loot entries of a message, in order: "+7 Mana | +2 Moose Skin" -> [(7, Mana), (2, Moose Skin)]. */
	public static List<Entry> entries(String text) {
		if (text == null || text.indexOf('+') < 0) return List.of();
		Matcher m = ENTRY.matcher(COLOUR.matcher(text).replaceAll(""));
		List<Entry> out = new ArrayList<>();
		while (m.find()) {
			String item = m.group(2).trim().replaceAll("\\s+", " ");
			if (!item.isEmpty()) out.add(new Entry(m.group(1).replace(",", ""), item));
		}
		return out;
	}
}
