package com.mage.cubewheel.wheel;

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
	 * @return slice index (0 = up, increasing clockwise), or -1 if count ≤ 0, outside deadZone, or slices ≤ 0
	 */
	public static int sliceAt(double dx, double dy, int count, double deadZone) {
		if (count <= 0 || Math.hypot(dx, dy) < deadZone) return -1;
		double deg = Math.toDegrees(Math.atan2(dx, -dy));
		if (deg < 0) deg += 360;
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
		return index * 360.0 / count;
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
}
