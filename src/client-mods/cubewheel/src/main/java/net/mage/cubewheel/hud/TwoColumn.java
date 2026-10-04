package net.mage.cubewheel.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Layout for a panel with a {@link Panel.Side} column: the lines on the left, a {@link #GAP} with a thin divider in
 * its middle, then the side column; and word wrapping for text that must fit the side column. Widths are content
 * pixels (the panel's padding comes on top). Pure: no Minecraft/Fabric imports; text is measured by the caller's
 * {@code width} function (the font's).
 */
public final class TwoColumn {
	/** Between the columns, the divider in its middle. */
	public static final int GAP = 9;
	/** The side column's width range: room for an item and a short bar, at most a short set name and count. */
	public static final int SIDE_MIN_W = 64;
	public static final int SIDE_MAX_W = 96;

	private TwoColumn() {}

	/** The side column's width for content that would like {@code natural} pixels. */
	public static int sideWidth(int natural) {
		return Math.max(SIDE_MIN_W, Math.min(SIDE_MAX_W, natural));
	}

	/** The whole content width for a left column of {@code left} and a side column of {@code side} (0 = none). */
	public static int width(int left, int side) {
		return side <= 0 ? left : left + GAP + side;
	}

	/** The left column's width in a content width of {@code total} (stretched to its corner's width). */
	public static int leftWidth(int total, int side) {
		return side <= 0 ? total : total - GAP - side;
	}

	/**
	 * {@code text} word-wrapped into at most {@code maxLines} lines of at most {@code maxW} pixels; a word wider than a
	 * line is cut, and when text is left over the last line ends with "…".
	 */
	public static List<String> wrap(String text, int maxW, ToIntFunction<String> width, int maxLines) {
		List<String> out = new ArrayList<>();
		if (text == null || text.isBlank() || maxLines <= 0) return out;
		String rest = text.trim();
		while (!rest.isEmpty()) {
			if (out.size() == maxLines - 1 && width.applyAsInt(rest) > maxW) {
				out.add(ellipsis(rest, maxW, width));
				return out;
			}
			String line = fitWords(rest, maxW, width);
			out.add(line);
			rest = rest.substring(line.length()).trim();
		}
		return out;
	}

	/** The longest start of {@code s} that fits, broken after a whole word when one fits, else mid-word. */
	private static String fitWords(String s, int maxW, ToIntFunction<String> width) {
		if (width.applyAsInt(s) <= maxW) return s;
		int fit = 0;
		while (fit < s.length() && width.applyAsInt(s.substring(0, fit + 1)) <= maxW) fit++;
		int space = s.lastIndexOf(' ', fit);
		if (space > 0) return s.substring(0, space).stripTrailing();
		return s.substring(0, Math.max(1, fit));
	}

	private static String ellipsis(String s, int maxW, ToIntFunction<String> width) {
		int fit = 0;
		while (fit < s.length() && width.applyAsInt(s.substring(0, fit + 1) + "…") <= maxW) fit++;
		return s.substring(0, fit).stripTrailing() + "…";
	}
}
