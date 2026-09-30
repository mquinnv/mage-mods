package com.mage.cubewheel.tracker.local;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mob custom names as stacker/health plugins decorate them: "12x Zombie", "Mana Wolf ❤ 20",
 * "3x Mana Slime [40/40]". Parses the stack size (1 if none) and the bare name. Pure: no
 * Minecraft/Fabric imports.
 */
public final class StackName {
	public record Parsed(int count, String name) {}

	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern STACK = Pattern.compile("^(\\d{1,6})\\s*[xX×]\\s+(.*)$");
	/** A trailing health display: a heart glyph and what follows, or "[a/b]" / "a/b" numbers. */
	private static final Pattern HEALTH = Pattern.compile("\\s*(?:[❤♥].*|\\[?\\d+(?:\\.\\d+)?\\s*/\\s*\\d+(?:\\.\\d+)?]?)\\s*$");

	private StackName() {}

	public static Optional<Parsed> parse(String raw) {
		if (raw == null) return Optional.empty();
		String s = FORMATTING.matcher(raw).replaceAll("").trim();
		int count = 1;
		Matcher m = STACK.matcher(s);
		if (m.matches()) {
			count = Integer.parseInt(m.group(1));
			s = m.group(2);
		}
		s = HEALTH.matcher(s).replaceFirst("").trim();
		return s.isEmpty() ? Optional.empty() : Optional.of(new Parsed(count, s));
	}
}
