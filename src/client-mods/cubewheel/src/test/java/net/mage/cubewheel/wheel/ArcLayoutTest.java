package net.mage.cubewheel.wheel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ArcLayoutTest {
	@Test void stepFromPixelSpacing() {
		assertEquals(Math.toDegrees(40.0 / 160), ArcLayout.step(160, 40), 1e-9);
		assertEquals(0, ArcLayout.step(0, 40), 1e-9);
	}

	@Test void anglesAreCentredOnTheSlice() {
		assertArrayEquals(new double[] {200, 220, 240}, ArcLayout.angles(3, 220, 20), 1e-9);
		assertArrayEquals(new double[] {350, 10}, ArcLayout.angles(2, 0, 20), 1e-9); // wraps past 0
		assertEquals(0, ArcLayout.angles(0, 90, 20).length);
	}

	/** 2026-10-08: "armor being 1st means it is all the way across the screen from where my mouse is". */
	@Test void pinnedEntriesSitInTheMiddleOfTheArc() {
		java.util.List<String> nine = java.util.List.of("Armor", "a", "b", "c", "d", "e", "f", "g", "h");
		assertEquals(java.util.List.of("a", "b", "c", "d", "Armor", "e", "f", "g", "h"),
				ArcLayout.centred(nine, "Armor"::equals)); // index 4 of 9: exactly under the pointer
		assertEquals(java.util.List.of("a", "b", "Armor", "Base", "c", "d"),
				ArcLayout.centred(java.util.List.of("Armor", "Base", "a", "b", "c", "d"), s -> Character.isUpperCase(s.charAt(0))));
		assertEquals(nine, ArcLayout.centred(nine, s -> false)); // nothing pinned: untouched
		assertEquals(java.util.List.of("Armor"), ArcLayout.centred(java.util.List.of("Armor"), "Armor"::equals));
	}

	@Test void anglesFromPerGapSpacingStayCentred() {
		double r = 100;
		double g1 = Math.toDegrees(40.0 / r), g2 = Math.toDegrees(80.0 / r);
		double[] a = ArcLayout.angles(new double[] {40, 80}, 90, r);
		assertArrayEquals(new double[] {90 - (g1 + g2) / 2, 90 - (g1 + g2) / 2 + g1, 90 + (g1 + g2) / 2}, a, 1e-9);
		assertArrayEquals(new double[] {90}, ArcLayout.angles(new double[0], 90, r), 1e-9);
	}

	@Test void aLabelReachesFurtherAlongTheArcAtTheTopThanAtTheSide() {
		// At the top (0°) a 60px label sits centred above its disc and runs along the arc: half its width.
		assertEquals(30, ArcLayout.extent(0, 60, 9, 16, 0.35), 1e-9);
		// At 3 o'clock it sits beside the disc, across the arc: only the disc counts.
		assertEquals(16, ArcLayout.extent(90, 60, 9, 16, 0.35), 1e-9);
		// A short label never reaches less than the disc.
		assertEquals(16, ArcLayout.extent(0, 10, 9, 16, 0.35), 1e-9);
	}

	@Test void pickNeedsThePointerPastTheEdgeAndNearAnEntry() {
		double[] a = ArcLayout.angles(3, 180, 20); // 160, 180, 200
		assertEquals(1, ArcLayout.pick(0, 150, 100, a, 15));            // straight down, past the edge
		assertEquals(-1, ArcLayout.pick(0, 90, 100, a, 15));            // inside the ring
		double r = Math.toRadians(200);
		assertEquals(2, ArcLayout.pick(Math.sin(r) * 150, -Math.cos(r) * 150, 100, a, 15));
		r = Math.toRadians(240);                                        // 40° past the last entry
		assertEquals(-1, ArcLayout.pick(Math.sin(r) * 150, -Math.cos(r) * 150, 100, a, 15));
	}
}
