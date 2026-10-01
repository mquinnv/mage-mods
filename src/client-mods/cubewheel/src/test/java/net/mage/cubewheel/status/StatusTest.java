package net.mage.cubewheel.status;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatusTest {
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
