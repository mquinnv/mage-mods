package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SingularTest {
	@Test void words() {
		assertEquals("crop", Singular.word("crops"));
		assertEquals("wolf", Singular.word("wolves"));
		assertEquals("enderman", Singular.word("endermen"));
		assertEquals("potato", Singular.word("potatoes"));
		assertEquals("berry", Singular.word("berries"));
		assertEquals("fish", Singular.word("fish"));
		assertEquals("glass", Singular.word("glass"));
		assertEquals("box", Singular.word("boxes"));
		assertEquals("sheep", Singular.word("sheep"));
		assertEquals("stone", Singular.word("stone"));
		assertEquals("tangleroot", Singular.word("Tangleroots"));
		assertEquals("zombie", Singular.word("Zombies"));
		assertEquals("blaze", Singular.word("Blazes"));
		assertEquals("slime", Singular.word("slimes"));
		assertEquals("glass", Singular.word("glasses"));
	}

	@Test void phraseSingularisesTheLastWordAndNormalisesSpace() {
		assertEquals("mana wolf", Singular.phrase("Mana  Wolves"));
		assertEquals("sweet berry bush", Singular.phrase("sweet_berry_bush"));
		assertEquals("", Singular.phrase(null));
	}
	/** Latin and French loan words ending in -s are singular already. */
	@Test void lapisAndDebrisAreNotPlurals() {
		assertEquals("lapis", Singular.word("Lapis"));
		assertEquals("deepslate lapis ore", Singular.phrase("deepslate_lapis_ore"));
		assertEquals("ancient debris", Singular.phrase("Ancient Debris"));
	}

	/**
	 * "Slay 96 Morendmen in Morend" (job, capture 2026-11-03) hunts mobs named "Morendman 150⺛": any "-men" noun is
	 * the plural of a "-man" one. Words that merely end in "men" ("specimen", a Bad Omen) are left alone.
	 */
	@Test void menIsThePluralOfMan() {
		assertEquals("morendman", Singular.word("Morendmen"));
		assertEquals("snowman", Singular.word("snowmen"));
		assertEquals("man", Singular.word("men"));
		assertEquals("specimen", Singular.word("specimen"));
		assertEquals("omen", Singular.word("Omen"));
		assertEquals("abdomen", Singular.word("abdomen"));
	}
}
