package com.mage.cubewheel.boosters;

import com.mage.cubewheel.hud.Durations;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises booster chat messages. Formats (from a study of another mod; UNVERIFIED on Survival, confirm with
 * capture mode):
 * <ul>
 *   <li>"You have received a 2x Sell Boost for 30m"</li>
 *   <li>"Your 2x Sell Boost( booster) has been extended from 5m 10s to 35m 10s"</li>
 *   <li>"Your 2x Sell Boost has expired/ended" (a guess; harmless if never sent)</li>
 * </ul>
 * Tolerant of case, colour codes, "a"/"an", "Booster", a leading "[Tag]" and h/m/s or word units, but the
 * message must start with the phrase, so player chat ("§r [Rank] Name: You have received ...", "Survival
 * [SHOUT] Name: ...") never matches. Pure: no Minecraft/Fabric imports.
 */
public final class BoosterParser {
	private BoosterParser() {}

	public enum Kind { RECEIVED, EXTENDED, ENDED }

	/** A parsed message; {@code durationMs} is the remaining time (0 for ENDED). */
	public record Message(Kind kind, double multiplier, String type, long durationMs) {}

	private static final Pattern FORMATTING = Pattern.compile("§.");
	/** Private-use glyphs (ManaCube's rank/icon font) and other invisible prefix junk. */
	private static final Pattern GLYPHS = Pattern.compile("[\\uE000-\\uF8FF\\u200B-\\u200D\\uFEFF]");
	private static final String TAG = "(?:\\[[^\\]]{1,24}\\]\\s*)?";
	private static final String BOOST = "(\\d+(?:\\.\\d+)?)x\\s+([a-z][a-z ]{0,24}?)\\s+boost(?:er)?(?:\\s+booster)?";
	private static final String END = "\\s*[.!]*\\s*$";
	private static final Pattern RECEIVED = Pattern.compile(
			"^" + TAG + "you\\s+(?:have\\s+)?received\\s+(?:an?\\s+)?" + BOOST + "\\s+for\\s+(.+?)" + END,
			Pattern.CASE_INSENSITIVE);
	private static final Pattern EXTENDED = Pattern.compile(
			"^" + TAG + "your\\s+" + BOOST + "\\s+has\\s+been\\s+extended\\s+from\\s+.+?\\s+to\\s+(.+?)" + END,
			Pattern.CASE_INSENSITIVE);
	private static final Pattern ENDED = Pattern.compile(
			"^" + TAG + "your\\s+" + BOOST + "\\s+has\\s+(?:expired|ended|run\\s+out|worn\\s+off)" + END,
			Pattern.CASE_INSENSITIVE);
	/** "[Rank] [Title] *Name: " after colour codes and glyphs are removed. */
	private static final Pattern PLAYER_PREFIX = Pattern.compile(
			"^[^\\p{L}\\p{N}\\[*]{0,8}(?:\\[[^\\]]{0,24}\\]\\s*)*\\*?[A-Za-z0-9_]{1,16}:\\s");

	/** Parses a chat message's plain text (Component.getString()); empty for anything else. */
	public static Optional<Message> parse(String raw) {
		if (raw == null || raw.isBlank() || isPlayerChat(raw)) return Optional.empty();
		String s = clean(raw);
		Matcher m = RECEIVED.matcher(s);
		if (m.find()) return timed(Kind.RECEIVED, m);
		m = EXTENDED.matcher(s);
		if (m.find()) return timed(Kind.EXTENDED, m);
		m = ENDED.matcher(s);
		if (m.find()) return Optional.of(new Message(Kind.ENDED, Double.parseDouble(m.group(1)), m.group(2).trim(), 0));
		return Optional.empty();
	}

	/**
	 * True for player chat as ManaCube shows it: lines starting with "§r" (every captured player line does),
	 * "[SHOUT]" cross-server chat, or a "[Rank] Name: " prefix.
	 */
	public static boolean isPlayerChat(String raw) {
		if (raw == null) return false;
		if (raw.stripLeading().startsWith("§r")) return true;
		if (raw.contains("[SHOUT]")) return true;
		return PLAYER_PREFIX.matcher(clean(raw)).find();
	}

	private static String clean(String raw) {
		String s = FORMATTING.matcher(raw).replaceAll("");
		return GLYPHS.matcher(s).replaceAll("").trim();
	}

	private static Optional<Message> timed(Kind kind, Matcher m) {
		OptionalLong d = Durations.parse(m.group(3));
		if (d.isEmpty() || d.getAsLong() <= 0) return Optional.empty();
		return Optional.of(new Message(kind, Double.parseDouble(m.group(1)), m.group(2).trim(), d.getAsLong()));
	}
}
