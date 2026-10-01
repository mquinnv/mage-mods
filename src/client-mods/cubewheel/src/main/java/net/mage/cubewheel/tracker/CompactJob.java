package net.mage.cubewheel.tracker;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/** Short forms for the Jobs and Tracker panels' narrow columns. Pure: no Minecraft/Fabric imports. */
final class CompactJob {
	/** Longest target shown; longer ones are cut with "…". */
	static final int MAX_TARGET = 40;

	private static final Pattern VERB = Pattern.compile("(?i)^(?:harvest or mine|harvest|mine|break|chop|dig|gather|"
			+ "kill|slay|slaughter|defeat|catch|fish|shear|milk|cook|collect|craft|smelt|brew|complete|deliver|obtain|get|reach)\\s+");
	/** A leading amount ("2,500 Skill Level", "15,000 Tangleroots Resources"); the count column shows it. */
	private static final Pattern LEADING_NUMBER = Pattern.compile("^\\d[\\d,.]*[kKmM]?\\s+");
	private static final Pattern IN_WORLD = Pattern.compile("(?i)\\s+(?:in|at|from)\\s+\\S.*$");
	private static final Pattern WHILE = Pattern.compile("(?i)\\s+while\\s+\\w+.*$");
	private static final Pattern PRIVATE_USE = Pattern.compile("[\\uE000-\\uF8FF]");

	/** Short world tags by the start of the world key; "✦" for "special worlds" objectives. */
	private static final java.util.List<String[]> WORLD_TAGS = java.util.List.of(
			new String[] {"wolf", "WH"}, new String[] {"tangle", "TR"}, new String[] {"sand", "SA"},
			new String[] {"ice", "IH"}, new String[] {"morend", "MO"}, new String[] {"burn", "BL"});
	static final String SPECIAL_TAG = "\u2726";

	private CompactJob() {}

	/** The world a job is tied to, as a two-letter tag ("TR"), "✦" for any special world, "" for none. */
	static String worldTag(net.mage.cubewheel.tracker.local.WorldScope.Scope scope) {
		if (scope == null || !scope.scoped()) return "";
		if (scope.worlds().isEmpty()) return SPECIAL_TAG;
		String key = scope.worlds().iterator().next();
		for (String[] t : WORLD_TAGS) {
			if (key.startsWith(t[0])) return t[1];
		}
		return key.length() >= 2 ? key.substring(0, 2).toUpperCase(Locale.ROOT) : key.toUpperCase(Locale.ROOT);
	}

	/** "Harvest or Mine Wolfhaven Resources" → "Resources"; "Slay Tigers in Tangleroots" → "Tigers". */
	static String target(String objective, Collection<String> worldNames) {
		return cut(objective(objective, worldNames), MAX_TARGET);
	}

	/**
	 * {@link #target} without the cut: "Mine 15,000 Tangleroots Resources" → "Resources", "Reach 2,500 Skill Level"
	 * → "Skill Level", "Reach Party Level 55" → "Party Level 55". Falls back to the cleaned text if nothing is left.
	 */
	static String objective(String objective, Collection<String> worldNames) {
		String t = clean(objective);
		String full = t;
		t = VERB.matcher(t).replaceFirst("");
		t = LEADING_NUMBER.matcher(t).replaceFirst("");
		t = WHILE.matcher(t).replaceFirst("");
		t = IN_WORLD.matcher(t).replaceFirst("");
		t = dropLeadingWorld(t, worldNames);
		return t.isBlank() ? full : t;
	}

	/** Private-use glyphs (server icons) dropped, spaces collapsed. */
	static String clean(String s) {
		return PRIVATE_USE.matcher(s == null ? "" : s).replaceAll(" ").replaceAll("\\s+", " ").trim();
	}

	/** {@code s} cut to {@code max} characters, ending in "…" when cut. */
	static String cut(String s, int max) {
		return s.length() > max ? s.substring(0, max - 1).trim() + "…" : s;
	}

	/**
	 * "3,127/4,800", "~2/2 ✓?", "64/64 ✓". How old the last read is shows only in the Tracker screen: on the HUD a
	 * trailing "·3h" read as a countdown.
	 */
	static String count(TrackerRow row, long now) {
		String c = (row.estimated() ? "~" : "") + number(row.shownCurrent()) + "/" + number(row.shownMax());
		if (row.complete()) c += " ✓";
		else if (row.atCap()) c += " ✓?";
		return c;
	}

	/**
	 * 950 → "950", 3127 → "3,127" (exact below 10,000, so the last few are visible), 12500 → "12.5k",
	 * 250000 → "250k", 1_250_000 → "1.3M".
	 */
	static String number(double v) {
		double a = Math.abs(v);
		if (a < 1_000) return v == Math.rint(v) ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
		if (a < 10_000) return String.format(Locale.ROOT, "%,d", Math.round(v));
		if (a < 100_000) return trimZero(String.format(Locale.ROOT, "%.1f", v / 1_000)) + "k";
		if (a < 1_000_000) return Math.round(v / 1_000) + "k";
		return trimZero(String.format(Locale.ROOT, "%.1f", v / 1_000_000)) + "M";
	}

	private static String trimZero(String s) {
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	/** Drops a leading mana-world name ("Wolfhaven Resources" → "Resources"); the panel's colour shows the world. */
	private static String dropLeadingWorld(String t, Collection<String> worldNames) {
		if (worldNames == null) return t;
		int space = t.indexOf(' ');
		if (space <= 0) return t;
		String first = norm(t.substring(0, space));
		for (String w : worldNames) {
			if (w != null && norm(w).equals(first)) return t.substring(space + 1).trim();
		}
		return t;
	}

	private static String norm(String s) {
		String n = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		return n.endsWith("s") ? n.substring(0, n.length() - 1) : n;
	}
}
