package net.mage.cubewheel.tracker;

/** One tracked progress item. Pure: no Minecraft/Fabric imports. */
public record Trackable(String id, String source, String name, double current, double max, long seenAt) {
	public double fraction() {
		if (max <= 0) return 0;
		return Math.max(0, Math.min(1, current / max));
	}

	public boolean complete() {
		return max > 0 && current >= max;
	}

	public static String idOf(String source, String name) {
		return source + ":" + name;
	}
}
