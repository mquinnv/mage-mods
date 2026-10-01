package net.mage.cubewheel.tracker;

import java.util.Optional;

/**
 * ManaCube item rarities and their colours, as the server draws them (catch messages, 2026-09-30:
 * "You caught a 183.2cm Common Salmon" in #87D473, Uncommon #42D8D4, Rare #4271D8). Job objectives mark the
 * rarity with a resource-pack icon glyph before the name ("Catch 1/3 \uF895 Goldfish while fishing").
 * Pure: no Minecraft/Fabric imports.
 */
public enum Rarity {
	COMMON('\uF895', 0xFF87D473),
	UNCOMMON('\uF894', 0xFF42D8D4),
	RARE('\uF893', 0xFF4271D8),
	/**
	 * Fishing Log order is Common, Uncommon, Rare, Legendary (user, 2026-09-30); chat shows it red (user); the shade follows the
	 * pattern of the other three and the glyph continues the sequence. A captured Legendary catch line will confirm both.
	 */
	LEGENDARY('\uF892', 0xFFD84242);

	/** The icon glyph ManaCube's resource pack draws for this rarity. */
	public final char glyph;
	/** The rarity's colour, opaque ARGB. */
	public final int argb;

	Rarity(char glyph, int argb) {
		this.glyph = glyph;
		this.argb = argb;
	}

	/** The rarity marked by the first rarity glyph in {@code text}, if any. */
	public static Optional<Rarity> inText(String text) {
		if (text == null) return Optional.empty();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			for (Rarity r : values()) {
				if (r.glyph == c) return Optional.of(r);
			}
		}
		return Optional.empty();
	}
}
