package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProgressExtractorTest {
	static Optional<ProgressExtractor.Progress> x(String... l) { return ProgressExtractor.extract(List.of(l)); }
	@Test void simpleRatio() { assertEquals(Optional.of(new ProgressExtractor.Progress(3, 10)), x("Progress: 3/10")); }
	@Test void commasAndSpaces() { assertEquals(Optional.of(new ProgressExtractor.Progress(12500, 20000)), x("Harvested 12,500 / 20,000 carrots")); }
	@Test void suffixes() { assertEquals(Optional.of(new ProgressExtractor.Progress(1500, 3000)), x("1.5k/3k")); }
	@Test void wordAfterNumberIsNotSuffix() { assertEquals(Optional.of(new ProgressExtractor.Progress(3, 10)), x("Kill 3/10 mobs")); }
	@Test void ofForm() { assertEquals(Optional.of(new ProgressExtractor.Progress(7, 25)), x("7 of 25 jobs")); }
	@Test void percentOnly() { assertEquals(Optional.of(new ProgressExtractor.Progress(45, 100)), x("Complete: 45%")); }
	@Test void ratioWinsOverPercent() { assertEquals(Optional.of(new ProgressExtractor.Progress(1, 4)), x("25%", "1/4")); }
	@Test void firstRatioLineWins() { assertEquals(Optional.of(new ProgressExtractor.Progress(2, 5)), x("Reward", "2/5", "9/9")); }
	@Test void noneFound() { assertEquals(Optional.empty(), x("Click to claim", "Reward: $500")); }
	@Test void zeroMaxIgnored() { assertEquals(Optional.empty(), x("0/0")); }
	@Test void gluedWordYieldsNoRatio() {
		assertEquals(Optional.empty(), x("3/10mobs"));
		assertEquals(Optional.empty(), x("10 of 20th"));
	}
	@Test void threePartDatesRejected() {
		assertEquals(Optional.empty(), x("2026/09/30"));
		assertEquals(Optional.empty(), x("Expires 09/30/2026"));
	}
	// known limitation: indistinguishable from progress
	@Test void twoPartDateAccepted() { assertEquals(Optional.of(new ProgressExtractor.Progress(12, 25)), x("12/25")); }
	@Test void percentRejectsGluedAndSigned() {
		assertEquals(Optional.empty(), x("1234%"));
		assertEquals(Optional.empty(), x("+15% sell price"));
	}
	@Test void nonFiniteRejected() {
		String huge = "9".repeat(400);
		assertEquals(Optional.empty(), x(huge + "/" + huge));
	}
	@Test void parseNumber() {
		assertEquals(2_000_000, ProgressExtractor.parseNumber("2M").getAsDouble(), 1e-9);
		assertTrue(ProgressExtractor.parseNumber("abc").isEmpty());
	}
}
