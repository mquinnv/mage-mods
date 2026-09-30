package com.mage.cubewheel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SmokeTest {
	@Test
	public void testModId() {
		assertEquals("cubewheel", CubeWheelClient.MOD_ID);
	}
}
