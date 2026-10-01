package net.mage.cubewheel.hud;

import java.util.List;

/**
 * One small HUD panel: a gold title and coloured lines, drawn at a corner + offset (see {@link HudLayout}).
 * Pure: no Minecraft/Fabric imports.
 */
public record Panel(String title, List<Line> lines, HudLayout.Corner corner, int x, int y) {
	public static final int WHITE = 0xFFFFFFFF;
	public static final int YELLOW = 0xFFFFFF55;
	public static final int GRAY = 0xFFAAAAAA;
	public static final int GREEN = 0xFF55FF55;

	/** How opaque a full progress bar behind a row gets (text stays readable on top). */
	static final int METER_ALPHA = 0x80;

	/**
	 * The colour of a row's progress bar at {@code fraction}: it heats up from clear through yellow (half way) to red
	 * (nearly there), and is green once done (1).
	 */
	public static int meterColor(double fraction) {
		double f = Math.max(0, Math.min(1, fraction));
		if (f >= 1) return argb(METER_ALPHA, 0x55, 0xFF, 0x55);
		if (f < 0.5) {
			double t = f / 0.5; // clear -> yellow
			return argb((int) Math.round(METER_ALPHA * t), 0xFF, 0xDD, 0x33);
		}
		double t = (f - 0.5) / 0.5; // yellow -> red
		return argb(METER_ALPHA, 0xFF, (int) Math.round(0xDD + (0x33 - 0xDD) * t), 0x33);
	}

	private static int argb(int a, int r, int g, int b) {
		return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
	}

	/**
	 * A row: an optional short {@code tag} in its own coloured column, the {@code text}, and an optional
	 * {@code right} part aligned to the panel's right edge (so counts line up in a proportional font).
	 */
	public record Line(String tag, int tagColor, String text, int color, String right, int rightColor, int accent,
			Object icon, double progress) {
		/** No accent bar, no icon, no meter. */
		public Line(String tag, int tagColor, String text, int color, String right, int rightColor) {
			this(tag, tagColor, text, color, right, rightColor, 0, null, -1);
		}

		/** This line with a bar in {@code argb} along the panel's left edge (0 = none). */
		public Line withAccent(int argb) {
			return new Line(tag, tagColor, text, color, right, rightColor, argb, icon, progress);
		}

		/** This line with a small item picture before its text ({@code icon}: an ItemStack; null = none). */
		public Line withIcon(Object icon) {
			return new Line(tag, tagColor, text, color, right, rightColor, accent, icon, progress);
		}

		/** This line with a thin progress meter under it ({@code fraction} 0..1; negative = none). */
		public Line withProgress(double fraction) {
			return new Line(tag, tagColor, text, color, right, rightColor, accent, icon, fraction);
		}

		/** The right part in the text's colour. */
		public Line(String tag, int tagColor, String text, int color, String right) {
			this(tag, tagColor, text, color, right, color);
		}

		public Line(String text, int color) {
			this("", 0, text, color, "");
		}

		/** A name on the left and its value (a countdown) pinned to the panel's right edge. */
		public static Line split(String left, String right, int color) {
			return new Line("", 0, left, color, right);
		}
	}
}
