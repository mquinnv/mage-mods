package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.mage.cubewheel.tracker.ContainerScanner.ItemView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Values a menu states about the player (real /party menu, 2026-09-30) keep matching objectives current. */
class MenuFactsTest {
	@TempDir Path dir;

	static final List<ItemView> PARTY_MENU = List.of(
			new ItemView(6, "minecraft:paper", "Party Level",
					List.of("Level Up your party to unlock", "better spawners & team quests", "", "㎋ Click to Level Up")),
			new ItemView(29, "minecraft:paper", "Purchase 1 Level", List.of("Upgrade Party Level", "", "Cost: $550,000",
					"Current Level: 55", "Target Level: 56", "", "Click purchase 1 level")),
			new ItemView(32, "minecraft:paper", "Unlocked Spawners",
					List.of("", "Level 1: Pig, Zombie, Spider", "Level 50: Enderman")));

	@Test void partyMenuStatesThePartyLevel() {
		assertEquals(Map.of("Party Level", 55.0), MenuFacts.of(PARTY_MENU));
	}

	@Test void unrelatedMenusStateNothing() {
		assertEquals(Map.of(), MenuFacts.of(List.of(new ItemView(1, "minecraft:paper", "Carrot",
				List.of("Current Level: 3")))));
	}

	@Test void partyLevelCompletesTheRankObjectiveAndReleasesItsPin() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		String name = "Rank [✪9] · Reach Party Level 55";
		s.update("prestige", name, new ProgressExtractor.Progress(51, 55), 0);
		s.togglePin(Trackable.idOf("prestige", name));
		assertEquals(1, MenuFacts.apply(MenuFacts.of(PARTY_MENU), s, 1_000));
		Trackable t = s.all().get(0);
		assertEquals(55, t.current(), 1e-9);
		assertTrue(t.complete());
		assertFalse(s.isPinned(t.id()));
		assertEquals(List.of(name), s.drainCompletions());
	}

	@Test void otherLevelObjectivesAreLeftAlone() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("pquests", "Experienced Party", new ProgressExtractor.Progress(0, 100), 0);
		s.update("prestige", "Rank [✪4] · Reach 2,500 Skill Level", new ProgressExtractor.Progress(1900, 2500), 0);
		assertEquals(0, MenuFacts.apply(MenuFacts.of(PARTY_MENU), s, 1_000));
	}
}
