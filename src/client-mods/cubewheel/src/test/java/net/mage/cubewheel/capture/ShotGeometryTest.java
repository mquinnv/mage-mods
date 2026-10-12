package net.mage.cubewheel.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ShotGeometryTest {
	private static final double[] EYE = {10, 65, 10};

	@Test
	void aPointStraightAheadIsOnTheCrosshair() {
		// Facing south (yaw 0), level: a point 8 blocks south is dead ahead.
		ShotGeometry.View v = ShotGeometry.view(EYE, 0, 0, new double[] {10, 65, 18});
		assertEquals(8, v.forward(), 1e-9);
		assertEquals(0, v.right(), 1e-9);
		assertEquals(0, v.up(), 1e-9);
		assertEquals(0, v.dyaw(), 1e-9);
		assertEquals(0, v.dpitch(), 1e-9);
		assertEquals(8, v.distance(), 1e-9);
	}

	@Test
	void aPointToThePlayersRightHasPositiveDyawAndAPointBelowHasPositiveDpitch() {
		// Facing south, west (-X) is on the right. Minecraft's yaw grows turning right, pitch grows looking down.
		ShotGeometry.View right = ShotGeometry.view(EYE, 0, 0, new double[] {9, 65, 18});
		assertEquals(1, right.right(), 1e-9);
		assertEquals(Math.toDegrees(Math.atan2(1, 8)), right.dyaw(), 1e-9);
		ShotGeometry.View below = ShotGeometry.view(EYE, 0, 0, new double[] {10, 64, 18});
		assertEquals(-1, below.up(), 1e-9);
		assertEquals(Math.toDegrees(Math.atan2(1, 8)), below.dpitch(), 1e-9);
	}

	@Test
	void theFrameFollowsTheLookDirection() {
		// Facing north (yaw 180) and 30 degrees down: a point along the look vector is ahead, at no angle.
		double[] look = ShotGeometry.look(180, 30);
		double[] p = {EYE[0] + 5 * look[0], EYE[1] + 5 * look[1], EYE[2] + 5 * look[2]};
		ShotGeometry.View v = ShotGeometry.view(EYE, 180, 30, p);
		assertEquals(5, v.forward(), 1e-9);
		assertEquals(0, v.right(), 1e-9);
		assertEquals(0, v.up(), 1e-9);
		assertEquals(0, v.dyaw(), 1e-9);
		assertEquals(0, v.dpitch(), 1e-9);
		// Facing north, east (+X) is on the right.
		ShotGeometry.View east = ShotGeometry.view(EYE, 180, 0, new double[] {12, 65, 2});
		assertEquals(2, east.right(), 1e-9);
		assertEquals(8, east.forward(), 1e-9);
	}

	@Test
	void yawWrapsAcrossTheSeam() {
		// Facing yaw 179 at a point at yaw -179: two degrees to the right, not 358 to the left.
		double[] look = ShotGeometry.look(-179, 0);
		double[] p = {EYE[0] + 8 * look[0], EYE[1], EYE[2] + 8 * look[2]};
		assertEquals(2, ShotGeometry.view(EYE, 179, 0, p).dyaw(), 1e-6);
		assertEquals(-2, ShotGeometry.view(EYE, -179, 0, new double[] {
				EYE[0] + 8 * ShotGeometry.look(179, 0)[0], EYE[1], EYE[2] + 8 * ShotGeometry.look(179, 0)[2]}).dyaw(), 1e-6);
		// Yaw as the game reports it may be far outside -180..180.
		assertEquals(0, ShotGeometry.view(EYE, 720, 0, new double[] {10, 65, 18}).dyaw(), 1e-9);
	}

	@Test
	void aSummaryReadsOnOneLine() {
		ShotGeometry.View v = ShotGeometry.view(EYE, 0, 0, new double[] {9.5, 64, 18});
		assertEquals("fwd 8.00 right 0.50 up -1.00 | dyaw 3.6 dpitch 7.1 | 8.1 blocks", v.summary());
	}
}
