package net.mage.cubewheel.capture;

import java.util.Locale;

/**
 * Where a point lies relative to a player's eye and look direction, in Minecraft's own angles: yaw 0 faces +Z
 * (south) and grows clockwise seen from above (turning right), pitch 0 is level and grows looking down. Pure: no
 * Minecraft/Fabric imports.
 */
public final class ShotGeometry {
	private ShotGeometry() {}

	/**
	 * A point as the player sees it: {@code forward}/{@code right}/{@code up} blocks along the look frame, and the
	 * angles the crosshair would have to turn to point at it, {@code dyaw} positive to the right and
	 * {@code dpitch} positive downward (Minecraft's signs).
	 */
	public record View(double forward, double right, double up, double dyaw, double dpitch, double distance) {
		/** The fields on one line, a short detail for a capture: "fwd 12.3 right -0.4 up 0.8 | dyaw -1.9 dpitch -3.7". */
		public String summary() {
			return String.format(Locale.ROOT, "fwd %.2f right %.2f up %.2f | dyaw %.1f dpitch %.1f | %.1f blocks",
					forward, right, up, dyaw, dpitch, distance);
		}
	}

	/** The unit look vector for {@code yaw}/{@code pitch} in degrees (as Minecraft computes it). */
	public static double[] look(double yaw, double pitch) {
		double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
		return new double[] {-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
	}

	/** {@code point} seen from an eye at {@code eye} looking along {@code yaw}/{@code pitch} (degrees). */
	public static View view(double[] eye, double yaw, double pitch, double[] point) {
		double dx = point[0] - eye[0], dy = point[1] - eye[1], dz = point[2] - eye[2];
		return direction(yaw, pitch, dx, dy, dz);
	}

	/** A displacement or velocity {@code (dx, dy, dz)} in the frame of a look along {@code yaw}/{@code pitch}. */
	public static View direction(double yaw, double pitch, double dx, double dy, double dz) {
		double[] f = look(yaw, pitch);
		double y = Math.toRadians(yaw);
		// The player's right hand: west when facing south (yaw 0), east when facing north (yaw 180).
		double[] r = {-Math.cos(y), 0, -Math.sin(y)};
		double[] u = {r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]};
		double forward = dx * f[0] + dy * f[1] + dz * f[2];
		double right = dx * r[0] + dy * r[1] + dz * r[2];
		double up = dx * u[0] + dy * u[1] + dz * u[2];
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double dyaw = 0, dpitch = 0;
		if (len > 1e-9) {
			double pointYaw = Math.toDegrees(Math.atan2(-dx, dz));
			double pointPitch = Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, dy / len))));
			dyaw = wrap(pointYaw - wrap(yaw));
			dpitch = pointPitch - pitch;
		}
		return new View(forward, right, up, dyaw, dpitch, len);
	}

	/** {@code deg} folded into -180..180. */
	static double wrap(double deg) {
		double d = deg % 360;
		if (d >= 180) d -= 360;
		if (d < -180) d += 360;
		return d;
	}
}
