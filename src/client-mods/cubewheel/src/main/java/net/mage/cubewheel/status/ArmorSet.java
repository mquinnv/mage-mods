package net.mage.cubewheel.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which armor set is worn, from the four pieces' names: the piece word is dropped ("PHOENIX HELMET" -> "Phoenix",
 * "Diamond Boots" -> "Diamond") and the most common rest names the set, with how many pieces share it.
 * When the pieces' lore states a set bonus ("(Requires 4/4 pieces)"), {@code required} is the piece count it needs
 * (0 = no bonus stated). Pure: no Minecraft/Fabric imports.
 */
public record ArmorSet(String name, int matching, int worn, int required) {
	public static final ArmorSet NONE = new ArmorSet("No armor", 0, 0);

	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern REQUIRES = Pattern.compile("(?i)\\(\\s*requires\\s+(\\d+)\\s*/\\s*\\d+\\s+pieces?\\s*\\)");
	private static final Pattern PIECE = Pattern.compile(
			"(?i)\\s*\\b(helmet|helm|cap|hood|crown|mask|hat|chestplate|chest|tunic|breastplate|plate|robe|vest|"
					+ "leggings|legs|pants|greaves|trousers|boots|shoes|sandals|slippers|piece)\\b\\s*$");

	/** A set with no stated bonus (what callers that only know names get). */
	public ArmorSet(String name, int matching, int worn) {
		this(name, matching, worn, 0);
	}

	/** {@code pieces}: the worn pieces' display names (null/blank for an empty slot). */
	public static ArmorSet of(List<String> pieces) {
		return of(pieces, null);
	}

	/**
	 * Like {@link #of(List)}, with each piece's lore lines (parallel to {@code pieces}; null or short = no lore), so a
	 * "(Requires N/4 pieces)" line on any worn piece of the shown set fills in {@link #required()}.
	 */
	public static ArmorSet of(List<String> pieces, List<List<String>> lores) {
		if (pieces == null) return NONE;
		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, Integer> requires = new LinkedHashMap<>();
		int worn = 0;
		for (int i = 0; i < pieces.size(); i++) {
			String p = pieces.get(i);
			if (p == null || p.isBlank()) continue;
			worn++;
			String base = base(p);
			counts.merge(base, 1, Integer::sum);
			int need = lores != null && i < lores.size() ? requirement(lores.get(i)) : 0;
			if (need > 0) requires.merge(base, need, Math::max);
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
		return new ArmorSet(best.isEmpty() ? "Armor" : best, bestCount, worn, requires.getOrDefault(best, 0));
	}

	/** Pieces a lore says the set bonus needs ("(Requires 4/4 pieces)" -> 4); 0 when there is no such line. */
	public static int requirement(List<String> lore) {
		if (lore == null) return 0;
		for (String line : lore) {
			if (line == null) continue;
			Matcher m = REQUIRES.matcher(FORMATTING.matcher(line).replaceAll(""));
			if (m.find()) return Integer.parseInt(m.group(1));
		}
		return 0;
	}

	/** True when a set bonus is stated and fewer pieces are worn than it needs. */
	public boolean bonusUnmet() {
		return required > 0 && matching < required;
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
