package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PieTest {
	static int count(Pie.Part[][] m, Pie.Part part) {
		int n = 0;
		for (Pie.Part[] row : m) for (Pie.Part p : row) if (p == part) n++;
		return n;
	}

	@Test void emptyAndFullDiscs() {
		Pie.Part[][] none = Pie.mask(Pie.SIZE, 0);
		assertEquals(0, count(none, Pie.Part.FILLED));
		assertTrue(count(none, Pie.Part.EMPTY) > 40); // most of a 9x9 square is disc
		assertTrue(count(none, Pie.Part.OUTSIDE) > 0); // the corners are not
		Pie.Part[][] full = Pie.mask(Pie.SIZE, 1);
		assertEquals(0, count(full, Pie.Part.EMPTY));
		assertEquals(count(none, Pie.Part.EMPTY), count(full, Pie.Part.FILLED));
	}

	@Test void aQuarterFillsTheTopRightAndGrowsClockwise() {
		Pie.Part[][] q = Pie.mask(Pie.SIZE, 0.25);
		int c = Pie.SIZE / 2;
		assertEquals(Pie.Part.FILLED, q[1][c + 1]); // just right of the top
		assertEquals(Pie.Part.EMPTY, q[1][c - 1]); // just left of the top: the last to fill
		assertEquals(Pie.Part.EMPTY, q[c + 2][c + 1]); // below the middle on the right: the second quarter
		int quarter = count(q, Pie.Part.FILLED);
		int half = count(Pie.mask(Pie.SIZE, 0.5), Pie.Part.FILLED);
		int three = count(Pie.mask(Pie.SIZE, 0.75), Pie.Part.FILLED);
		assertTrue(quarter > 0 && quarter < half && half < three);
		assertEquals(0, count(Pie.mask(Pie.SIZE, -0.5), Pie.Part.FILLED)); // clamped
		assertEquals(count(Pie.mask(Pie.SIZE, 1), Pie.Part.FILLED), count(Pie.mask(Pie.SIZE, 7), Pie.Part.FILLED));
	}
}
