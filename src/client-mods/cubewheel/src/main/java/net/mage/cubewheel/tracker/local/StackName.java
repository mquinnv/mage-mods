package net.mage.cubewheel.tracker.local;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mob custom names as stacker/health plugins decorate them: "12x Zombie", "Tiger x5", "Tiger (x5)",
 * "Mana Wolf ❤ 20", "Dart Frog 20⺛", "3x Mana Slime [40/40]". Parses the stack size (1 if none) and the
 * bare name. Pure: no Minecraft/Fabric imports.
 */
public final class StackName {
	public record Parsed(int count, String name) {}

	private static final Pattern FORMATTING = Pattern.compile("§.");
	/** "12x Zombie", "12 x Zombie", "x12 Zombie". */
	private static final Pattern STACK_PREFIX = Pattern.compile("^(?:(\\d{1,6})\\s*[xX×]|[xX×]\\s*(\\d{1,6}))\\s+(.*)$");
	/** "Zombie x12", "Zombie x 12", "Zombie 12x", "Zombie (x12)", "Zombie (12)". */
	private static final Pattern STACK_SUFFIX = Pattern.compile(
			"^(.*?)\\s*(?:\\s[xX×]\\s*(\\d{1,6})|\\s(\\d{1,6})\\s*[xX×]|\\(\\s*[xX×]?\\s*(\\d{1,6})\\s*[xX×]?\\s*\\))$");
	/**
	 * A trailing health display: a heart glyph and what follows, "[a/b]" / "a/b" numbers, or a number with
	 * a glyph ("20⺛", ManaCube's mob health).
	 */
	private static final Pattern HEALTH = Pattern.compile("\\s*(?:[❤♥].*|\\[?\\d+(?:\\.\\d+)?\\s*/\\s*\\d+(?:\\.\\d+)?]?"
			+ "|\\s\\d[\\d,]*(?:\\.\\d+)?\\s*[^\\p{L}\\p{N}\\s\\[\\]()]{1,3})\\s*$");

	private StackName() {}

	public static Optional<Parsed> parse(String raw) {
		if (raw == null) return Optional.empty();
		String s = FORMATTING.matcher(raw).replaceAll("").trim();
		int count = 1;
		Matcher m = STACK_PREFIX.matcher(s);
		if (m.matches()) {
			count = Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(2));
			s = m.group(3);
		}
		s = HEALTH.matcher(s).replaceFirst("").trim();
		if (count == 1) {
			m = STACK_SUFFIX.matcher(s);
			if (m.matches() && !m.group(1).isBlank()) {
				String n = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4);
				count = Integer.parseInt(n);
				s = HEALTH.matcher(m.group(1)).replaceFirst("").trim();
			}
		}
		return s.isEmpty() ? Optional.empty() : Optional.of(new Parsed(count, s));
	}
}
