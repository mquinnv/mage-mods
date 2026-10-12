package net.mage.cubewheel.tracker.local;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ManaCube's custom fishing announces every catch in chat (real capture 2026-09-30/10-01: the vanilla
 * bobber never bites, so this line is the only catch signal): "You caught a 52.2cm Common Flounder",
 * "You caught a 299.1cm Rare DiamondAngler". The word after the size is the rarity; the rest is the
 * species, often CamelCase ("BlueSeashroom"). A Legendary catch is announced to the whole server instead,
 * by name, and no "You caught" line follows (capture 2026-10-12: "Qualan has fished a 142.3cm Legendary
 * Manaray!" was the only line for Michael's catch); that line is yours when it names you. Player chat and
 * anything else is ignored. Pure: no Minecraft/Fabric imports.
 */
public final class FishCatchParser {
	/** One catch; {@code sizeCm} is the announced length. */
	public record Catch(String species, String rarity, double sizeCm) {}

	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	/** Player chat on ManaCube: "§r" then "[Rank] Name: " (as {@code McmmoParser}). */
	private static final Pattern PLAYER_CHAT = Pattern.compile("^§r[^:]{0,64}:\\s");
	private static final Pattern CATCH = Pattern.compile(
			"^You caught an? (\\d[\\d,]*(?:\\.\\d+)?) ?cm ([A-Za-z]+) ([A-Za-z][A-Za-z' -]{0,60})$",
			Pattern.CASE_INSENSITIVE);
	/** The server-wide announcement of a rare catch: "Qualan has fished a 142.3cm Legendary Manaray!". */
	private static final Pattern ANNOUNCED = Pattern.compile(
			"^(?<player>[A-Za-z0-9_]{1,32}) has fished an? (\\d[\\d,]*(?:\\.\\d+)?) ?cm ([A-Za-z]+) ([A-Za-z][A-Za-z' -]{0,60}?)!*$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern NOT_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");

	private FishCatchParser() {}

	/** As {@link #parse(String, String)} with no name known: only "You caught" lines. */
	public static Optional<Catch> parse(String raw) {
		return parse(raw, null);
	}

	/**
	 * The catch a chat message announces as yours (legacy § codes and extra spaces allowed): a "You caught"
	 * line, or the server-wide "… has fished a …" line when it names {@code self} (may be null). Empty for
	 * another player's catch or anything else.
	 */
	public static Optional<Catch> parse(String raw, String self) {
		if (raw == null || raw.isEmpty() || raw.length() > 256 || PLAYER_CHAT.matcher(raw).find()) return Optional.empty();
		String line = WHITESPACE.matcher(FORMATTING.matcher(raw).replaceAll("")).replaceAll(" ").trim();
		Matcher m = CATCH.matcher(line);
		int size = 1, rarity = 2, species = 3;
		if (!m.matches()) {
			m = ANNOUNCED.matcher(line);
			if (!m.matches() || self == null || self.isBlank() || !m.group("player").equalsIgnoreCase(self.trim())) {
				return Optional.empty();
			}
			size = 2;
			rarity = 3;
			species = 4;
		}
		double cm;
		try {
			cm = Double.parseDouble(m.group(size).replace(",", ""));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
		String name = m.group(species).trim();
		if (name.isEmpty()) return Optional.empty();
		return Optional.of(new Catch(name, m.group(rarity), cm));
	}

	/**
	 * A species name as a comparable key: letters and digits only, lower case, singular. "YellowSeaShroom",
	 * "Yellow Sea Shrooms" and "yellow sea shroom" are all "yellowseashroom".
	 */
	public static String speciesKey(String name) {
		if (name == null) return "";
		String compact = NOT_ALNUM.matcher(name).replaceAll("").toLowerCase(Locale.ROOT);
		return compact.isEmpty() ? "" : Singular.word(compact);
	}
}
