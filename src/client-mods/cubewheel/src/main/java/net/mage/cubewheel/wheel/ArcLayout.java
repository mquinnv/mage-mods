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
	 * Directions of entries whose neighbours sit {@code gaps[k]} pixels apart (k between entry k and k + 1) on a
	 * circle of {@code radius}, the whole arc centred on {@code centre}; in [0, 360).
	 */
	public static double[] angles(double[] gaps, double centre, double radius) {
		int count = gaps.length + 1;
		double[] out = new double[count];
		double[] at = new double[count];
		for (int k = 0; k < gaps.length; k++) at[k + 1] = at[k] + step(radius, gaps[k]);
		double start = centre - at[count - 1] / 2.0;
		for (int i = 0; i < count; i++) out[i] = norm(start + at[i]);
		return out;
	}

	/**
	 * How far an entry at {@code direction} reaches along the arc (tangentially) from its centre: its disc, or its
	 * label where that sticks out further. The label sits as {@code RadialScreen.radialLabel} puts it: beside the
	 * disc where the arc leans sideways (sine past {@code sideways}), above or below it otherwise.
	 */
	public static double extent(double direction, int labelWidth, int labelHeight, int disc, double sideways) {
		double rad = Math.toRadians(direction);
		double dx = Math.sin(rad), dy = -Math.cos(rad); // outwards
		double tx = Math.cos(rad), ty = Math.sin(rad);  // along the arc
		int reach = disc + 2;
		double cx = dx > sideways ? reach + labelWidth / 2.0 : dx < -sideways ? -reach - labelWidth / 2.0 : 0;
		double cy = dy > sideways ? reach - 4 + labelHeight / 2.0 : dy < -sideways ? -reach + 4 - labelHeight / 2.0 : 0;
		double label = Math.abs(cx * tx + cy * ty) + labelWidth / 2.0 * Math.abs(tx) + labelHeight / 2.0 * Math.abs(ty);
		return Math.max(disc, label);
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
