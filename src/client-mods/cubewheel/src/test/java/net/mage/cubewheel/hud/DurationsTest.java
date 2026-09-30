package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class DurationsTest {
	private static long ms(String s) {
		OptionalLong v = Durations.parse(s);
		assertTrue(v.isPresent(), "should parse: " + s);
		return v.getAsLong();
	}

	@Test void compactTokens() {
		assertEquals(30 * 60_000L, ms("30m"));
		assertEquals(12 * 60_000L + 30_000L, ms("12m 30s"));
		assertEquals(3_600_000L + 30 * 60_000L, ms("1h 30m"));
		assertEquals(3_600_000L + 5_000L, ms("1h5s"));
		assertEquals(60_000L, ms("60s"));
		assertEquals(500L, ms("0.5s"));
		assertEquals(270_000L, ms("4.5m"));
	}

	@Test void wordsAndCase() {
		assertEquals(5 * 60_000L, ms("5min"));
		assertEquals(10_000L, ms("10 seconds"));
		assertEquals(10_000L, ms("10 (Seconds)"));
		assertEquals(2 * 3_600_000L, ms("2 Hours"));
		assertEquals(90_000L, ms("1 minute and 30 seconds"));
		assertEquals(90_000L, ms("1m, 30s"));
		assertEquals(30 * 60_000L, ms("30M"));
	}

	@Test void rejectsJunk() {
		assertTrue(Durations.parse(null).isEmpty());
		assertTrue(Durations.parse("").isEmpty());
		assertTrue(Durations.parse("None").isEmpty());
		assertTrue(Durations.parse("30").isEmpty(), "a bare number has no unit");
		assertTrue(Durations.parse("30m of fun").isEmpty());
		assertTrue(Durations.parse("s").isEmpty());
	}

	@Test void countdownFormat() {
		assertEquals("0:00", Durations.countdown(0));
		assertEquals("0:01", Durations.countdown(1)); // rounds up: never shows 0:00 while time remains
		assertEquals("12:34", Durations.countdown(12 * 60_000L + 34_000L));
		assertEquals("1:02:03", Durations.countdown(3_723_000L));
		assertEquals("0:00", Durations.countdown(-5));
	}

	@Test void shortCountdownFormat() {
		assertEquals("4.5s", Durations.shortCountdown(4_500));
		assertEquals("0.1s", Durations.shortCountdown(20));
		assertEquals("45s", Durations.shortCountdown(44_100));
		assertEquals("1:30", Durations.shortCountdown(90_000));
	}
}
