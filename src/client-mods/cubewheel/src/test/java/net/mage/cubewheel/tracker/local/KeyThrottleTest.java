package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyThrottleTest {
	@Test void oneLinePerKeyPerWindow() {
		KeyThrottle t = new KeyThrottle(10_000, 64);
		assertTrue(t.allow("minecraft:warped_wart_block", 0));
		assertFalse(t.allow("minecraft:warped_wart_block", 9_999));
		assertTrue(t.allow("minecraft:netherrack", 5_000)); // other keys are independent
		assertTrue(t.allow("minecraft:warped_wart_block", 10_000));
		assertFalse(t.allow(null, 0));
	}

	@Test void boundedKeysForgetTheOldest() {
		KeyThrottle t = new KeyThrottle(10_000, 2);
		assertTrue(t.allow("a", 0));
		assertTrue(t.allow("b", 1));
		assertTrue(t.allow("c", 2)); // drops "a"
		assertTrue(t.allow("a", 3));
		assertFalse(t.allow("c", 4));
	}
}
