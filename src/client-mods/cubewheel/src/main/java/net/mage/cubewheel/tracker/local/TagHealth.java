package net.mage.cubewheel.tracker.local;

import java.util.OptionalDouble;
import java.util.regex.Matcher;

/**
 * The current health a mob's name tag shows, in the trailing forms {@link StackName#HEALTH} strips: "Viper 75⺛"
 * (ManaCube), "Mana Wolf ❤ 20", "Mana Wolf [12.5/40]". A leading stack count ("3x Tiger") is stripped first, so it
 * is never read as health; a tag with no health is empty. Pure: no Minecraft/Fabric imports.
 */
public final class TagHealth {
	private TagHealth() {}

	public static OptionalDouble parse(String rawTagLine) {
		if (rawTagLine == null) return OptionalDouble.empty();
		String s = StackName.FORMATTING.matcher(rawTagLine).replaceAll("").trim();
		Matcher stack = StackName.STACK_PREFIX.matcher(s);
		if (stack.matches()) s = stack.group(3);
		Matcher m = StackName.HEALTH.matcher(s);
		if (!m.find()) return OptionalDouble.empty();
		String n = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
		return n == null ? OptionalDouble.empty() : OptionalDouble.of(Double.parseDouble(n.replace(",", "")));
	}
}
