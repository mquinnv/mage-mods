package net.mage.cubewheel.status;

import java.util.Locale;

/** Text and colours for the Status panel. Pure: no Minecraft/Fabric imports. */
public final class StatusFormat {
	/** An armor piece's durability bar with plenty left. */
	public static final int WORN_OK = 0xFF55FF55;
	/** Under 25% left. */
	public static final int WORN_LOW = 0xFFFFFF55;
	/** Under 10% left. */
	public static final int WORN_OUT = 0xFFFF5555;

	private StatusFormat() {}

	/** Overworld clock ticks to 24-hour "14:20" (tick 0 is 6:00): short, and unlike the real clock beside it. */
	public static String gameTime(long ticks) {
		long t = Math.floorMod(ticks, 24_000L);
		int minutes = (int) ((t * 60 / 1000 + 6 * 60) % (24 * 60));
		return String.format(Locale.ROOT, "%d:%02d", minutes / 60, minutes % 60);
	}

	/** The real clock, compact: 16, 35 -> "4:35p". */
	public static String clock(int hour, int minute) {
		int h = hour % 12 == 0 ? 12 : hour % 12;
		return String.format(Locale.ROOT, "%d:%02d%s", h, minute, hour < 12 ? "a" : "p");
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
		for (String w : path.split("[_/]")) {
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
		return String.format(Locale.ROOT, "%.1f b/s", Math.max(0, blocksPerSecond));
	}

	/** A durability bar's colour for the fraction {@code remaining}: red under 10%, yellow under 25%, else green. */
	/** Below this fraction of durability left a piece is wearing out (yellow; its bar shows). */
	public static final double WEAR_LOW = 0.25;

	public static int wearColor(double remaining) {
		return remaining < 0.10 ? WORN_OUT : remaining < WEAR_LOW ? WORN_LOW : WORN_OK;
	}
}
