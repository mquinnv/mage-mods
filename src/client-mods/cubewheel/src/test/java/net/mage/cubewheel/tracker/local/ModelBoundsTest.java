package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class ModelBoundsTest {
	private static final double EPS = 1e-9;
	private static final ModelBounds UNIT = new ModelBounds(-0.5, 0, -0.5, 0.5, 1, 0.5);

	@Test void rayThroughTheBoxHitsAtItsNearFace() {
		OptionalDouble t = UNIT.clip(-3, 0.5, 0, 1, 0, 0, 10);
		assertTrue(t.isPresent());
		assertEquals(2.5, t.getAsDouble(), EPS);
	}

	@Test void rayMissingTheBoxIsEmpty() {
		assertFalse(UNIT.clip(-3, 1.5, 0, 1, 0, 0, 10).isPresent()); // passes above
		assertFalse(UNIT.clip(-3, 0.5, 0, -1, 0, 0, 10).isPresent()); // points away
	}

	@Test void boxBeyondReachIsEmpty() {
		assertFalse(UNIT.clip(-3, 0.5, 0, 1, 0, 0, 2.4).isPresent());
		assertTrue(UNIT.clip(-3, 0.5, 0, 1, 0, 0, 2.5).isPresent());
	}

	@Test void unnormalisedDirectionGivesDistanceNotParameter() {
		assertEquals(2.5, UNIT.clip(-3, 0.5, 0, 4, 0, 0, 10).getAsDouble(), EPS);
	}

	@Test void originInsideIsDistanceZero() {
		assertEquals(0, UNIT.clip(0, 0.5, 0, 0, 0, 1, 10).getAsDouble(), EPS);
	}

	@Test void diagonalRayHits() {
		OptionalDouble t = UNIT.clip(-2, 2.5, 0, 1, -1, 0, 10);
		assertTrue(t.isPresent()); // enters through the top face at x = -0.5, y = 1
		assertEquals(Math.sqrt(2) * 1.5, t.getAsDouble(), EPS);
	}

	@Test void unionInflateAndMove() {
		ModelBounds a = ModelBounds.point(0, 0, 0).union(ModelBounds.point(1, 2, -1));
		assertEquals(new ModelBounds(0, 0, -1, 1, 2, 0), a);
		assertEquals(new ModelBounds(-0.5, -0.5, -1.5, 1.5, 2.5, 0.5), a.inflate(0.5));
		assertEquals(new ModelBounds(10, 0, -1, 11, 2, 0), a.move(10, 0, 0));
	}

	@Test void aroundAxisCoversEveryYawOfATranslation() {
		ModelBounds b = ModelBounds.aroundAxis(0.6, 0.4, 0.8); // horizontal radius 1.0
		assertEquals(new ModelBounds(-1, 0.4, -1, 1, 0.4, 1), b);
	}

	@Test void containsAndVolume() {
		assertTrue(UNIT.contains(ModelBounds.point(0, 0, 0)));
		assertFalse(ModelBounds.point(0, 0, 0).contains(UNIT));
		assertEquals(1.0, UNIT.volume(), EPS);
	}
}
