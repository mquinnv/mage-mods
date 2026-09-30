package com.mage.cubewheel.tracker;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds "current / max" progress in item lore lines. Pure: no Minecraft/Fabric imports. */
public final class ProgressExtractor {
	public record Progress(double current, double max) {}

	// The suffix must touch the number and not start a word, so "10 mobs" is 10, not 10M.
	private static final String NUM = "(\\d[\\d,]*(?:\\.\\d+)?[kKmM]?)(?![A-Za-z])";
	private static final Pattern RATIO = Pattern.compile(NUM + "\\s*(?:/|\\bof\\b)\\s*" + NUM);
	private static final Pattern PCT = Pattern.compile("(\\d{1,3}(?:\\.\\d+)?)\\s*%");

	private ProgressExtractor() {}

	public static Optional<Progress> extract(List<String> lines) {
		if (lines == null) return Optional.empty();
		for (String line : lines) {
			if (line == null) continue;
			Matcher m = RATIO.matcher(line);
			while (m.find()) {
				OptionalDouble cur = parseNumber(m.group(1));
				OptionalDouble max = parseNumber(m.group(2));
				if (cur.isPresent() && max.isPresent() && max.getAsDouble() > 0) {
					return Optional.of(new Progress(cur.getAsDouble(), max.getAsDouble()));
				}
			}
		}
		for (String line : lines) {
			if (line == null) continue;
			Matcher m = PCT.matcher(line);
			if (m.find()) {
				OptionalDouble pct = parseNumber(m.group(1));
				if (pct.isPresent()) return Optional.of(new Progress(pct.getAsDouble(), 100));
			}
		}
		return Optional.empty();
	}

	/** Parses "1,234", "1.5k", "2M" (case-insensitive suffix). */
	public static OptionalDouble parseNumber(String s) {
		if (s == null) return OptionalDouble.empty();
		String t = s.replace(",", "").replace(" ", "");
		double mult = 1;
		if (!t.isEmpty()) {
			char c = Character.toLowerCase(t.charAt(t.length() - 1));
			if (c == 'k') mult = 1e3;
			else if (c == 'm') mult = 1e6;
			if (mult != 1) t = t.substring(0, t.length() - 1);
		}
		try {
			return OptionalDouble.of(Double.parseDouble(t) * mult);
		} catch (NumberFormatException e) {
			return OptionalDouble.empty();
		}
	}
}
