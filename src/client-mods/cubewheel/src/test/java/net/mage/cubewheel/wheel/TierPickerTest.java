package net.mage.cubewheel.wheel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TierPickerTest {
	private final Object sell = new Object();
	private final Object crops = new Object();

	@Test void distanceAloneDecides() {
		TierPicker p = new TierPicker();
		assertEquals(0, p.pick(sell, 0, 2, false));
		assertEquals(1, p.pick(sell, 1, 2, false));
		assertEquals(0, p.pick(null, 0, 0, false));
	}

	@Test void scrollStepsOutwardAndBackClamped() {
		TierPicker p = new TierPicker();
		p.pick(sell, 0, 2, false);
		p.scroll(1, 2);
		assertEquals(1, p.pick(sell, 0, 2, false));
		p.scroll(1, 2);
		p.scroll(1, 2);
		assertEquals(2, p.pick(sell, 0, 2, false));
		p.scroll(-1, 2);
		p.scroll(-1, 2);
		p.scroll(-1, 2);
		assertEquals(0, p.pick(sell, 0, 2, false));
	}

	@Test void scrollIsForgottenOnAnotherSliceOrDistanceTier() {
		TierPicker p = new TierPicker();
		p.pick(sell, 0, 2, false);
		p.scroll(1, 2);
		assertEquals(0, p.pick(crops, 0, 1, false));
		assertEquals(0, p.pick(sell, 0, 2, false));
		p.scroll(1, 2);
		assertEquals(2, p.pick(sell, 2, 2, false)); // moved out by distance: distance wins again
	}

	@Test void scrollStartsFromTheDistanceTier() {
		TierPicker p = new TierPicker();
		p.pick(sell, 1, 2, false);
		p.scroll(1, 2);
		assertEquals(2, p.pick(sell, 1, 2, false));
	}

	@Test void shiftWinsAndPlainSlicesIgnoreScroll() {
		TierPicker p = new TierPicker();
		p.pick(sell, 0, 2, false);
		p.scroll(-1, 2);
		assertEquals(2, p.pick(sell, 0, 2, true));
		p.pick(crops, 0, 0, false);
		p.scroll(1, 0);
		assertEquals(0, p.pick(crops, 0, 0, false));
	}
}
