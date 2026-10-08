package net.mage.cubewheel.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.mage.cubewheel.hud.Panel;

/** Text and colours for the Status panel. Pure: no Minecraft/Fabric imports. */
public final class StatusFormat {
	/** An armor piece's durability bar with plenty left. */
	public static final int WORN_OK = 0xFF55FF55;
	/** Under 25% left. */
	public static final int WORN_LOW = 0xFFFFFF55;
	/** Under 10% left. */
	public static final int WORN_OUT = 0xFFFF5555;

	/** Separators in a biome id's path. */
	private static final Pattern BIOME_SEPARATOR = Pattern.compile("[_/]");
	/** Between the effects of the set bonus row. */
	private static final String EFFECT_DOT = "·";

	private StatusFormat() {}

	/**
	 * The set bonus row: each effect of {@code bonus} (see {@link ArmorSet#effects}) as its own piece, a dot piece
	 * between them. While the bonus is {@code on}, an effect is {@code lit} unless it only applies in the Resource
	 * World ({@link ArmorSet#resourceWorldOnly}) and you are not there ({@code inResourceWorld}): then it is
	 * {@code dim}. A bonus that is not on is dim throughout. Empty for an empty bonus.
	 */
	public static List<Panel.Piece> bonusPieces(String bonus, boolean on, boolean inResourceWorld, int lit, int dim) {
		List<Panel.Piece> out = new ArrayList<>();
		for (String effect : ArmorSet.effects(bonus)) {
			if (!out.isEmpty()) out.add(new Panel.Piece(null, EFFECT_DOT, on ? lit : dim));
			boolean applies = on && (inResourceWorld || !ArmorSet.resourceWorldOnly(effect));
			out.add(new Panel.Piece(null, effect, applies ? lit : dim));
		}
		return out;
	}

	/** Overworld clock ticks to 24-hour "14:20" (tick 0 is 6:00): short, and unlike the real clock beside it. */
	public static String gameTime(long ticks) {
		long t = Math.floorMod(ticks, 24_000L);
		int minutes = (int) ((t * 60 / 1000 + 6 * 60) % (24 * 60));
		return (minutes / 60) + ":" + twoDigits(minutes % 60);
	}

	/** The real clock, compact: 16, 35 -> "4:35p". */
	public static String clock(int hour, int minute) {
		int h = hour % 12 == 0 ? 12 : hour % 12;
		return h + ":" + twoDigits(minute) + (hour < 12 ? "a" : "p");
	}

	/** As {@code %02d} formats it: at least two characters, zero-padded ("05", "42", "-5"). */
	static String twoDigits(int v) {
		return v >= 0 && v < 10 ? "0" + v : Integer.toString(v);
	}

	/** Block coordinates as plain numbers: "123  64  -456". */
	public static String coords(int x, int y, int z) {
		return x + "  " + y + "  " + z;
	}

	/** "minecraft:dark_forest" -> "Dark Forest". */
	public static String biome(String id) {
		if (id == null || id.isBlank()) return "";
		String path = id.substring(id.indexOf(':') + 1);
		StringBuilder out = new StringBuilder();
		for (String w : BIOME_SEPARATOR.split(path)) {
			if (w.isEmpty()) continue;
			if (out.length() > 0) out.append(' ');
			out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return out.toString();
	}

	/** Facing as its letter and the axis it points along: "west" -> "W -X". */
	public static String facing(String direction) {
		if (direction == null) return "";
		return switch (direction.toLowerCase(Locale.ROOT)) {
			case "north" -> "N -Z";
			case "south" -> "S +Z";
			case "east" -> "E +X";
			case "west" -> "W -X";
			default -> direction;
		};
	}

	/** Blocks per second with one decimal: "4.3 b/s". */
	public static String speed(double blocksPerSecond) {
		// The speed changes once a tick, the panel asks more often: keep the last text.
		double v = Math.max(0, blocksPerSecond);
		Speed last = lastSpeed;
		if (Double.compare(last.value(), v) == 0) return last.text();
		String text = String.format(Locale.ROOT, "%.1f b/s", v);
		lastSpeed = new Speed(v, text);
		return text;
	}

	private record Speed(double value, String text) {}

	private static volatile Speed lastSpeed = new Speed(0, "0.0 b/s");

	/** Below this fraction of durability left a piece is wearing out (yellow; its bar shows). */
	public static final double WEAR_LOW = 0.25;

	/** A durability bar's colour for the fraction {@code remaining}: red under 10%, yellow under 25%, else green. */
	public static int wearColor(double remaining) {
		return remaining < 0.10 ? WORN_OUT : remaining < WEAR_LOW ? WORN_LOW : WORN_OK;
	}

	/**
	 * The wear bar for an item with {@code remaining} durability (0..1; negative = it takes no damage): shown only as
	 * a warning, while it wears out (under {@link #WEAR_LOW}), and never for an unbreakable item, which ManaCube
	 * gear says in its lore while still reporting durability (Michael 2026-10-04). Negative = no bar.
	 */
	public static double wear(double remaining, boolean unbreakable) {
		return remaining < 0 || remaining >= WEAR_LOW || unbreakable ? -1 : remaining;
	}

	/** True when a lore line says "Unbreakable", as ManaCube gear does (lines as Component#getString gives them). */
	public static boolean unbreakableLore(java.util.List<String> lore) {
		if (lore == null) return false;
		for (String line : lore) {
			if (line != null && line.toLowerCase(Locale.ROOT).contains("unbreakable")) return true;
		}
		return false;
	}
}
