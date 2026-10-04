package net.mage.cubewheel.tracker.local;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finishing a quest prints (real game log, 2026-10-03) "Congratulations! You have completed the Volcano Potion
 * Quest!" to you, then broadcasts "㘓 Qualan Finished the Volcano Potion Quest!" to everyone. Only the first
 * counts: it is addressed to you, so other players' quests never count and yours is not counted twice. A
 * party quest's "Complete the Volcano Potion Quest" objective is then done. Pure: no Minecraft/Fabric imports.
 */
public final class QuestCompleted {
	private static final Pattern FORMATTING = Pattern.compile("§.");
	/** Player chat on ManaCube: "§r" then "[Rank] Name: " (as {@link FishCatchParser}). */
	private static final Pattern PLAYER_CHAT = Pattern.compile("^§r[^:]{0,64}:\\s");
	/** Resource-pack glyphs ("㘓", private-use icons): anything but Latin letters, digits, spaces and ASCII punctuation. */
	private static final Pattern GLYPHS = Pattern.compile("[^\\p{IsLatin}\\p{N}\\s\\p{Punct}]");
	private static final Pattern COMPLETED = Pattern.compile(
			"^(?:congratulations\\s*!?\\s*)?you have completed the\\s+(.+?)[\\s!.]*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern OBJECTIVE = Pattern.compile("^complete the\\s+(.+?)[\\s!.]*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]");

	private QuestCompleted() {}

	/** The quest a "You have completed the …!" line names (§ codes and glyphs allowed); empty otherwise. */
	public static Optional<String> parse(String raw) {
		if (raw == null || raw.isEmpty() || raw.length() > 256 || PLAYER_CHAT.matcher(raw).find()) return Optional.empty();
		Matcher m = COMPLETED.matcher(clean(raw));
		if (!m.matches()) return Optional.empty();
		String name = m.group(1).trim();
		return WORD.matcher(name).find() ? Optional.of(name) : Optional.empty();
	}

	/**
	 * Is {@code objective} the "Complete the …" step of {@code quest}? Case-insensitive, ignoring § codes,
	 * glyphs, extra spaces and a trailing "!" or ".".
	 */
	public static boolean matches(String objective, String quest) {
		if (objective == null || quest == null) return false;
		Matcher m = OBJECTIVE.matcher(clean(objective));
		if (!m.matches()) return false;
		String want = key(quest);
		return !want.isEmpty() && key(m.group(1)).equals(want);
	}

	/** § codes and glyphs removed, spaces collapsed, trimmed. */
	private static String clean(String s) {
		String out = FORMATTING.matcher(s).replaceAll("");
		return GLYPHS.matcher(out).replaceAll("").replaceAll("\\s+", " ").trim();
	}

	/** A quest name as a comparable key: cleaned, lower case, without trailing "!"/".". */
	private static String key(String name) {
		return clean(name).replaceAll("[\\s!.]+$", "").toLowerCase(Locale.ROOT);
	}
}
