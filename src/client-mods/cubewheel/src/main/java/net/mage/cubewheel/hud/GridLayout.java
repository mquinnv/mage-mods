package net.mage.cubewheel.hud;

/**
 * Column widths for a {@link Panel.Grid}: each column as wide as its widest cell, and since the cells' digits change
 * every frame (coordinates, fps, speed), as wide as the widest cell seen until {@link #reset} (another world), so
 * nothing twitches. A panel wider than its grid shares the extra out ({@link #stretch}). Widths are content pixels.
 * Pure: no Minecraft/Fabric imports.
 */
public final class GridLayout {
	/** Between two columns, a thin divider in its middle. */
	public static final int GAP = 9;

	private int[] widest = new int[0];

	/** Column widths for cells of natural widths {@code natural[row][column]}: never narrower than before a reset. */
	public int[] columns(int[][] natural) {
		int n = 0;
		for (int[] row : natural) n = Math.max(n, row.length);
		if (widest.length < n) widest = java.util.Arrays.copyOf(widest, n);
		for (int[] row : natural) {
			for (int c = 0; c < row.length; c++) widest[c] = Math.max(widest[c], row[c]);
		}
		return java.util.Arrays.copyOf(widest, n);
	}

	/** Start over: the next widths are taken as they are. */
	public void reset() {
		widest = new int[0];
	}

	/** The grid's width: its columns and the gaps between them. */
	public static int width(int[] columns) {
		int w = 0;
		for (int c : columns) w += c;
		return w + GAP * Math.max(0, columns.length - 1);
	}

	/** {@code columns} widened to fill {@code total}: the extra shared evenly, any odd pixels to the last column. */
	public static int[] stretch(int[] columns, int total) {
		int[] out = columns.clone();
		int extra = total - width(columns);
		if (extra <= 0 || out.length == 0) return out;
		for (int c = 0; c < out.length; c++) out[c] += extra / out.length;
		out[out.length - 1] += extra % out.length;
		return out;
	}
}
