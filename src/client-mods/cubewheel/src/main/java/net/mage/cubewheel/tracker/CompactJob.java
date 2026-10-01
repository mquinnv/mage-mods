package net.mage.cubewheel.tracker;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/** Short forms for the Jobs panel's narrow columns. Pure: no Minecraft/Fabric imports. */
final class CompactJob {
	/** Longest target shown; longer ones are cut with "…". */
	static final int MAX_TARGET = 16;
	/** Reads at least this old show their age ("·3h"). */
	static final long SHOW_AGE_MS = 3_600_000L;

	private static final Pattern VERB = Pattern.compile("(?i)^(?:harvest or mine|harvest|mine|break|chop|dig|gather|"
			+ "kill|slay|slaughter|defeat|catch|fish|shear|cook|collect|craft|smelt|brew|complete|deliver|obtain|get)\\s+");
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
		String t = PRIVATE_USE.matcher(objective == null ? "" : objective).replaceAll(" ").replaceAll("\\s+", " ").trim();
		t = VERB.matcher(t).replaceFirst("");
		t = WHILE.matcher(t).replaceFirst("");
		t = IN_WORLD.matcher(t).replaceFirst("");
		t = dropLeadingWorld(t, worldNames);
		if (t.length() > MAX_TARGET) t = t.substring(0, MAX_TARGET - 1).trim() + "…";
		return t;
	}

	/** "3.1k/4.8k", "~2/2 ✓?", "64/64 ✓", plus "·3h" for old reads. */
	static String count(TrackerRow row, long now) {
		String c = (row.estimated() ? "~" : "") + number(row.shownCurrent()) + "/" + number(row.shownMax());
		if (row.complete()) c += " ✓";
		else if (row.atCap()) c += " ✓?";
		long age = now - row.item().seenAt();
		if (age >= SHOW_AGE_MS) c += " ·" + TrackerFormat.age(age);
		return c;
	}

	/** 950 → "950", 3127 → "3.1k", 12500 → "12.5k", 250000 → "250k", 1_250_000 → "1.3M". */
	static String number(double v) {
		double a = Math.abs(v);
		if (a < 1_000) return v == Math.rint(v) ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
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
