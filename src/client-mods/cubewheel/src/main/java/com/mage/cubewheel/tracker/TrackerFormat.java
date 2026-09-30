package com.mage.cubewheel.tracker;

import java.util.Locale;

/** Text formatting for tracker HUD lines. Pure: no Minecraft/Fabric imports. */
public final class TrackerFormat {
	private TrackerFormat() {}

	public static String line(Trackable t, long now) {
		long pct = Math.round(t.fraction() * 100);
		if (!t.complete()) pct = Math.min(pct, 99);
		return t.name() + "  " + fmt(t.current()) + " / " + fmt(t.max())
			+ " (" + pct + "%) \u00b7 " + age(now - t.seenAt());
	}

	public static String age(long ms) {
		if (ms < 60_000) return "now";
		if (ms < 3_600_000L) return (ms / 60_000) + "m";
		if (ms < 86_400_000L) return (ms / 3_600_000L) + "h";
		return (ms / 86_400_000L) + "d";
	}

	private static String fmt(double v) {
		if (v == Math.rint(v)) return String.format(Locale.ROOT, "%,d", Math.round(v));
		return String.format(Locale.ROOT, "%,.1f", v);
	}
}
