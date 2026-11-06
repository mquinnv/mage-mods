package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.mage.cubewheel.sidebar.Gamemode;
import org.junit.jupiter.api.Test;

class MenuClassifierTest {
	@Test void otherGamemodeNeverClassifies() {
		assertEquals(Optional.empty(), MenuClassifier.classify(Gamemode.OTHER, ManaCubeMenusTest.GLYPH_TITLE,
				ManaCubeMenusTest.PRESTIGE_RANKS, ManaCubeMenusTest.TITLE_SOURCES));
	}

	@Test void survivalMatchesTheThreeArgumentPath() {
		assertEquals(MenuClassifier.classify(ManaCubeMenusTest.GLYPH_TITLE, ManaCubeMenusTest.PRESTIGE_RANKS,
				ManaCubeMenusTest.TITLE_SOURCES), MenuClassifier.classify(Gamemode.SURVIVAL,
				ManaCubeMenusTest.GLYPH_TITLE, ManaCubeMenusTest.PRESTIGE_RANKS, ManaCubeMenusTest.TITLE_SOURCES));
		assertEquals(Optional.of("prestige"), MenuClassifier.classify(Gamemode.SURVIVAL,
				ManaCubeMenusTest.GLYPH_TITLE, ManaCubeMenusTest.PRESTIGE_RANKS, ManaCubeMenusTest.TITLE_SOURCES));
	}

	// wiki wording, replace with capture
	@Test void skyblockUsesItsOwnRules() {
		assertEquals(Optional.of("tasks"), MenuClassifier.classify(Gamemode.SKYBLOCK, "x",
				List.of(SkyblockMenusTest.item(1, "Daily Task", "Task: Kill 50 Zombies", "12/50")), Map.of()));
	}

	@Test void skyblockFallsBackToTwoProgressItems() {
		assertEquals(Optional.of("menu"), MenuClassifier.classify(Gamemode.SKYBLOCK, "x", List.of(
				SkyblockMenusTest.item(1, "A", "Progress: 1/2"), SkyblockMenusTest.item(2, "B", "Progress: 3/4")),
				Map.of()));
	}
}
