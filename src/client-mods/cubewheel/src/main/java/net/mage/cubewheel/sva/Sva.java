package net.mage.cubewheel.sva;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One Survival SVA from ManaCube's catalog. Raw strings keep their legacy codes (see {@link LegacyText});
 * the plain/normalized forms are precomputed so filtering and tooltip look-ups stay cheap. Pure.
 *
 * @param itemType     the API's key; owned entries join on it
 * @param itemId       vanilla item path from {@code material} ("carved_pumpkin"), "" if absent
 * @param itemModel    resource-pack model id ("manalabs:crates/viking/viking_helmet") or null
 * @param leatherColor RGB or -1
 */
public record Sva(String itemType, int version, String itemId, String displayName, String plainName, String slot,
		int circulation, String itemModel, List<String> lore, List<String> plainLore, Map<String, Integer> enchants,
		int leatherColor, String searchText) {

	static Sva of(String itemType, int version, String material, String displayName, String slot, int circulation,
			String itemModel, List<String> lore, Map<String, Integer> enchants, int leatherColor) {
		String plainName = LegacyText.strip(displayName).replaceAll("\\s+", " ").trim();
		List<String> plainLore = lore.stream().map(LegacyText::strip).toList();
		StringBuilder search = new StringBuilder(plainName.toLowerCase(Locale.ROOT));
		for (String l : plainLore) search.append('\n').append(l.toLowerCase(Locale.ROOT));
		String itemId = material == null ? "" : material.trim().toLowerCase(Locale.ROOT);
		return new Sva(itemType, version, itemId, displayName, plainName, slot, circulation, itemModel,
				List.copyOf(lore), plainLore, java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(enchants)), leatherColor, search.toString());
	}

	/** Key for alphabetical order. */
	public String sortKey() {
		return plainName.toLowerCase(Locale.ROOT) + "\u0000" + itemType;
	}

	/** True if every whitespace-separated word of the lower-cased query occurs in the name or lore. */
	public boolean matches(String lowerQuery) {
		if (lowerQuery == null || lowerQuery.isBlank()) return true;
		for (String word : lowerQuery.trim().split("\\s+")) {
			if (!searchText.contains(word)) return false;
		}
		return true;
	}

	/**
	 * The name for {@code /ah search}: decorative symbols (☀ ▲ ☠ …) dropped, letters, digits, spaces and
	 * {@code ' - + .} kept.
	 */
	public String ahQuery() {
		String kept = plainName.replaceAll("[^\\p{L}\\p{N} '\\-+.]", " ");
		return kept.replaceAll("\\s+", " ").trim();
	}
}
