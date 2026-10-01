package net.mage.cubewheel.wheel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RadialMathTest {
	@Test void deadZoneReturnsMinusOne() { assertEquals(-1, RadialMath.sliceAt(2, 3, 8, 10)); }
	@Test void emptyWheel() { assertEquals(-1, RadialMath.sliceAt(0, -50, 0, 10)); }
	@Test void upIsZero() { assertEquals(0, RadialMath.sliceAt(0, -50, 8, 10)); }
	@Test void rightIsQuarter() { assertEquals(2, RadialMath.sliceAt(50, 0, 8, 10)); }
	@Test void downIsHalf() { assertEquals(4, RadialMath.sliceAt(0, 50, 8, 10)); }
	@Test void leftIsThreeQuarters() { assertEquals(6, RadialMath.sliceAt(-50, 0, 8, 10)); }
	@Test void slightlyLeftOfUpWrapsToZero() { assertEquals(0, RadialMath.sliceAt(-5, -50, 8, 10)); }
	@Test void boundaryJustPastHalfSlice() { // 8 slices → 45° each, slice 1 starts at 22.5°
		double r = Math.toRadians(23); assertEquals(1, RadialMath.sliceAt(Math.sin(r) * 50, -Math.cos(r) * 50, 8, 10)); }
	@Test void singleSliceAlwaysZero() { assertEquals(0, RadialMath.sliceAt(0, 50, 1, 10)); }
	@Test void rotatedRingPutsSliceZeroAtTheStartAngle() {
		// A sub-ring opened from the slice at 90° (right) starts there, so the same spot picks its first entry.
		assertEquals(90.0, RadialMath.sliceCenterDegrees(0, 5, 90), 1e-9);
		assertEquals(0, RadialMath.sliceAt(50, 0, 5, 10, 90));
		assertEquals(162.0, RadialMath.sliceCenterDegrees(1, 5, 90), 1e-9);
		assertEquals(1, RadialMath.sliceAt(Math.sin(Math.toRadians(162)) * 50, -Math.cos(Math.toRadians(162)) * 50, 5, 10, 90));
		// Wrapping past 360°: start 315°, slice 1 of 4 is centred at 45°.
		assertEquals(45.0, RadialMath.sliceCenterDegrees(1, 4, 315), 1e-9);
		assertEquals(1, RadialMath.sliceAt(50, -50, 4, 10, 315));
		assertEquals(-1, RadialMath.sliceAt(1, 1, 4, 10, 315));
	}

	@Test void unrotatedOverloadsMatchStartZero() {
		for (int i = 0; i < 8; i++) assertEquals(RadialMath.sliceCenterDegrees(i, 8), RadialMath.sliceCenterDegrees(i, 8, 0), 1e-9);
		assertEquals(RadialMath.sliceAt(-50, 5, 8, 10), RadialMath.sliceAt(-50, 5, 8, 10, 0));
	}

	@Test void smallSubRingFansOutBesideTheDefault() {
		// Two entries opened from the slice at 225°: default there, the second one step clockwise, not opposite.
		assertArrayEquals(new double[] {225, 270}, RadialMath.fan(2, 225, 45), 1e-9);
		// Alternating: default, right, left, further right, further left.
		assertArrayEquals(new double[] {0, 45, 315, 90, 270}, RadialMath.fan(5, 0, 45), 1e-9);
	}

	@Test void fullRingsStayEvenlySpread() {
		assertArrayEquals(new double[] {0, 36, 72, 108, 144, 180, 216, 252, 288, 324}, RadialMath.fan(10, 0, 360), 1e-9);
		assertArrayEquals(new double[] {90, 135, 180, 225, 270, 315, 0, 45}, RadialMath.fan(8, 90, 45), 1e-9);
	}

	@Test void pointingPicksTheNearestEntryEvenInTheEmptyPartOfAFan() {
		double[] fan = RadialMath.fan(2, 225, 45); // 225 (down-left), 270 (left)
		assertEquals(1, RadialMath.nearest(-50, 0, fan, 10));   // left → 270
		assertEquals(0, RadialMath.nearest(-35, 35, fan, 10));  // down-left → 225
		assertEquals(1, RadialMath.nearest(0, -50, fan, 10));   // up (0°) is nearer 270 than 225
		assertEquals(-1, RadialMath.nearest(1, 1, fan, 10));    // dead zone
		assertEquals(-1, RadialMath.nearest(0, -50, new double[0], 10));
	}

	@Test void centersAndOffset() {
		assertEquals(90.0, RadialMath.sliceCenterDegrees(1, 4), 1e-9);
		double[] o = RadialMath.offset(90, 10); assertEquals(10, o[0], 1e-9); assertEquals(0, o[1], 1e-9);
		double[] up = RadialMath.offset(0, 10); assertEquals(0, up[0], 1e-9); assertEquals(-10, up[1], 1e-9);
	}
}
