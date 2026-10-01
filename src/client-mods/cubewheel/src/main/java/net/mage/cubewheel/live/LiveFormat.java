package net.mage.cubewheel.live;

/** Short durations for wheel labels ("13h", "2m", "3d"). Pure: no Minecraft/Fabric imports. */
public final class LiveFormat {
	private LiveFormat() {}

	/** Largest whole unit, rounded down: "3d" (from 48 h), "13h", "2m", "<1m". Negative counts as 0. */
	public static String compact(long ms) {
		long m = Math.max(0, ms) / 60_000;
		if (m >= 2 * 24 * 60) return (m / (24 * 60)) + "d";
		if (m >= 60) return (m / 60) + "h";
		if (m >= 1) return m + "m";
		return "<1m";
	}
}
