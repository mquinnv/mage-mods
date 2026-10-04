package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LightDiscTest {
	private static int r(int c) { return c >> 16 & 0xFF; }
	private static int g(int c) { return c >> 8 & 0xFF; }
	private static int b(int c) { return c & 0xFF; }
	private static double lum(int c) { return 0.299 * r(c) + 0.587 * g(c) + 0.114 * b(c); }

	@Test void darkIsBlackAndFullLightIsBrightYellow() {
		assertEquals(0xFF000000, LightDisc.fill(0));
		int full = LightDisc.fill(15);
		assertEquals(0xFF, full >>> 24);
		assertTrue(r(full) >= 0xF0 && g(full) >= 0xE0 && b(full) < r(full) / 2, "bright yellow: " + Integer.toHexString(full));
	}

	@Test void brighterWithEveryLevel() {
		for (int level = 1; level <= 15; level++) {
			assertTrue(lum(LightDisc.fill(level)) > lum(LightDisc.fill(level - 1)), "level " + level);
			assertEquals(0xFF, LightDisc.fill(level) >>> 24);
		}
		assertEquals(LightDisc.fill(0), LightDisc.fill(-3));   // clamped
		assertEquals(LightDisc.fill(15), LightDisc.fill(40));
	}

	@Test void theNumberContrastsWithTheFill() {
		for (int level = 0; level <= 15; level++) {
			double fill = lum(LightDisc.fill(level)), text = lum(LightDisc.text(level));
			assertTrue(Math.abs(fill - text) > 100, "level " + level + " unreadable");
		}
		assertEquals(Panel.WHITE, LightDisc.text(0));
		assertTrue(lum(LightDisc.text(15)) < 80, "dark text on the bright fill");
	}

	@Test void spansDrawACentredRoundDisc() {
		for (int d : new int[] {11, 12, 13}) {
			int[] w = LightDisc.spans(d);
			assertEquals(d, w.length);
			for (int i = 0; i < d; i++) {
				assertEquals(w[i], w[d - 1 - i], "symmetric, d=" + d);
				assertEquals(d % 2, w[i] % 2, "centred on whole pixels, d=" + d + " row " + i);
				assertTrue(w[i] > 0 && w[i] <= d);
				if (i > 0 && i <= d / 2) assertTrue(w[i] >= w[i - 1], "widens towards the middle, d=" + d);
			}
			assertEquals(d, w[d / 2]);
			assertTrue(w[0] < d - 4, "the ends are rounded, d=" + d);
		}
	}
}
