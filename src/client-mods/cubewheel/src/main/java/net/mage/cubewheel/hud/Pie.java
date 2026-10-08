package net.mage.cubewheel.hud;

/**
 * A small pie chart for the progress popup: a disc with a wedge filled clockwise from the top to a fraction. Given
 * as a pixel mask ({@link #mask}), as the GUI has no circle primitive; the HUD fills the disc dark and the wedge in
 * the line's colour. Pure: no Minecraft/Fabric imports.
 */
public final class Pie {
	/** Diameter in pixels: a text row tall. */
	public static final int SIZE = 9;

	/** A pixel's part: outside the disc, the unfilled rest, or the filled wedge. */
	public enum Part { OUTSIDE, EMPTY, FILLED }

	private Pie() {}

	/**
	 * The disc of {@code diameter} pixels, row by row then column by column: which pixels are in the disc, and which of
	 * those are in the wedge that runs clockwise from the top over {@code fraction} (clamped to 0..1) of the turn. At 1
	 * the whole disc is filled; at 0 nothing.
	 */
	public static Part[][] mask(int diameter, double fraction) {
		double f = Math.max(0, Math.min(1, fraction));
		double r = diameter / 2.0;
		Part[][] out = new Part[diameter][diameter];
		for (int y = 0; y < diameter; y++) {
			for (int x = 0; x < diameter; x++) {
				double dx = x + 0.5 - r, dy = y + 0.5 - r;
				if (dx * dx + dy * dy > r * r) {
					out[y][x] = Part.OUTSIDE;
					continue;
				}
				// The angle clockwise from straight up, as a fraction of the turn.
				double turn = Math.atan2(dx, -dy) / (2 * Math.PI);
				if (turn < 0) turn += 1;
				out[y][x] = f >= 1 || (f > 0 && turn < f) ? Part.FILLED : Part.EMPTY;
			}
		}
		return out;
	}
}
