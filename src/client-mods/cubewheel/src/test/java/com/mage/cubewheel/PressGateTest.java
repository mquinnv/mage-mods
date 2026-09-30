package com.mage.cubewheel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PressGateTest {
	@Test void aHeldKeyFiresOnceDespiteKeyRepeat() {
		// Minecraft counts OS key-repeat events as clicks while the key is held (the 12:03:48 double toggle).
		PressGate g = new PressGate();
		assertTrue(g.fire(true, true));   // press
		assertFalse(g.fire(false, true)); // held
		assertFalse(g.fire(true, true));  // repeat click, still held
		assertFalse(g.fire(true, true));
		assertFalse(g.fire(false, false)); // released
		assertTrue(g.fire(true, true));   // next press
	}

	@Test void aTapWithinOneTickFiresAndRearms() {
		PressGate g = new PressGate();
		assertTrue(g.fire(true, false)); // pressed and released between two ticks
		assertTrue(g.fire(true, false));
		assertFalse(g.fire(false, false));
	}

	@Test void noClickNeverFires() {
		PressGate g = new PressGate();
		assertFalse(g.fire(false, true)); // held since before (e.g. down when a screen closed)
		assertFalse(g.fire(false, false));
	}
}
