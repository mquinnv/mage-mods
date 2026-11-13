package net.mage.cubewheel.compat.jade;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * A mana mob's tag health as Jade writes health numbers: at most one decimal, no trailing zero ("75", "12.5").
 * Pure (no Jade or Minecraft imports) so it is testable without Jade on the test classpath.
 */
final class HealthText {
	private HealthText() {}

	static String format(double health) {
		DecimalFormat f = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
		f.setRoundingMode(RoundingMode.HALF_UP);
		return f.format(health);
	}
}
