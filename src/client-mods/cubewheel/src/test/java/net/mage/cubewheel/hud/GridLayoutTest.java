package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GridLayoutTest {
	@Test void eachColumnIsItsWidestCell() {
		GridLayout g = new GridLayout();
		assertArrayEquals(new int[] {90, 70}, g.columns(new int[][] {{90, 60}, {70, 70}}));
	}

	@Test void columnsOnlyGrowUntilReset() {
		GridLayout g = new GridLayout();
		g.columns(new int[][] {{90, 60}, {70, 70}});
		// "-1234" became "-12345" in the first column; the second got narrower: it stays.
		assertArrayEquals(new int[] {96, 70}, g.columns(new int[][] {{96, 50}, {70, 40}}));
		assertArrayEquals(new int[] {96, 70}, g.columns(new int[][] {{80, 50}, {70, 40}}));
		g.reset();
		assertArrayEquals(new int[] {80, 50}, g.columns(new int[][] {{80, 50}, {70, 40}}));
	}

	@Test void widthIsTheColumnsAndTheGaps() {
		assertEquals(90 + GridLayout.GAP + 70, GridLayout.width(new int[] {90, 70}));
		assertEquals(50, GridLayout.width(new int[] {50}));
		assertEquals(0, GridLayout.width(new int[0]));
	}

	@Test void aWiderPanelSharesTheExtraOut() {
		int[] cols = {90, 70};
		int total = GridLayout.width(cols) + 21;
		assertArrayEquals(new int[] {100, 81}, GridLayout.stretch(cols, total));
		assertEquals(total, GridLayout.width(GridLayout.stretch(cols, total)));
		assertArrayEquals(cols, GridLayout.stretch(cols, 10)); // never narrower
	}
}
