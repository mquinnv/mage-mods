package net.mage.cubewheel.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillsMenuTest {
	/** name, level, cap in captured slot order (2026-10-01). */
	private static final Object[][] CAPTURED = {
			{"HERBALISM ▪ [GATHERING]", 137, 2500},
			{"ACROBATICS ▪ [MISC]", 134, 500},
			{"ALCHEMY ▪ [MISC]", 7, 500},
			{"REPAIR ▪ [MISC]", 7, 500},
			{"SMELTING ▪ [MISC]", 150, 3000},
			{"MACES ▪ [COMBAT]", 40, 1000},
			{"CROSSBOWS ▪ [COMBAT]", 17, 1000},
			{"TRIDENT ▪ [COMBAT]", 26, 1000},
			{"SWORDS ▪ [COMBAT]", 247, 1000},
			{"MINING ▪ [GATHERING]", 293, 2500},
			{"AXES ▪ [COMBAT]", 131, 1000},
			{"WOODCUTTING ▪ [GATHERING]", 196, 2500},
			{"ARCHERY ▪ [COMBAT]", 44, 1000},
			{"EXCAVATION ▪ [GATHERING]", 109, 2500},
			{"TAMING ▪ [COMBAT]", 500, 500},
			{"FISHING ▪ [GATHERING]", 75, 1000},
			{"UNARMED ▪ [COMBAT]", 25, 1000},
	};

	private static SkillsMenu.Item skillItem(String name, int level, int cap) {
		List<String> lore = new ArrayList<>(List.of("Some skill description.", "", "Level: " + level + "/" + cap, "",
				"Abilities:", "  [✔] Something - Chance of find rare items", "", "㎋ Click to open Abilities Menu"));
		if (name.startsWith("SMELTING")) {
			lore.add(2, "This Skill is a Child Skill. It does not count to your total skill level.");
		}
		return new SkillsMenu.Item(name, lore);
	}

	private static List<SkillsMenu.Item> captured() {
		List<SkillsMenu.Item> items = new ArrayList<>();
		for (Object[] row : CAPTURED) items.add(skillItem((String) row[0], (int) row[1], (int) row[2]));
		items.add(new SkillsMenu.Item("General Information", List.of("Some info")));
		items.add(new SkillsMenu.Item("Guides & Settings", List.of()));
		items.add(new SkillsMenu.Item("Go Back", List.of()));
		return items;
	}

	@Test
	void parsesTheSeventeenCapturedItems() {
		List<SkillsMenu.Entry> entries = SkillsMenu.parse(captured());
		assertEquals(17, entries.size());
		for (int i = 0; i < CAPTURED.length; i++) {
			SkillsMenu.Entry e = entries.get(i);
			Skill expected = Skill.byName(((String) CAPTURED[i][0]).split(" ")[0]).orElseThrow();
			assertEquals(expected, e.skill());
			assertEquals(CAPTURED[i][1], e.level());
			assertEquals(CAPTURED[i][2], e.cap());
			assertEquals(expected == Skill.SMELTING, e.child(), e.skill().name());
		}
	}

	@Test
	void parsesColouredNamesAndThousandsSeparators() {
		List<SkillsMenu.Entry> entries = SkillsMenu.parse(List.of(new SkillsMenu.Item("§6§lMINING ▪ [GATHERING]",
				List.of("§7Level: §a1,234§7/§a2,500"))));
		assertEquals(List.of(new SkillsMenu.Entry(Skill.MINING, 1234, 2500, false)), entries);
	}

	@Test
	void itemWithoutLevelLineIsSkipped() {
		assertTrue(SkillsMenu.parse(List.of(new SkillsMenu.Item("MINING ▪ [GATHERING]", List.of("no level")))).isEmpty());
	}

	@Test
	void unknownSkillOrCategoryIsSkipped() {
		assertTrue(SkillsMenu.parse(List.of(
				new SkillsMenu.Item("COOKING ▪ [GATHERING]", List.of("Level: 1/2")),
				new SkillsMenu.Item("MINING ▪ [OTHER]", List.of("Level: 1/2")))).isEmpty());
	}

	@Test
	void isSkillsMenuNeedsTwoEntries() {
		assertTrue(SkillsMenu.isSkillsMenu(captured()));
		assertFalse(SkillsMenu.isSkillsMenu(List.of()));
		assertFalse(SkillsMenu.isSkillsMenu(List.of(skillItem("MINING ▪ [GATHERING]", 293, 2500))));
		assertTrue(SkillsMenu.isSkillsMenu(List.of(skillItem("MINING ▪ [GATHERING]", 293, 2500),
				skillItem("AXES ▪ [COMBAT]", 131, 1000))));
	}
}
