package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SteadyWidthTest {
	@Test void onlyGrowsUntilReset() {
		SteadyWidth w = new SteadyWidth();
		assertEquals(120, w.apply(120));
		assertEquals(126, w.apply(126)); // "-1234" became "-12345"
		assertEquals(126, w.apply(118)); // narrower digits: the panel stays put
		w.reset();
		assertEquals(110, w.apply(110));
	}
}
