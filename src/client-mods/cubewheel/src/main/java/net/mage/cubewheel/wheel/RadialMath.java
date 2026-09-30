package net.mage.cubewheel.wheel;

import java.util.ArrayList;
import java.util.List;

/** Radial geometry for wheel UI. All angles: 0 = up, clockwise increasing (screen coords: y down). */
public final class RadialMath {
	private RadialMath() {}

	/**
	 * Which slice of the wheel does (dx, dy) fall into?
	 *
	 * @param dx horizontal offset (screen coords)
	 * @param dy vertical offset (screen coords, positive = down)
	 * @param count number of slices; must be > 0
	 * @param deadZone minimum distance from center; points within are ignored
	 * @return slice index (0 = up, increasing clockwise), or -1 if count ≤ 0 or outside deadZone
	 */
	public static int sliceAt(double dx, double dy, int count, double deadZone) {
		return sliceAt(dx, dy, count, deadZone, 0);
	}

	/**
	 * As {@link #sliceAt(double, double, int, double)} for a ring rotated so slice 0 is centred at
	 * {@code startDegrees} (0 = up, clockwise). Sub-rings start where the slice that opened them was, so
	 * clicking the same spot twice takes the sub-ring's first (default) entry.
	 */
	public static int sliceAt(double dx, double dy, int count, double deadZone, double startDegrees) {
		if (count <= 0 || Math.hypot(dx, dy) < deadZone) return -1;
		double deg = Math.toDegrees(Math.atan2(dx, -dy)) - startDegrees;
		deg = ((deg % 360) + 360) % 360;
		double w = 360.0 / count;
		return (int) Math.floor((deg + w / 2) / w) % count;
	}

	/**
	 * Center angle of a slice.
	 *
	 * @param index slice index (0 = up, increasing clockwise)
	 * @param count total number of slices
	 * @return angle in degrees (0 = up, clockwise)
	 */
	public static double sliceCenterDegrees(int index, int count) {
		return sliceCenterDegrees(index, count, 0);
	}

	/** Centre of slice {@code index} in a ring whose slice 0 sits at {@code startDegrees}; in [0, 360). */
	public static double sliceCenterDegrees(int index, int count, double startDegrees) {
		double deg = startDegrees + index * 360.0 / count;
		return ((deg % 360) + 360) % 360;
	}

	/**
	 * Screen offset for a direction and distance.
	 *
	 * @param degrees direction (0 = up, clockwise)
	 * @param radius distance
	 * @return {x, y} in screen coords (y positive = down)
	 */
	public static double[] offset(double degrees, double radius) {
		double r = Math.toRadians(degrees);
		return new double[] { Math.sin(r) * radius, -Math.cos(r) * radius };
	}

	/**
	 * Horizontal spans that rasterise a ring (or a disc when {@code inner} is 0) centred on the
	 * origin, one row per unit of height. The GUI renderer can only fill rectangles, so circles are
	 * drawn as these one-unit-tall strips.
	 *
	 * @param outer outer radius
	 * @param inner inner radius (0 for a solid disc)
	 * @return {@code {dy, x0, x1}} per span, x1 exclusive; empty if {@code outer <= 0} or
	 *         {@code inner >= outer}
	 */
	public static List<int[]> ringSpans(int outer, int inner) {
		List<int[]> spans = new ArrayList<>();
		if (outer <= 0 || inner >= outer) return spans;
		for (int dy = -outer; dy < outer; dy++) {
			double yc = dy + 0.5;
			int xo = (int) Math.round(Math.sqrt(outer * (double) outer - yc * yc));
			if (xo <= 0) continue;
			if (Math.abs(yc) < inner) {
				int xi = (int) Math.round(Math.sqrt(inner * (double) inner - yc * yc));
				if (xi < xo) {
					spans.add(new int[] { dy, -xo, -xi });
					spans.add(new int[] { dy, xi, xo });
				}
			} else {
				spans.add(new int[] { dy, -xo, xo });
			}
		}
		return spans;
	}
}
