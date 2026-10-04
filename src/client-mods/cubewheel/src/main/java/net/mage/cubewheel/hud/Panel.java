package net.mage.cubewheel.hud;

import java.util.List;

/**
 * One small HUD panel: a gold title and coloured lines, drawn at a corner + offset (see {@link HudLayout}), with an
 * optional second column on the right ({@code side}, null = none; the Status panel's armor).
 * Pure: no Minecraft/Fabric imports.
 */
public record Panel(String title, List<Line> lines, HudLayout.Corner corner, int x, int y, Side side) {
	/** A one-column panel. */
	public Panel(String title, List<Line> lines, HudLayout.Corner corner, int x, int y) {
		this(title, lines, corner, x, y, null);
	}

	public static final int WHITE = 0xFFFFFFFF;
	public static final int YELLOW = 0xFFFFFF55;
	public static final int GRAY = 0xFFAAAAAA;
	public static final int GREEN = 0xFF55FF55;
	/** Entries you can work on right here (green is taken by done, yellow by nearly done). */
	public static final int CYAN = 0xFF55FFFF;

	/** How opaque a full progress bar behind a row gets (text stays readable on top). */
	static final int METER_ALPHA = 0x99;

	/**
	 * The colour of a row's progress bar at {@code fraction}: one steady deep teal while under way, so only the bar's
	 * length tells how far along it is, and dark green once done (1). Dark enough for white text on top. (It used to
	 * heat up to red near the goal, which read as a warning — Michael 2026-10-04.)
	 */
	public static int meterColor(double fraction) {
		return fraction >= 1 ? argb(METER_ALPHA, 0x1F, 0x7A, 0x2E) : argb(METER_ALPHA, 0x1A, 0x5E, 0x6E);
	}

	/** Above this fraction a capacity gauge turns yellow. */
	static final double GAUGE_HIGH = 0.8;

	/**
	 * The colour of a capacity gauge (how full the inventory is) at {@code fraction}: green with room, yellow from
	 * {@link #GAUGE_HIGH}, red when full: here filling up is the thing to act on.
	 */
	public static int gaugeColor(double fraction) {
		if (fraction >= 1) return 0xFFFF5555;
		if (fraction >= GAUGE_HIGH) return 0xFFFFDD44;
		return 0xFF55CC55;
	}

	private static int argb(int a, int r, int g, int b) {
		return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
	}

	/**
	 * A second column, {@code width} pixels wide, to the right of a panel's lines past a thin divider (see
	 * {@link TwoColumn}): {@code slots} as rows of a full-size item with a bar beside it, then {@code lines} under them
	 * (their {@code text} and {@code right} parts only).
	 */
	public record Side(int width, List<Slot> slots, List<Line> lines) {}

	/**
	 * One row of a {@link Side}: an item ({@code icon}: an ItemStack; null or empty = an empty slot, drawn dim) and a
	 * bar beside it filled to {@code fraction} (0..1) in {@code color}; a negative fraction draws no bar.
	 */
	public record Slot(Object icon, double fraction, int color) {}

	/** One item picture ({@code icon}: an ItemStack; null = none) and a few characters, in a row of pieces. */
	public record Piece(Object icon, String text, int color) {}

	/**
	 * A row: an optional short {@code tag} in its own coloured column, the {@code text}, and an optional
	 * {@code right} part aligned to the panel's right edge (so counts line up in a proportional font), or a light-level
	 * disc there ({@code light} 0..15, see {@link LightDisc}; -1 = none).
	 */
	public record Line(String tag, int tagColor, String text, int color, String right, int rightColor, int accent,
			Object icon, double progress, List<Piece> pieces, double gauge, int light) {
		/** No accent bar, no icon, no meter. */
		public Line(String tag, int tagColor, String text, int color, String right, int rightColor) {
			this(tag, tagColor, text, color, right, rightColor, 0, null, -1, List.of(), -1, -1);
		}

		/** This line with a bar in {@code argb} along the panel's left edge (0 = none). */
		public Line withAccent(int argb) {
			return new Line(tag, tagColor, text, color, right, rightColor, argb, icon, progress, pieces, gauge, light);
		}

		/** This line with a small item picture before its text ({@code icon}: an ItemStack; null = none). */
		public Line withIcon(Object icon) {
			return new Line(tag, tagColor, text, color, right, rightColor, accent, icon, progress, pieces, gauge, light);
		}

		/** This line with a thin progress meter under it ({@code fraction} 0..1; negative = none). */
		public Line withProgress(double fraction) {
			return new Line(tag, tagColor, text, color, right, rightColor, accent, icon, fraction, pieces, gauge, light);
		}

		/**
		 * This line with a capacity gauge: a thin bar under the text, coloured by how full it is ({@link #gaugeColor};
		 * {@code fraction} 0..1; negative = none).
		 */
		public Line withGauge(double fraction) {
			return new Line(tag, tagColor, text, color, right, rightColor, accent, icon, progress, pieces, fraction, light);
		}

		/** This line with a light-level disc ({@code level} 0..15) pinned to the right edge, in place of a right part. */
		public Line withLight(int level) {
			return new Line(tag, tagColor, text, color, "", rightColor, accent, icon, progress, pieces, gauge,
					Math.max(0, Math.min(15, level)));
		}

		/** A row of small icon + text pieces side by side instead of the text (e.g. the Charms panel's charms). */
		public static Line pieces(List<Piece> pieces) {
			return new Line("", 0, "", WHITE, "", WHITE, 0, null, -1, List.copyOf(pieces), -1, -1);
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
