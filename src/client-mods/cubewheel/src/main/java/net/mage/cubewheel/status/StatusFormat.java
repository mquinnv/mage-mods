package net.mage.cubewheel.status;

import java.util.Locale;

/** Text for the Status panel. Pure: no Minecraft/Fabric imports. */
public final class StatusFormat {
	private StatusFormat() {}

	/** Overworld clock ticks to "3:00 PM" (tick 0 is 6:00 AM). */
	public static String gameTime(long ticks) {
		long t = Math.floorMod(ticks, 24_000L);
		int minutes = (int) ((t * 60 / 1000 + 6 * 60) % (24 * 60));
		return clock(minutes / 60, minutes % 60);
	}

	/** 17, 9 -> "5:09 PM". */
	public static String clock(int hour, int minute) {
		int h = hour % 12 == 0 ? 12 : hour % 12;
		return String.format(Locale.ROOT, "%d:%02d %s", h, minute, hour < 12 ? "AM" : "PM");
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

	/** Facing as "N", "E", "S", "W" with its axis: "West -X". */
	public static String facing(String direction) {
		if (direction == null) return "";
		return switch (direction.toLowerCase(Locale.ROOT)) {
			case "north" -> "North -Z";
			case "south" -> "South +Z";
			case "east" -> "East +X";
			case "west" -> "West -X";
			default -> direction;
		};
	}

	/** Blocks per second with two decimals: "4.32 m/s". */
	public static String speed(double blocksPerSecond) {
		return String.format(Locale.ROOT, "%.2f m/s", Math.max(0, blocksPerSecond));
	}
}
