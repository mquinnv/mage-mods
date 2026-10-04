package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PanelMeterTest {
	private static int a(int c) { return c >>> 24; }
	private static int r(int c) { return c >> 16 & 0xFF; }
	private static int g(int c) { return c >> 8 & 0xFF; }
	private static int b(int c) { return c & 0xFF; }

	/** Progress: one steady colour so only the length reads (Michael 2026-10-04: the heat ramp was confusing), green when done. */
	@Test void progressIsOneSteadyColourThenGreenWhenDone() {
		int quarter = Panel.meterColor(0.25), nearly = Panel.meterColor(0.99);
		assertEquals(quarter, nearly);                                     // same colour all the way
		assertEquals(Panel.METER_ALPHA, a(quarter));
		assertTrue(b(quarter) > r(quarter) && g(quarter) > r(quarter));     // teal, never red
		int done = Panel.meterColor(1);
		assertNotEquals(quarter, done);
		assertTrue(g(done) > r(done) && g(done) > b(done));                 // green
		for (double f : new double[] {0.5, 1}) {                            // dark enough for white text
			int c = Panel.meterColor(f);
			assertTrue(0.299 * r(c) + 0.587 * g(c) + 0.114 * b(c) < 120, "too bright at " + f);
		}
		assertEquals(Panel.meterColor(1), Panel.meterColor(5));            // clamped
	}

	/** A capacity gauge (inventory): green with room, yellow from 80%, red when full. */
	@Test void gaugeWarnsAsItFills() {
		int roomy = Panel.gaugeColor(0.5), high = Panel.gaugeColor(0.8), full = Panel.gaugeColor(1);
		assertTrue(g(roomy) > r(roomy));                                    // green
		assertTrue(r(high) > 0xC0 && g(high) > 0xC0 && b(high) < 0x80);     // yellow
		assertTrue(r(full) > 0xC0 && g(full) < 0x80);                       // red
		assertEquals(0xFF, a(roomy));
		assertEquals(Panel.gaugeColor(1), Panel.gaugeColor(3));             // clamped
	}
}
