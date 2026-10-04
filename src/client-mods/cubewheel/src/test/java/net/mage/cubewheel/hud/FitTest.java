package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class FitTest {
	/** 6 px a character, as Minecraft's font for plain letters. */
	private static final ToIntFunction<String> W = s -> s.length() * 6;

	@Test void fittingTextIsUnchanged() {
		assertEquals("Phoenix", Fit.cut("Phoenix", 90, W));
		assertEquals("", Fit.cut(null, 90, W));
	}

	@Test void longTextIsCutWithAnEllipsis() {
		// 15 characters fit in 90 px: 14 and "…".
		assertEquals("Enhanced Impro…", Fit.cut("Enhanced Improved Mana", 90, W));
		assertEquals("Enhanced…", Fit.cut("Enhanced Improved Mana", 60, W)); // no space before the "…"
	}

	@Test void noRoomGivesNothing() {
		assertEquals("", Fit.cut("Phoenix", 5, W));
	}
}
