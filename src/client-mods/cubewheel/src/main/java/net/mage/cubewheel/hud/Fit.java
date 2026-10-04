package net.mage.cubewheel.hud;

import java.util.function.ToIntFunction;

/** Text cut to a width with "…". Pure: no Minecraft/Fabric imports; text is measured by the caller's {@code width}. */
public final class Fit {
	private static final String ELLIPSIS = "…";

	private Fit() {}

	/** {@code s} as is when it fits {@code max} pixels, else its longest start that fits with "…" after it. */
	public static String cut(String s, int max, ToIntFunction<String> width) {
		if (s == null) return "";
		if (width.applyAsInt(s) <= max) return s;
		int n = s.length();
		while (n > 0 && width.applyAsInt(s.substring(0, n).stripTrailing() + ELLIPSIS) > max) n--;
		return n == 0 ? "" : s.substring(0, n).stripTrailing() + ELLIPSIS;
	}
}
