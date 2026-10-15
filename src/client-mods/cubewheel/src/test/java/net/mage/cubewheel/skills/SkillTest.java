package net.mage.cubewheel.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkillTest {
	@Test void seventeenMenuSkillsAndOnlySmeltingIsChild() {
		assertEquals(17, Skill.values().length);
		for (Skill s : Skill.values()) assertEquals(s == Skill.SMELTING, s.child, s.name());
	}

	@Test void fieldsMatchTheCapturedMenu() {
		assertEquals("Mining", Skill.MINING.label);
		assertEquals("MINING", Skill.MINING.menuName);
		assertEquals(Skill.Category.GATHERING, Skill.MINING.category);
		assertEquals(2500, Skill.MINING.defaultCap);
		assertEquals("minecraft:golden_pickaxe", Skill.MINING.iconId);
		assertEquals("Trident", Skill.TRIDENT.label);
		assertEquals("TRIDENT", Skill.TRIDENT.menuName);
		assertEquals(Skill.Category.COMBAT, Skill.TRIDENT.category);
		assertEquals(1000, Skill.TRIDENT.defaultCap);
		assertEquals(500, Skill.TAMING.defaultCap);
		assertEquals(3000, Skill.SMELTING.defaultCap);
		assertEquals(Skill.Category.MISC, Skill.SMELTING.category);
		assertEquals("minecraft:netherite_axe", Skill.UNARMED.iconId);
		assertEquals(500, Skill.ACROBATICS.defaultCap);
		assertEquals(500, Skill.ALCHEMY.defaultCap);
		assertEquals(500, Skill.REPAIR.defaultCap);
		assertEquals(1000, Skill.FISHING.defaultCap);
		assertEquals(2500, Skill.WOODCUTTING.defaultCap);
	}

	@Test void everySkillResolvesByLabelAndMenuName() {
		for (Skill s : Skill.values()) {
			assertEquals(Optional.of(s), Skill.byName(s.label), s.label);
			assertEquals(Optional.of(s), Skill.byName(s.menuName), s.menuName);
		}
	}

	@Test void byNameIsCaseInsensitiveTrimmedAndStripsFormatting() {
		assertEquals(Optional.of(Skill.MINING), Skill.byName("  mining "));
		assertEquals(Optional.of(Skill.MINING), Skill.byName("§a§lMining"));
		assertEquals(Optional.of(Skill.HERBALISM), Skill.byName("HeRbAlIsM"));
	}

	@Test void pluralAndSingularForms() {
		assertEquals(Optional.of(Skill.TRIDENT), Skill.byName("TRIDENTS"));
		assertEquals(Optional.of(Skill.TRIDENT), Skill.byName("TRIDENT"));
		assertEquals(Optional.of(Skill.MACES), Skill.byName("MACE"));
		assertEquals(Optional.of(Skill.MACES), Skill.byName("MACES"));
		assertEquals(Optional.of(Skill.CROSSBOWS), Skill.byName("CROSSBOW"));
		assertEquals(Optional.of(Skill.CROSSBOWS), Skill.byName("CROSSBOWS"));
		assertEquals(Optional.of(Skill.SWORDS), Skill.byName("SWORD"));
		assertEquals(Optional.of(Skill.SWORDS), Skill.byName("SWORDS"));
		assertEquals(Optional.of(Skill.AXES), Skill.byName("AXE"));
		assertEquals(Optional.of(Skill.AXES), Skill.byName("AXES"));
	}

	@Test void unknownNamesAreEmpty() {
		assertEquals(Optional.empty(), Skill.byName("MANA GOLEM"));
		assertEquals(Optional.empty(), Skill.byName(""));
		assertEquals(Optional.empty(), Skill.byName(null));
		assertFalse(Skill.byName("Mining Fatigue").isPresent());
		assertTrue(Skill.byName("Taming").isPresent());
	}
}
