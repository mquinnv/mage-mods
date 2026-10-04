package net.mage.cubewheel.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatusTest {
	private static final List<String> DRAGON_LORE = List.of("§6Dragon Armor Set:", "§7➤ Drops 2x more heads",
			"§7➤ Permanent Speed II Effect", "§e(Requires 4/4 pieces)");

	private static List<List<String>> lores(int pieces, List<String> lore) {
		List<List<String>> out = new ArrayList<>();
		for (int i = 0; i < 4; i++) out.add(i < pieces ? lore : List.of());
		return out;
	}

	@Test void setBonusMetWhenAllFourWorn() {
		ArmorSet s = ArmorSet.of(List.of("DRAGON HELMET", "DRAGON CHESTPLATE", "DRAGON LEGGINGS", "DRAGON BOOTS"),
				lores(4, DRAGON_LORE));
		assertEquals(4, s.required());
		assertFalse(s.bonusUnmet());
	}

	@Test void setBonusUnmetAtThreeOfFour() {
		ArmorSet s = ArmorSet.of(Arrays.asList("DRAGON HELMET", "DRAGON CHESTPLATE", "DRAGON LEGGINGS", null),
				lores(3, DRAGON_LORE));
		assertTrue(s.bonusUnmet());
	}

	@Test void requirementOnAnyPieceOfTheShownSetCounts() {
		ArmorSet s = ArmorSet.of(Arrays.asList("DRAGON HELMET", "DRAGON CHESTPLATE", "DRAGON LEGGINGS", null),
				Arrays.asList(List.of(), List.of(), DRAGON_LORE, List.of()));
		assertTrue(s.bonusUnmet());
	}

	@Test void noRequirementLineMeansNoBonus() {
		List<String> plain = List.of("§7Just armor", "§7Protection IV");
		ArmorSet s = ArmorSet.of(Arrays.asList("DRAGON HELMET", "DRAGON CHESTPLATE", null, null), lores(2, plain));
		assertEquals(0, s.required());
		assertFalse(s.bonusUnmet());
		assertFalse(ArmorSet.of(List.of("DRAGON HELMET")).bonusUnmet());
	}

	@Test void twoOfFourRequirementMetWithTwoWorn() {
		List<String> lore = List.of("Wolf Armor Set:", "(Requires 2/4 pieces)");
		ArmorSet s = ArmorSet.of(Arrays.asList("WOLF HELMET", "WOLF BOOTS", null, null), lores(2, lore));
		assertEquals(2, s.required());
		assertFalse(s.bonusUnmet());
	}

	@Test void requirementOfAnotherSetIsIgnored() {
		// Shown set is Dragon (2 pieces); the lone Wolf piece's requirement must not apply to it.
		ArmorSet s = ArmorSet.of(Arrays.asList("DRAGON HELMET", "DRAGON BOOTS", "WOLF LEGGINGS", null),
				Arrays.asList(List.of(), List.of(), List.of("(Requires 4/4 pieces)"), List.of()));
		assertEquals("Dragon", s.name());
		assertFalse(s.bonusUnmet());
	}

	@Test void fullCustomSet() {
		ArmorSet s = ArmorSet.of(List.of("PHOENIX HELMET", "PHOENIX CHESTPLATE", "PHOENIX LEGGINGS", "PHOENIX BOOTS"));
		assertEquals("Phoenix", s.name());
		assertEquals("4/4", s.count());
	}

	@Test void mixedAndPartialSets() {
		ArmorSet s = ArmorSet.of(Arrays.asList("Diamond Helmet", "Netherite Chestplate", "Diamond Leggings", null));
		assertEquals("Diamond", s.name());
		assertEquals(2, s.matching());
		assertEquals(3, s.worn());
		assertEquals(ArmorSet.NONE, ArmorSet.of(Arrays.asList(null, null, "", null)));
		assertEquals("Mana", ArmorSet.base("§bMana Boots of Speed"));
		assertEquals("Golden", ArmorSet.base("✦ GOLDEN HELMET ✦"));
	}

	@Test void formats() {
		assertEquals("6:00 AM", StatusFormat.gameTime(0));
		assertEquals("12:00 PM", StatusFormat.gameTime(6_000));
		assertEquals("3:00 PM", StatusFormat.gameTime(9_000));
		assertEquals("12:00 AM", StatusFormat.gameTime(18_000));
		assertEquals("6:00 AM", StatusFormat.gameTime(24_000 * 5));
		assertEquals("5:09 PM", StatusFormat.clock(17, 9));
		assertEquals("Dark Forest", StatusFormat.biome("minecraft:dark_forest"));
		assertEquals("West -X", StatusFormat.facing("west"));
		assertEquals("0.00 m/s", StatusFormat.speed(-1));
	}
}
