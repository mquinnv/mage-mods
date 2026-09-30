package net.mage.cubewheel.tracker.local;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the objective lines of a menu item (name + lore). Pure: no Minecraft/Fabric imports. */
public final class ObjectiveExtractor {
	/** Quest/challenge line: optional "NN% " then U+2192 "→". The prestige action arrow "➟" (U+279F) is not matched. */
	private static final Pattern ARROW = Pattern.compile("^\\s*(?:(\\d{1,3}(?:\\.\\d+)?)%\\s*)?→\\s*(.+)$");
	/** Menus without an arrow or OBJECTIVE heading: a line that starts with a counting verb and holds a number. */
	private static final Pattern VERB_LINE = Pattern.compile(
			"(?i)^\\s*(harvest|mine|break|chop|dig|gather|kill|slay|slaughter|defeat|catch|fish|shear)\\b.*\\d.*$");
	private static final Pattern FORMATTING = Pattern.compile("§.");

	private ObjectiveExtractor() {}

	public static ObjectiveInfo extract(String name, List<String> lore) {
		return extract(name, lore, null);
	}

	/**
	 * Like {@link #extract(String, List)}; {@code listingObjective} is a job listing's objective line
	 * ("Shear 10/84 Sheep"), stored as the objective whatever its verb when nothing else is found, so a
	 * verb this class does not know yet still reaches the parser (and the tracker file).
	 */
	public static ObjectiveInfo extract(String name, List<String> lore, String listingObjective) {
		List<String> lines = new ArrayList<>();
		if (lore != null) {
			for (String l : lore) lines.add(clean(l));
		}
		boolean special = false;
		for (String l : lines) {
			if (l.toLowerCase(Locale.ROOT).contains("special worlds")) special = true;
		}
		List<ObjectiveInfo.Sub> subs = prestige(lines);
		if (subs.isEmpty()) subs = arrows(lines);
		if (subs.isEmpty()) subs = verbLines(clean(name), lines);
		if (subs.isEmpty() && !clean(listingObjective).isEmpty()) {
			subs = List.of(new ObjectiveInfo.Sub(clean(listingObjective), null));
		}
		boolean handIn = false;
		for (ObjectiveInfo.Sub s : subs) {
			String t = s.text().toLowerCase(Locale.ROOT);
			if (t.contains("hand in") || t.contains("turn in")) handIn = true;
		}
		return new ObjectiveInfo(subs, special, handIn);
	}

	/** The line after an "OBJECTIVE" heading, without its trailing "[..]" counter. */
	private static List<ObjectiveInfo.Sub> prestige(List<String> lines) {
		for (int i = 0; i < lines.size() - 1; i++) {
			if (!lines.get(i).equalsIgnoreCase("OBJECTIVE")) continue;
			String text = lines.get(i + 1).replaceAll("\\s*\\[[^\\]]*]\\s*$", "").trim();
			if (!text.isEmpty()) return List.of(new ObjectiveInfo.Sub(text, null));
		}
		return List.of();
	}

	private static List<ObjectiveInfo.Sub> arrows(List<String> lines) {
		List<ObjectiveInfo.Sub> out = new ArrayList<>();
		for (String l : lines) {
			Matcher m = ARROW.matcher(l);
			if (!m.matches()) continue;
			Double pct = m.group(1) == null ? null : Double.valueOf(m.group(1));
			out.add(new ObjectiveInfo.Sub(m.group(2).trim(), pct));
		}
		return out;
	}

	private static List<ObjectiveInfo.Sub> verbLines(String name, List<String> lines) {
		List<ObjectiveInfo.Sub> out = new ArrayList<>();
		if (VERB_LINE.matcher(name).matches()) out.add(new ObjectiveInfo.Sub(name, null));
		for (String l : lines) {
			if (VERB_LINE.matcher(l).matches()) out.add(new ObjectiveInfo.Sub(l, null));
		}
		return out;
	}

	private static String clean(String s) {
		return s == null ? "" : FORMATTING.matcher(s).replaceAll("").trim();
	}
}
