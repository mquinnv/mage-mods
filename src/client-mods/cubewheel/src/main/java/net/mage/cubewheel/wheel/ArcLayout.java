package net.mage.cubewheel.wheel;

/**
 * A slice's arc: extra entries fanned out in a curve just beyond the ring, centred on the slice (e.g. the isle
 * warps around Isles). Angles as in {@link RadialMath}: degrees, 0 = up, clockwise.
 */
public final class ArcLayout {
	private ArcLayout() {}

	/** Angular gap between neighbours {@code spacing} pixels apart on a circle of {@code radius}. */
	public static double step(double radius, double spacing) {
		return radius <= 0 ? 0 : Math.toDegrees(spacing / radius);
	}

	/** Directions of {@code count} entries {@code step} degrees apart, centred on {@code centre}; in [0, 360). */
	public static double[] angles(int count, double centre, double step) {
		if (count <= 0) return new double[0];
		double[] out = new double[count];
		double start = centre - step * (count - 1) / 2.0;
		for (int i = 0; i < count; i++) out[i] = norm(start + i * step);
		return out;
	}

	/**
	 * The arc entry the pointer at (dx, dy) is on: nearest by angle, provided the pointer is past {@code edge} and
	 * within {@code slack} degrees of that entry; otherwise -1.
	 */
	public static int pick(double dx, double dy, double edge, double[] angles, double slack) {
		if (angles == null || angles.length == 0 || Math.hypot(dx, dy) <= edge) return -1;
		double deg = norm(Math.toDegrees(Math.atan2(dx, -dy)));
		int best = -1;
		double bestDiff = Double.MAX_VALUE;
		for (int i = 0; i < angles.length; i++) {
			double d = Math.abs(deg - angles[i]) % 360;
			d = Math.min(d, 360 - d);
			if (d < bestDiff) {
				bestDiff = d;
				best = i;
			}
		}
		return bestDiff <= slack ? best : -1;
	}

	private static double norm(double deg) {
		return ((deg % 360) + 360) % 360;
	}
}
