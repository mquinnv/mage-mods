package net.mage.cubewheel.status;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/** The Status panel's hand-formatted clocks read exactly as String.format wrote them. */
class StatusFormatTest {
	@Test void clocksMatchStringFormat() {
		for (long t = -48_000; t <= 48_000; t += 7) {
			long m = Math.floorMod(t, 24_000L);
			int minutes = (int) ((m * 60 / 1000 + 6 * 60) % (24 * 60));
			assertEquals(String.format(Locale.ROOT, "%d:%02d", minutes / 60, minutes % 60), StatusFormat.gameTime(t));
		}
		for (int hour = 0; hour < 24; hour++) {
			for (int minute = 0; minute < 60; minute++) {
				int h = hour % 12 == 0 ? 12 : hour % 12;
				assertEquals(String.format(Locale.ROOT, "%d:%02d%s", h, minute, hour < 12 ? "a" : "p"), StatusFormat.clock(hour, minute));
			}
		}
		for (int v : new int[] {Integer.MIN_VALUE, -100, -10, -9, -1, 0, 1, 9, 10, 99, 100, Integer.MAX_VALUE}) {
			assertEquals(String.format(Locale.ROOT, "%02d", v), StatusFormat.twoDigits(v));
		}
	}

	@Test void speedMatchesStringFormatWhenRepeatedOrChanged() {
		double[] speeds = {0, 4.25, 4.25, 4.35, -1, 0, 12.96, 12.96, 0.05, 1.45};
		for (double s : speeds) {
			assertEquals(String.format(Locale.ROOT, "%.1f b/s", Math.max(0, s)), StatusFormat.speed(s));
		}
	}
}
