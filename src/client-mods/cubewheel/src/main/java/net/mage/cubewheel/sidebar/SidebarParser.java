package net.mage.cubewheel.sidebar;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads "Key: value" lines of the scoreboard sidebar ("Money: $2.89M", "Skills: Lvl 1851") into
 * numbers. Pure: no Minecraft/Fabric imports.
 */
public final class SidebarParser {
	/** Legacy formatting codes; ManaCube also pads lines with them to make entries unique. */
	private static final Pattern FORMATTING = Pattern.compile("§.");
	/** Key: Latin words (icon glyphs before it are not letters, so they fall away); value: the rest. */
	private static final Pattern KEY_VALUE = Pattern.compile("([A-Za-z][A-Za-z ]*?)\\s*:\\s*(.*)$");
	/**
	 * First number in the value, with an optional magnitude suffix touching it. A number touching a ':' is
	 * part of a clock time ("12:30") and is not a value.
	 */
	private static final Pattern NUMBER = Pattern.compile("(?<![\\d.,:])(\\d[\\d,]*(?:\\.\\d+)?)([kKmMbBtT])?(?![A-Za-z\\d:])");

	private SidebarParser() {}

	/** Key -> value in line order; the first line wins for a repeated key. Lines without a number are skipped. */
	public static Map<String, Double> parse(List<String> lines) {
		Map<String, Double> out = new LinkedHashMap<>();
		if (lines == null) return out;
		for (String raw : lines) {
			if (raw == null) continue;
			String line = foldSmallCaps(FORMATTING.matcher(raw).replaceAll(""));
			Matcher m = KEY_VALUE.matcher(line);
			if (!m.find()) continue;
			String key = m.group(1).trim();
			if (key.isEmpty() || out.containsKey(key)) continue;
			OptionalDouble v = parseValue(m.group(2));
			if (v.isPresent()) out.put(key, v.getAsDouble());
		}
		return out;
	}

	/** ManaCube's small-caps letters, a to z (there is no small-caps x; a plain one stands in). */
	private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

	/**
	 * Michael 2026-11-06 capture: Skyblock's sidebar labels are small caps ("⚒ ꜱᴋɪʟʟꜱ: Lvl 50") where Survival's are
	 * plain ("Skills: Lvl 4102"). A run of small-caps letters becomes a capitalised word ("Skills"), so both read the
	 * same and {@code tracker.sidebarLinks} works on either. Plain letters are left alone.
	 */
	static String foldSmallCaps(String s) {
		StringBuilder b = null;
		boolean inWord = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			int k = c == 'x' ? -1 : SMALL_CAPS.indexOf(c);
			if (k < 0) {
				inWord = Character.isLetter(c);
				continue;
			}
			if (b == null) b = new StringBuilder(s);
			char plain = (char) ('a' + k);
			b.setCharAt(i, inWord ? plain : Character.toUpperCase(plain));
			inWord = true;
		}
		return b == null ? s : b.toString();
	}

	/** "$2.89M" -> 2890000, "Lvl 1851" -> 1851, "18,975" -> 18975; empty when there is no number. */
	public static OptionalDouble parseValue(String value) {
		if (value == null) return OptionalDouble.empty();
		Matcher m = NUMBER.matcher(value);
		if (!m.find()) return OptionalDouble.empty();
		try {
			BigDecimal n = new BigDecimal(m.group(1).replace(",", ""));
			if (m.group(2) != null) {
				n = n.movePointRight(switch (m.group(2).toLowerCase(Locale.ROOT)) {
					case "k" -> 3;
					case "m" -> 6;
					case "b" -> 9;
					default -> 12; // t
				});
			}
			return OptionalDouble.of(n.doubleValue());
		} catch (NumberFormatException e) {
			return OptionalDouble.empty();
		}
	}
}
