package com.mage.cubewheel.hud;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses "1h 30m", "12m 30s", "0.5s", "10 seconds" and formats countdowns. Pure: no Minecraft/Fabric imports. */
public final class Durations {
	private Durations() {}

	private static final Pattern TOKEN = Pattern.compile(
			"(\\d+(?:\\.\\d+)?)\\s*\\(?\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\s*\\)?(?![a-z])");
	private static final Pattern SEPARATOR = Pattern.compile("[\\s,]*(?:and\\s+)?");

	/**
	 * Milliseconds for a string made only of number+unit tokens (h/hr/hour, m/min/minute, s/sec/second, any
	 * case, decimals allowed, separated by spaces, commas or "and"). Anything else, a bare number included,
	 * is rejected.
	 */
	public static OptionalLong parse(String text) {
		if (text == null) return OptionalLong.empty();
		String s = text.trim().toLowerCase(Locale.ROOT);
		if (s.isEmpty()) return OptionalLong.empty();
		Matcher m = TOKEN.matcher(s);
		Matcher sep = SEPARATOR.matcher(s);
		int pos = 0;
		double total = 0;
		boolean any = false;
		while (pos < s.length()) {
			if (any && sep.region(pos, s.length()).lookingAt()) pos = sep.end();
			if (pos >= s.length()) break;
			if (!m.region(pos, s.length()).lookingAt()) return OptionalLong.empty();
			double n = Double.parseDouble(m.group(1));
			total += n * unitMs(m.group(2));
			pos = m.end();
			any = true;
		}
		if (!any || !Double.isFinite(total)) return OptionalLong.empty();
		return OptionalLong.of(Math.round(total));
	}

	private static long unitMs(String unit) {
		return switch (unit.charAt(0)) {
			case 'h' -> 3_600_000L;
			case 'm' -> 60_000L;
			default -> 1_000L;
		};
	}

	/** "m:ss" or "h:mm:ss", rounded up to whole seconds (so it never shows 0:00 while time remains). */
	public static String countdown(long ms) {
		long s = ms <= 0 ? 0 : (ms + 999) / 1000;
		long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
		return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
				: String.format(Locale.ROOT, "%d:%02d", m, sec);
	}

	/** Like {@link #countdown} but "4.5s" under 10 s and "45s" under a minute (short item cooldowns). */
	public static String shortCountdown(long ms) {
		if (ms < 10_000) {
			long tenths = ms <= 0 ? 0 : (ms + 99) / 100;
			return String.format(Locale.ROOT, "%d.%ds", tenths / 10, tenths % 10);
		}
		if (ms < 60_000) return ((ms + 999) / 1000) + "s";
		return countdown(ms);
	}
}
