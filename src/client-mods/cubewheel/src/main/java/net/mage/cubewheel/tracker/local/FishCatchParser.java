package net.mage.cubewheel.tracker.local;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ManaCube's custom fishing announces every catch in chat (real capture 2026-09-30/10-01: the vanilla
 * bobber never bites, so this line is the only catch signal): "You caught a 52.2cm Common Flounder",
 * "You caught a 299.1cm Rare DiamondAngler". The word after the size is the rarity; the rest is the
 * species, often CamelCase ("BlueSeashroom"). Player chat and anything else is ignored. Pure: no
 * Minecraft/Fabric imports.
 */
public final class FishCatchParser {
	/** One catch; {@code sizeCm} is the announced length. */
	public record Catch(String species, String rarity, double sizeCm) {}

	private static final Pattern FORMATTING = Pattern.compile("§.");
	/** Player chat on ManaCube: "§r" then "[Rank] Name: " (as {@code McmmoParser}). */
	private static final Pattern PLAYER_CHAT = Pattern.compile("^§r[^:]{0,64}:\\s");
	private static final Pattern CATCH = Pattern.compile(
			"^You caught an? (\\d[\\d,]*(?:\\.\\d+)?) ?cm ([A-Za-z]+) ([A-Za-z][A-Za-z' -]{0,60})$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern NOT_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");

	private FishCatchParser() {}

	/** The catch a chat message announces (legacy § codes and extra spaces allowed); empty otherwise. */
	public static Optional<Catch> parse(String raw) {
		if (raw == null || raw.isEmpty() || raw.length() > 256 || PLAYER_CHAT.matcher(raw).find()) return Optional.empty();
		String line = FORMATTING.matcher(raw).replaceAll("").replaceAll("\\s+", " ").trim();
		Matcher m = CATCH.matcher(line);
		if (!m.matches()) return Optional.empty();
		double size;
		try {
			size = Double.parseDouble(m.group(1).replace(",", ""));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
		String species = m.group(3).trim();
		if (species.isEmpty()) return Optional.empty();
		return Optional.of(new Catch(species, m.group(2), size));
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
