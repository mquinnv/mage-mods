package net.mage.cubewheel.wheel;

import org.junit.jupiter.api.Test;

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
	@Test void centersAndOffset() {
		assertEquals(90.0, RadialMath.sliceCenterDegrees(1, 4), 1e-9);
		double[] o = RadialMath.offset(90, 10); assertEquals(10, o[0], 1e-9); assertEquals(0, o[1], 1e-9);
		double[] up = RadialMath.offset(0, 10); assertEquals(0, up[0], 1e-9); assertEquals(-10, up[1], 1e-9);
	}
}
