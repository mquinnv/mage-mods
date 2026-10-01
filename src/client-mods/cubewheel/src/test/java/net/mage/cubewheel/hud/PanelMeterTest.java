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
		assertTrue(r(half) > g(half) && g(half) > 0x50);                 // amber
		int nearly = Panel.meterColor(0.99);
		assertTrue(r(nearly) > 0x90 && g(nearly) < 0x30);               // red
		int done = Panel.meterColor(1);
		assertTrue(g(done) > r(done) && g(done) > 0x60);                // green
		for (double f : new double[] {0.5, 0.75, 0.99, 1}) {               // dark enough for white text
			int c = Panel.meterColor(f);
			assertTrue(0.299 * r(c) + 0.587 * g(c) + 0.114 * (c & 0xFF) < 120, "too bright at " + f);
		}
		assertTrue(a(Panel.meterColor(0.25)) > 0 && a(Panel.meterColor(0.25)) < Panel.METER_ALPHA);
		assertEquals(Panel.meterColor(1), Panel.meterColor(5));         // clamped
	}
}
