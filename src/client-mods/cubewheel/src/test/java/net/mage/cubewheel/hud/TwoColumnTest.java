package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class TwoColumnTest {
	/** Like Minecraft's font for plain letters: 6 px a character. */
	private static final ToIntFunction<String> W = s -> s.length() * 6;

	@Test void theSideColumnIsClampedToItsRange() {
		assertEquals(TwoColumn.SIDE_MIN_W, TwoColumn.sideWidth(10));
		assertEquals(80, TwoColumn.sideWidth(80));
		assertEquals(TwoColumn.SIDE_MAX_W, TwoColumn.sideWidth(500));
	}

	@Test void theBoxIsBothColumnsAndTheGap() {
		assertEquals(100 + TwoColumn.GAP + 70, TwoColumn.width(100, 70));
		assertEquals(100, TwoColumn.width(100, 0)); // no side column
		assertEquals(30, TwoColumn.leftWidth(30 + TwoColumn.GAP + 70, 70));
		assertEquals(30, TwoColumn.leftWidth(30, 0));
	}

	@Test void shortTextIsOneLine() {
		assertEquals(List.of("+20% MCMMO Boost"), TwoColumn.wrap("+20% MCMMO Boost", 96, W, 2));
	}

	@Test void wrapsAtWords() {
		// 16 characters a line.
		assertEquals(List.of("Mobs drop 3x", "more EXP"), TwoColumn.wrap("Mobs drop 3x more EXP", 96, W, 2));
	}

	@Test void whatDoesNotFitEndsWithAnEllipsis() {
		List<String> out = TwoColumn.wrap("-50% Armor Effect Cooldowns · Snowy Particles", 96, W, 2);
		assertEquals(2, out.size());
		assertEquals("-50% Armor", out.get(0));
		assertTrue(out.get(1).endsWith("…"), out.get(1));
		for (String line : out) assertTrue(W.applyAsInt(line) <= 96, line);
	}

	@Test void aWordLongerThanTheLineIsCut() {
		List<String> out = TwoColumn.wrap("Supercalifragilistic", 60, W, 2);
		assertEquals(List.of("Supercalif", "ragilistic"), out);
		List<String> one = TwoColumn.wrap("Supercalifragilistic", 60, W, 1);
		assertEquals(List.of("Supercali…"), one);
	}

	@Test void nothingToWrap() {
		assertEquals(List.of(), TwoColumn.wrap("", 60, W, 2));
		assertEquals(List.of(), TwoColumn.wrap(null, 60, W, 2));
		assertEquals(List.of(), TwoColumn.wrap("text", 60, W, 0));
	}
}
