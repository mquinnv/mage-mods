package net.mage.cubewheel.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class XpCurveTest {
	@Test void matchesTheCapturedBars() {
		assertEquals(62_250L, XpCurve.needed(243));
		assertEquals(155_750L, XpCurve.needed(617));
		assertEquals(62_000L, XpCurve.needed(242));
		assertEquals(33_000L, XpCurve.needed(126));
		assertEquals(15_500L, XpCurve.needed(56));
		assertEquals(68_500L, XpCurve.needed(268));
	}

	@Test void doesNotOverflowAtLargeLevels() {
		assertEquals(1500L + 250L * Integer.MAX_VALUE, XpCurve.needed(Integer.MAX_VALUE));
	}
}
