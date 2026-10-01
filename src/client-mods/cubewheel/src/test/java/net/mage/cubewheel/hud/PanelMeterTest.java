package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PanelMeterTest {
	private static int a(int c) { return c >>> 24; }
	private static int r(int c) { return c >> 16 & 0xFF; }
	private static int g(int c) { return c >> 8 & 0xFF; }

	@Test void heatsUpFromClearThroughYellowAndRedToGreen() {
		assertEquals(0, a(Panel.meterColor(0)));                        // clear
		int half = Panel.meterColor(0.5);
		assertEquals(Panel.METER_ALPHA, a(half));
		assertTrue(r(half) == 0xFF && g(half) == 0xDD);                 // yellow
		int nearly = Panel.meterColor(0.99);
		assertTrue(r(nearly) == 0xFF && g(nearly) < 0x40);              // red
		int done = Panel.meterColor(1);
		assertTrue(g(done) == 0xFF && r(done) < 0x80);                  // green
		assertTrue(a(Panel.meterColor(0.25)) > 0 && a(Panel.meterColor(0.25)) < Panel.METER_ALPHA);
		assertEquals(Panel.meterColor(1), Panel.meterColor(5));         // clamped
	}
}
