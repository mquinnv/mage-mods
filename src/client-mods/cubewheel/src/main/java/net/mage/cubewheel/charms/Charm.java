package net.mage.cubewheel.charms;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An amulet/pendant or talisman in your inventory, boiled down to an icon and a few characters from its own lore:
 * the Undead Amulet ("Deal +50% damage to all Undead Monsters") is a zombie head and "+50%", the Farming Talisman
 * ("Automatically sells harvested crops", "Total earned: $217,633.69") a hoe and "$217k". Nothing is hard-coded per
 * item, so new ones read the same way. Pure: no Minecraft/Fabric imports.
 */
public record Charm(Kind kind, String icon, String text) {
	public enum Kind {
		/** Worn in the bottom-right inventory slot ("Equip a Pendant by placing in the bottom right slot"). */
		AMULET,
		/** Works from anywhere in the inventory. */
		TALISMAN
	}

	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern PERCENT = Pattern.compile("([+-]\\d+(?:\\.\\d+)?%)");
	private static final Pattern EARNED = Pattern.compile("(?i)total earned:\\s*\\$([\\d,]+(?:\\.\\d+)?)");
	private static final Pattern EFFECT = Pattern.compile("➟\\s*(.+)");

	public static Optional<Charm> parse(String name, List<String> lore) {
		if (name == null) return Optional.empty();
		String n = clean(name).toLowerCase(Locale.ROOT);
		StringBuilder all = new StringBuilder();
		String effect = null;
		if (lore != null) for (String l : lore) {
			String c = clean(l);
			all.append(' ').append(c);
			Matcher e = EFFECT.matcher(c);
			if (effect == null && e.find()) effect = e.group(1).trim();
		}
		String text = all.toString().toLowerCase(Locale.ROOT);
		if (n.contains("talisman")) return Optional.of(talisman(text, all.toString()));
		if (n.contains("amulet") || n.contains("pendant") || text.contains("equip a pendant")) {
			return Optional.of(amulet(text, effect));
		}
		return Optional.empty();
	}

	private static Charm talisman(String lower, String raw) {
		// What it sells, as the thing itself (Michael 2026-10-03: crops, mobs, souls read better than tools).
		String icon = lower.contains("soul") ? "minecraft:soul_lantern"
				: lower.contains("crop") || lower.contains("harvest") || lower.contains("farm") ? "minecraft:wheat"
				: lower.contains("fish") ? "minecraft:cod"
				: lower.contains("mob") || lower.contains("drop") || lower.contains("loot") || lower.contains("hunt") ? "minecraft:zombie_head"
				: "minecraft:emerald";
		Matcher m = EARNED.matcher(raw);
		String earned = m.find() ? money(Double.parseDouble(m.group(1).replace(",", ""))) : "";
		return new Charm(Kind.TALISMAN, icon, "$" + earned);
	}

	private static Charm amulet(String lower, String effect) {
		Matcher p = PERCENT.matcher(lower);
		if (p.find()) {
			String icon = lower.contains("undead") ? "minecraft:zombie_head"
					: lower.contains("take") ? "minecraft:shield"
					: "minecraft:iron_sword";
			return new Charm(Kind.AMULET, icon, p.group(1));
		}
		if (lower.contains("invisible")) return new Charm(Kind.AMULET, "minecraft:ender_eye", "invis");
		String t = effect == null ? "?" : effect.length() > 8 ? effect.substring(0, 8) + "…" : effect;
		return new Charm(Kind.AMULET, "minecraft:amethyst_shard", t);
	}

	/** 950 -> "950", 217633.69 -> "217k", 1234567 -> "1.2M". */
	static String money(double v) {
		if (v >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", v / 1_000_000);
		if (v >= 1_000) return (long) (v / 1_000) + "k";
		return String.valueOf((long) v);
	}

	private static String clean(String s) {
		return s == null ? "" : FORMATTING.matcher(s).replaceAll("").trim();
	}
}
