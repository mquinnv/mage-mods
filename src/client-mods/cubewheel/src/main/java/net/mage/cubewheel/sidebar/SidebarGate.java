package net.mage.cubewheel.sidebar;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Is the player in ManaCube Survival? ManaCube runs SkyBlock, Parkour, the hub and more on the same
 * host; only Survival's sidebar title says "SURVIVAL". Pure: no Minecraft/Fabric imports.
 */
public final class SidebarGate {
	private static final Pattern FORMATTING = Pattern.compile("§.");
	/** The last compiled pattern (the config rarely changes); null source = nothing compiled yet. */
	private static String cachedSource;
	private static Pattern cachedPattern;
	private static boolean cachedInvalid;
	/** The last answer: the title and pattern rarely change between the many calls per frame. */
	private record Answer(String title, String pattern, boolean matches) {}

	private static volatile Answer last;

	private SidebarGate() {}

	/**
	 * True if the sidebar {@code title} contains a match of {@code pattern}. No sidebar (null or blank
	 * title) never matches; an invalid pattern never matches; a null or blank pattern switches the gate
	 * off (always true).
	 */
	public static boolean matches(String title, String pattern) {
		if (pattern == null || pattern.isBlank()) return true;
		if (title == null) return false;
		Answer a = last;
		if (a != null && a.title().equals(title) && a.pattern().equals(pattern)) return a.matches();
		boolean matches = matchUncached(title, pattern);
		last = new Answer(title, pattern, matches);
		return matches;
	}

	private static boolean matchUncached(String title, String pattern) {
		String t = FORMATTING.matcher(title).replaceAll("").trim();
		if (t.isEmpty()) return false;
		Pattern p = compile(pattern);
		return p != null && p.matcher(t).find();
	}

	private static synchronized Pattern compile(String pattern) {
		if (!pattern.equals(cachedSource)) {
			cachedSource = pattern;
			try {
				cachedPattern = Pattern.compile(pattern);
				cachedInvalid = false;
			} catch (PatternSyntaxException e) {
				cachedPattern = null;
				cachedInvalid = true;
			}
		}
		return cachedInvalid ? null : cachedPattern;
	}
}
