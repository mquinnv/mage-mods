package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import net.mage.cubewheel.tracker.ContainerScanner.ItemView;
import org.junit.jupiter.api.Test;

/** Every fixture here is // wiki wording, replace with capture (https://wiki.manacube.com/skyblock/). */
class SkyblockMenusTest {
	static ItemView item(int slot, String name, String... lore) {
		return new ItemView(slot, "minecraft:paper", name, List.of(lore));
	}

	private static Optional<String> classify(ItemView... items) {
		return SkyblockMenus.classify(List.of(items));
	}

	// wiki wording, replace with capture
	@Test void rankIsPrestige() {
		assertEquals(Optional.of("prestige"),
				classify(item(1, "Rank 1", "Harvest 25,000 crops", "Progress: 1,200/25,000")));
	}

	// wiki wording, replace with capture
	@Test void bracketedRankAndPrestigeLevelArePrestige() {
		assertEquals(Optional.of("prestige"), classify(item(1, "Rank [✪3]", "x")));
		assertEquals(Optional.of("prestige"), classify(item(1, "Prestige 2", "x")));
	}

	// wiki wording, replace with capture
	@Test void mobChallengeIsSbChallenges() {
		assertEquals(Optional.of("sbchallenges"),
				classify(item(1, "Mob Challenge", "Level 1 - 3,500,000 Mobs", "120,000 / 3,500,000")));
		assertEquals(Optional.of("sbchallenges"), classify(item(1, "Mini Boss Challenge", "x")));
	}

	// wiki wording, replace with capture
	@Test void islandQuestsByNameAndByLore() {
		assertEquals(Optional.of("isquests"), classify(item(1, "Beginner Island Quests", "x")));
		assertEquals(Optional.of("isquests"),
				classify(item(1, "Miner", "Island Quest", "Mine 300 cobblestone", "120/300")));
	}

	// wiki wording, replace with capture
	@Test void taskWithCounterIsTasks() {
		assertEquals(Optional.of("tasks"), classify(item(1, "Daily Task", "Task: Kill 50 Zombies", "12/50")));
	}

	@Test void taskWithoutCounterIsNot() {
		assertEquals(Optional.empty(), classify(item(1, "Daily Task", "Task: Kill 50 Zombies")));
	}

	@Test void islandQuestsHubButtonDoesNotClassifyTheMenu() {
		assertEquals(Optional.empty(), classify(
				item(1, "Island Quests", "Click to view your Island Quests"),
				item(2, "Island Level", "Level: 12/50")));
	}

	@Test void taskIsAWordNotASubstring() {
		assertEquals(Optional.empty(), classify(item(1, "Focus", "multitasking", "3/5")));
	}

	@Test void onlyFillerIsEmpty() {
		assertEquals(Optional.empty(), classify(
				new ItemView(0, "minecraft:gray_stained_glass_pane", "", List.of()),
				new ItemView(1, "minecraft:arrow", "Back", List.of()),
				new ItemView(2, "minecraft:barrier", "Close", List.of())));
	}
}
