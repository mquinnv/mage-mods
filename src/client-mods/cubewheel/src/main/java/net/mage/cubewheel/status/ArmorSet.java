package net.mage.cubewheel.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Which armor set is worn, from the four pieces' names: the piece word is dropped ("PHOENIX HELMET" -> "Phoenix",
 * "Diamond Boots" -> "Diamond") and the most common rest names the set, with how many pieces share it.
 * Pure: no Minecraft/Fabric imports.
 */
public record ArmorSet(String name, int matching, int worn) {
	public static final ArmorSet NONE = new ArmorSet("No armor", 0, 0);

	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern PIECE = Pattern.compile(
			"(?i)\\s*\\b(helmet|helm|cap|hood|crown|mask|hat|chestplate|chest|tunic|breastplate|plate|robe|vest|"
					+ "leggings|legs|pants|greaves|trousers|boots|shoes|sandals|slippers|piece)\\b\\s*$");

	/** {@code pieces}: the worn pieces' display names (null/blank for an empty slot). */
	public static ArmorSet of(List<String> pieces) {
		if (pieces == null) return NONE;
		Map<String, Integer> counts = new LinkedHashMap<>();
		int worn = 0;
		for (String p : pieces) {
			if (p == null || p.isBlank()) continue;
			worn++;
			counts.merge(base(p), 1, Integer::sum);
		}
		if (worn == 0) return NONE;
		String best = null;
		int bestCount = 0;
		for (Map.Entry<String, Integer> e : counts.entrySet()) {
			if (e.getValue() > bestCount) {
				best = e.getKey();
				bestCount = e.getValue();
			}
		}
		return new ArmorSet(best.isEmpty() ? "Armor" : best, bestCount, worn);
	}

	/** "PHOENIX HELMET" -> "Phoenix"; "Mana Boots of Speed" keeps the words after the piece out of it. */
	static String base(String name) {
		String n = FORMATTING.matcher(name).replaceAll("").replaceAll("[^\\p{L}\\p{N}' ]", " ").replaceAll("\\s+", " ").trim();
		String lower = n.toLowerCase(Locale.ROOT);
		int of = lower.indexOf(" of ");
		if (of > 0) n = n.substring(0, of).trim(); // "Boots of Speed" style: the set word comes first
		n = PIECE.matcher(n).replaceAll("").trim();
		return titleCase(n);
	}

	/** All-caps words in title case; mixed case left alone. */
	static String titleCase(String s) {
		if (s.isEmpty() || !s.equals(s.toUpperCase(Locale.ROOT))) return s;
		StringBuilder out = new StringBuilder();
		for (String w : s.toLowerCase(Locale.ROOT).split(" ")) {
			if (w.isEmpty()) continue;
			if (out.length() > 0) out.append(' ');
			out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return out.toString();
	}

	/** "4/4" style count: pieces of this set out of four slots. */
	public String count() {
		return matching + "/4";
	}
}
