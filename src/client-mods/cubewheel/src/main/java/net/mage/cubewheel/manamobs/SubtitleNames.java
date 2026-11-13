package net.mage.cubewheel.manamobs;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure text rules for renaming a vanilla entity subtitle to a mana mob's name. */
public final class SubtitleNames {
	private static final String PREFIX = "subtitles.entity.";
	/** Entity subtitle groups that are never a mob standing in a model. */
	private static final Set<String> NOT_MOBS = Set.of("player", "experience_orb", "item", "generic", "armor_stand");
	private static final Pattern TYPE = Pattern.compile("[a-z0-9_]+");

	private SubtitleNames() {}

	/**
	 * Replaces the first whole-word {@code vanillaName} in {@code translated} with {@code mobName}
	 * ("Polar Bear hurts" becomes "Mana Wolf hurts"); when the vanilla name is not a word there, prefixes it
	 * ("Footsteps" becomes "Mana Wolf: Footsteps").
	 */
	public static String rename(String translated, String vanillaName, String mobName) {
		if (!vanillaName.isEmpty()) {
			Matcher m = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(vanillaName) + "(?![\\p{L}\\p{N}_])")
					.matcher(translated);
			if (m.find()) return translated.substring(0, m.start()) + mobName + translated.substring(m.end());
		}
		return mobName + ": " + translated;
	}

	/** {@code "subtitles.entity.wolf.growl"} gives {@code minecraft:wolf}; empty for any other key or a non-mob group. */
	public static Optional<String> entityType(String subtitleKey) {
		if (subtitleKey == null || !subtitleKey.startsWith(PREFIX)) return Optional.empty();
		String rest = subtitleKey.substring(PREFIX.length());
		int dot = rest.indexOf('.');
		if (dot <= 0 || dot == rest.length() - 1) return Optional.empty();
		String type = rest.substring(0, dot);
		if (!TYPE.matcher(type).matches() || NOT_MOBS.contains(type)) return Optional.empty();
		return Optional.of("minecraft:" + type);
	}
}
