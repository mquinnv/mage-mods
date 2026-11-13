package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class TagHealthTest {
	@Test void manaCubeGlyph() {
		assertEquals(OptionalDouble.of(75), TagHealth.parse("Viper 75⺛"));
	}

	@Test void heart() {
		assertEquals(OptionalDouble.of(20), TagHealth.parse("Mana Wolf ❤ 20"));
	}

	@Test void bracketedFraction() {
		assertEquals(OptionalDouble.of(12.5), TagHealth.parse("Mana Wolf [12.5/40]"));
	}

	@Test void noHealth() {
		assertEquals(OptionalDouble.empty(), TagHealth.parse("Mana Wolf"));
		assertEquals(OptionalDouble.empty(), TagHealth.parse(null));
	}

	@Test void stackCountIsNotHealth() {
		assertEquals(OptionalDouble.empty(), TagHealth.parse("3x Tiger"));
		assertEquals(OptionalDouble.empty(), TagHealth.parse("Tiger (x3)"));
	}

	@Test void stackedNameKeepsItsHealth() {
		assertEquals(OptionalDouble.of(40), TagHealth.parse("3x Mana Slime [40/40]"));
	}

	@Test void capturedTagLines() {
		assertEquals(OptionalDouble.of(60), TagHealth.parse("Mana Wolf 60⺛"));
		assertEquals(OptionalDouble.of(30), TagHealth.parse("Viper 30⺛"));
	}
}
