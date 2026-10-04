package net.mage.cubewheel.hud;

/**
 * The Status panel's light level as a small filled disc: black at light 0 shading to bright yellow at 15, with the
 * number inside in dark or light text, whichever reads on the fill. Drawn as one horizontal fill per pixel row
 * ({@link #spans}), as the GUI has no circle primitive. Pure: no Minecraft/Fabric imports.
 */
public final class LightDisc {
	/** Diameter in pixels: taller than a text row, so the line carrying it is drawn this tall. */
	public static final int SIZE = 13;
	/** Light 15's fill. */
	private static final int R = 0xFF, G = 0xEE, B = 0x44;
	private static final int DARK_TEXT = 0xFF202020;

	private LightDisc() {}

	/** The fill at {@code level} (clamped to 0..15): straight from black to bright yellow. */
	public static int fill(int level) {
		int l = Math.max(0, Math.min(15, level));
		return 0xFF000000 | R * l / 15 << 16 | G * l / 15 << 8 | B * l / 15;
	}

	/** The number's colour on {@link #fill}: dark on the bright half, white on the dark half. */
	public static int text(int level) {
		int c = fill(level);
		double lum = 0.299 * (c >> 16 & 0xFF) + 0.587 * (c >> 8 & 0xFF) + 0.114 * (c & 0xFF);
		return lum > 140 ? DARK_TEXT : Panel.WHITE;
	}

	/**
	 * The disc's width on each of its {@code diameter} pixel rows, top to bottom; each row is centred, so every width
	 * has the diameter's parity.
	 */
	public static int[] spans(int diameter) {
		int[] out = new int[diameter];
		double r = diameter / 2.0;
		for (int i = 0; i < diameter; i++) {
			double dy = i + 0.5 - r;
			double chord = 2 * Math.sqrt(Math.max(0, r * r - dy * dy));
			int inset = (int) Math.round((diameter - chord) / 2);
			out[i] = Math.max(diameter % 2 == 0 ? 2 : 1, diameter - 2 * inset);
		}
		return out;
	}
}
