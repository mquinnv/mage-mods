package net.mage.cubewheel.wheel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ArcLayoutTest {
	@Test void stepFromPixelSpacing() {
		assertEquals(Math.toDegrees(40.0 / 160), ArcLayout.step(160, 40), 1e-9);
		assertEquals(0, ArcLayout.step(0, 40), 1e-9);
	}

	@Test void anglesAreCentredOnTheSlice() {
		assertArrayEquals(new double[] {200, 220, 240}, ArcLayout.angles(3, 220, 20), 1e-9);
		assertArrayEquals(new double[] {350, 10}, ArcLayout.angles(2, 0, 20), 1e-9); // wraps past 0
		assertEquals(0, ArcLayout.angles(0, 90, 20).length);
	}

	@Test void pickNeedsThePointerPastTheEdgeAndNearAnEntry() {
		double[] a = ArcLayout.angles(3, 180, 20); // 160, 180, 200
		assertEquals(1, ArcLayout.pick(0, 150, 100, a, 15));            // straight down, past the edge
		assertEquals(-1, ArcLayout.pick(0, 90, 100, a, 15));            // inside the ring
		double r = Math.toRadians(200);
		assertEquals(2, ArcLayout.pick(Math.sin(r) * 150, -Math.cos(r) * 150, 100, a, 15));
		r = Math.toRadians(240);                                        // 40° past the last entry
		assertEquals(-1, ArcLayout.pick(Math.sin(r) * 150, -Math.cos(r) * 150, 100, a, 15));
	}
}
