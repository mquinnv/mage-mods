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
 * (0 = no bonus stated); {@code bonus} is what the set bonus does, as the lore words it ("" = not stated, see
 * {@link #bonus(List)}). Pure: no Minecraft/Fabric imports.
 */
public record ArmorSet(String name, int matching, int worn, int required, String bonus) {
	public static final ArmorSet NONE = new ArmorSet("No armor", 0, 0);

	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern REQUIRES = Pattern.compile("(?i)\\(\\s*requires\\s+(\\d+)\\s*/\\s*\\d+\\s+pieces?\\s*\\)");
	/** The heading of a set bonus block: "FULL SET EFFECTS:", "FULL SET EFFECTS (While Worn)". */
	private static final Pattern FULL_SET = Pattern.compile("(?i)^full set effects?\\b");
	/** An effect line: "➟ Speed V in Worlds", "➤ Mobs drop 3x more EXP". */
	private static final Pattern BULLET = Pattern.compile("^\\s*[➟➤•►▸»]\\s*(.*)$");
	/** Between effects in {@link #bonus}. */
	private static final String JOIN = " · ";
	private static final Pattern PIECE = Pattern.compile(
			"(?i)\\s*\\b(helmet|helm|cap|hood|crown|mask|hat|chestplate|chest|tunic|breastplate|plate|robe|vest|"
					+ "leggings|legs|pants|greaves|trousers|boots|shoes|sandals|slippers|piece)\\b\\s*$");

	/** A set with no stated bonus (what callers that only know names get). */
	public ArmorSet(String name, int matching, int worn) {
		this(name, matching, worn, 0, "");
	}

	/** A set whose bonus needs {@code required} pieces, its effects not known. */
	public ArmorSet(String name, int matching, int worn, int required) {
		this(name, matching, worn, required, "");
	}

	/** {@code pieces}: the worn pieces' display names (null/blank for an empty slot). */
	public static ArmorSet of(List<String> pieces) {
		return of(pieces, null);
	}

	/**
	 * Like {@link #of(List)}, with each piece's lore lines (parallel to {@code pieces}; null or short = no lore), so a
	 * "(Requires N/4 pieces)" line on any worn piece of the shown set fills in {@link #required()}, and the first of
	 * its pieces that states the set bonus fills in {@link #bonus()}.
	 */
	public static ArmorSet of(List<String> pieces, List<List<String>> lores) {
		if (pieces == null) return NONE;
		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, Integer> requires = new LinkedHashMap<>();
		Map<String, String> bonuses = new LinkedHashMap<>();
		int worn = 0;
		for (int i = 0; i < pieces.size(); i++) {
			String p = pieces.get(i);
			if (p == null || p.isBlank()) continue;
			worn++;
			String base = base(p);
			counts.merge(base, 1, Integer::sum);
			List<String> lore = lores != null && i < lores.size() ? lores.get(i) : null;
			int need = requirement(lore);
			if (need > 0) requires.merge(base, need, Math::max);
			if (!bonuses.containsKey(base)) {
				String b = bonus(lore);
				if (!b.isEmpty()) bonuses.put(base, b);
			}
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
		return new ArmorSet(best.isEmpty() ? "Armor" : best, bestCount, worn, requires.getOrDefault(best, 0),
				bonuses.getOrDefault(best, ""));
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

	/**
	 * What a piece's set bonus does, its effects joined with " · " ("" when the lore states none). ManaCube words it
	 * two ways (cubewheel-captures 2026-09-30 / 2026-10-01): a "FULL SET EFFECTS:" heading over the effects, maybe
	 * with a "(Requires 4/4 pieces)" or "(Full Set Required)" note first (Snowy, Warden, Morend, Dragonscale, which
	 * also list the piece's own "ITEM EFFECTS" above it); or, older, the effects right above the
	 * "(Requires 4/4 pieces)" line (Obsidian, Mystic, "Dragon Armor Set:"). An effect wrapped onto an indented next
	 * line is joined back into one, and "+ Strength II" reads "+Strength II".
	 */
	public static String bonus(List<String> lore) {
		if (lore == null || lore.isEmpty()) return "";
		List<String> clean = lore.stream().map(l -> l == null ? "" : FORMATTING.matcher(l).replaceAll("")).toList();
		for (int i = 0; i < clean.size(); i++) {
			if (FULL_SET.matcher(clean.get(i).trim()).find()) return effects(clean, i + 1);
		}
		for (int i = 0; i < clean.size(); i++) {
			if (!REQUIRES.matcher(clean.get(i)).find()) continue;
			int start = i;
			while (start > 0 && (BULLET.matcher(clean.get(start - 1)).matches() || continuation(clean.get(start - 1)))) start--;
			return effects(clean.subList(0, i), start);
		}
		return "";
	}

	/** The effects listed from {@code from}: notes in brackets before them skipped, up to the first other line. */
	private static String effects(List<String> lines, int from) {
		StringBuilder out = new StringBuilder();
		StringBuilder effect = null;
		for (int i = from; i < lines.size(); i++) {
			String line = lines.get(i);
			Matcher bullet = BULLET.matcher(line);
			if (bullet.matches()) {
				if (effect != null) append(out, effect);
				effect = new StringBuilder(bullet.group(1).trim().replaceFirst("^\\+\\s+", "+"));
			} else if (effect != null && continuation(line)) {
				effect.append(' ').append(line.trim());
			} else if (effect == null && line.trim().startsWith("(")) {
				continue; // "(Requires 4/4 pieces)", "(Full Set Required)" before the effects
			} else {
				break;
			}
		}
		if (effect != null) append(out, effect);
		return out.toString();
	}

	private static void append(StringBuilder out, StringBuilder effect) {
		String e = effect.toString().replaceAll("\\s+", " ").trim();
		if (e.isEmpty()) return;
		if (out.length() > 0) out.append(JOIN);
		out.append(e);
	}

	/** An indented line carrying on the effect above it ("   World Monsters & Resources"). */
	private static boolean continuation(String line) {
		return !line.isBlank() && Character.isWhitespace(line.charAt(0));
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
