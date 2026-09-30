package com.mage.cubewheel.wheel;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingSpansTest {
	@Test void discCoversEveryRowOnceAndIsWidestInTheMiddle() {
		List<int[]> spans = RadialMath.ringSpans(10, 0);
		assertEquals(20, spans.size());
		int[] mid = spans.stream().filter(s -> s[0] == 0).findFirst().orElseThrow();
		assertEquals(-10, mid[1]);
		assertEquals(10, mid[2]);
		int[] top = spans.get(0);
		assertEquals(-10, top[0]);
		assertTrue(top[2] - top[1] < 10, "top row is narrow");
	}

	@Test void discIsSymmetric() {
		for (int[] s : RadialMath.ringSpans(15, 0)) assertEquals(-s[1], s[2]);
	}

	@Test void ringLeavesTheCentreEmpty() {
		List<int[]> middle = RadialMath.ringSpans(20, 12).stream().filter(s -> s[0] == 0).toList();
		assertEquals(2, middle.size());
		assertEquals(-20, middle.get(0)[1]);
		assertEquals(-12, middle.get(0)[2]);
		assertEquals(12, middle.get(1)[1]);
		assertEquals(20, middle.get(1)[2]);
	}

	@Test void ringRowsOutsideTheHoleAreSolid() {
		List<int[]> top = RadialMath.ringSpans(20, 12).stream().filter(s -> s[0] == -19).toList();
		assertEquals(1, top.size());
	}

	@Test void degenerateInputsGiveNothing() {
		assertTrue(RadialMath.ringSpans(0, 0).isEmpty());
		assertTrue(RadialMath.ringSpans(10, 10).isEmpty());
		assertTrue(RadialMath.ringSpans(10, 12).isEmpty());
	}
}
