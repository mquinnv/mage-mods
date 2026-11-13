package net.mage.cubewheel.compat.jade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HealthTextTest {
	@Test
	void wholeNumbersHaveNoDecimal() {
		assertEquals("75", HealthText.format(75));
		assertEquals("0", HealthText.format(0));
		assertEquals("1200", HealthText.format(1200));
	}

	@Test
	void fractionsKeepOneDecimal() {
		assertEquals("12.5", HealthText.format(12.5));
		assertEquals("12.3", HealthText.format(12.25));
		assertEquals("13", HealthText.format(12.96));
	}
}
