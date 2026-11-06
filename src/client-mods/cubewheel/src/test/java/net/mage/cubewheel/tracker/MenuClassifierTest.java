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

	@Test void skyblockUsesItsOwnRules() {
		assertEquals(Optional.of("tasks"), MenuClassifier.classify(Gamemode.SKYBLOCK, "⻔⻔⻔⻔⻔⻔⻔⻔㑌",
				SkyblockCapture.load(SkyblockCapture.TASK_BOARD), Map.of()));
		assertEquals(Optional.of("isquests"), MenuClassifier.classify(Gamemode.SKYBLOCK, "⻔⻔⻔⻔⻔⻔⻔⻔㈵",
				SkyblockCapture.load(SkyblockCapture.ISLAND_QUESTS), Map.of()));
		assertEquals(Optional.of("sbchallenges"), MenuClassifier.classify(Gamemode.SKYBLOCK, "",
				SkyblockCapture.load(SkyblockCapture.SEASON_CHALLENGES), Map.of()));
	}

	/** Michael 2026-11-06 capture: Skyblock's prestige pages are Survival's, and classify the same way. */
	@Test void skyblockPrestigeMatchesSurvival() {
		for (String capture : List.of(SkyblockCapture.PRESTIGE_RANKS, SkyblockCapture.PRESTIGE_LIST)) {
			List<ContainerScanner.ItemView> items = SkyblockCapture.load(capture);
			assertEquals(Optional.of("prestige"), MenuClassifier.classify(Gamemode.SKYBLOCK, "x", items, Map.of()));
			assertEquals(Optional.of("prestige"), MenuClassifier.classify(Gamemode.SURVIVAL, "x", items, Map.of()));
		}
	}

	/** The /jobs hub and Boss Challenges show no counters, so a refresh run never reads them as a tracker menu. */
	@Test void skyblockHubAndBossChallengesAreNotTracked() {
		for (String capture : List.of(SkyblockCapture.HUB, SkyblockCapture.HUB_WITHOUT_QUESTS,
				SkyblockCapture.BOSS_CHALLENGES)) {
			assertEquals(Optional.empty(), MenuClassifier.classify(Gamemode.SKYBLOCK, "x",
					SkyblockCapture.load(capture), Map.of()), capture);
		}
	}

	@Test void skyblockFallsBackToTwoProgressItems() {
		assertEquals(Optional.of("menu"), MenuClassifier.classify(Gamemode.SKYBLOCK, "x", List.of(
				SkyblockMenusTest.item(1, "A", "Progress: 1/2"), SkyblockMenusTest.item(2, "B", "Progress: 3/4")),
				Map.of()));
	}

	@Test void skyblockIgnoresSurvivalTitlePatterns() {
		Map<String, String> sources = Map.of("jobs", "(?i)jobs");
		List<ContainerScanner.ItemView> items = List.of(SkyblockMenusTest.item(1, "Deco", "nothing"));
		assertEquals(Optional.of("jobs"), MenuClassifier.classify(Gamemode.SURVIVAL, "Jobs", items, sources));
		assertEquals(Optional.empty(), MenuClassifier.classify(Gamemode.SKYBLOCK, "Jobs", items, sources));
	}
}
