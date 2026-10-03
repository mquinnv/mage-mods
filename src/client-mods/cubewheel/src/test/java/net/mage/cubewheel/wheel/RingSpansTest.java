package net.mage.cubewheel.wheel;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

	/** Whether pixel (x, y) (its centre) is covered by a span. */
	private static boolean covered(List<int[]> spans, int x, int y) {
		for (int[] s : spans) if (s[0] == y && x >= s[1] && x < s[2]) return true;
		return false;
	}

	@Test void aSectorCoversOnlyItsWedgeOfTheBand() {
		List<int[]> right = RadialMath.sectorSpans(40, 20, 45, 135); // the right-hand quarter
		assertTrue(covered(right, 30, 0));    // 3 o'clock, in the band
		assertFalse(covered(right, -30, 0));  // 9 o'clock
		assertFalse(covered(right, 0, -30));  // 12 o'clock
		assertFalse(covered(right, 10, 0));   // inside the hole
		assertFalse(covered(right, 45, 0));   // beyond the band
		assertTrue(covered(right, 25, 20));   // ~129°, still inside
	}

	@Test void aSectorAcrossTwelveOClockWraps() {
		List<int[]> top = RadialMath.sectorSpans(40, 20, 330, 30);
		assertTrue(covered(top, 0, -30));
		assertTrue(covered(top, -8, -30));
		assertFalse(covered(top, 30, 0));
		assertEquals(RadialMath.ringSpans(40, 20).size(), RadialMath.sectorSpans(40, 20, 0, 360).size());
	}

	@Test void degenerateInputsGiveNothing() {
		assertTrue(RadialMath.ringSpans(0, 0).isEmpty());
		assertTrue(RadialMath.ringSpans(10, 10).isEmpty());
		assertTrue(RadialMath.ringSpans(10, 12).isEmpty());
	}
}
