package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import net.mage.cubewheel.tracker.ContainerScanner.ItemView;
import org.junit.jupiter.api.Test;

/** Fixtures come from Michael's first Skyblock capture (2026-11-06, see {@link SkyblockCapture}). */
class SkyblockMenusTest {
	static ItemView item(int slot, String name, String... lore) {
		return new ItemView(slot, "minecraft:paper", name, List.of(lore));
	}

	private static Optional<String> classify(ItemView... items) {
		return SkyblockMenus.classify(List.of(items));
	}

	private static Optional<String> classify(String capture) {
		return SkyblockMenus.classify(SkyblockCapture.load(capture));
	}

	// --- the captured menus ---

	@Test void capturedRankPageIsPrestige() {
		assertEquals(Optional.of("prestige"), classify(SkyblockCapture.PRESTIGE_RANKS));
	}

	/** Survival's own rule: the list page classifies, and its items carry no counter, so it gives no entries. */
	@Test void capturedPrestigeListIsPrestige() {
		assertEquals(Optional.of("prestige"), classify(SkyblockCapture.PRESTIGE_LIST));
	}

	@Test void capturedIslandTeamQuestsAreIsquests() {
		assertEquals(Optional.of("isquests"), classify(SkyblockCapture.ISLAND_QUESTS));
	}

	@Test void capturedTaskBoardIsTasks() {
		assertEquals(Optional.of("tasks"), classify(SkyblockCapture.TASK_BOARD));
	}

	@Test void capturedSeasonChallengesAreSbChallenges() {
		assertEquals(Optional.of("sbchallenges"), classify(SkyblockCapture.SEASON_CHALLENGES));
	}

	@Test void capturedHubAndBossChallengesAreNothing() {
		assertEquals(Optional.empty(), classify(SkyblockCapture.HUB));
		assertEquals(Optional.empty(), classify(SkyblockCapture.HUB_WITHOUT_QUESTS));
		assertEquals(Optional.empty(), classify(SkyblockCapture.BOSS_CHALLENGES));
	}

	// --- single-item rules ---

	@Test void rankAndPrestigeLevelArePrestige() {
		assertEquals(Optional.of("prestige"), classify(item(11, "Rank [✪1]", "", "OBJECTIVE",
				"Harvest 25,000 crops [0/25,000]", "", "➟ Click to rankup")));
		assertEquals(Optional.of("prestige"), classify(item(11, "Prestige 1 - Not Completed", "",
				"Complete all 10 challenges to", "level up to Prestige [✦1]")));
	}

	@Test void aListingNamedLikeARankIsNotPrestige() {
		assertEquals(Optional.empty(), classify(item(1, "Rank 1 Voucher", "50%")));
	}

	/** A quest outside a tier header's page still says its tier ("Beginner Quest") and has a Progress line. */
	@Test void islandQuestByItsTierLore() {
		assertEquals(Optional.of("isquests"), classify(item(5, "Fisherman", "Beginner Quest", "", "→ Catch 25 Fish", "",
				"Progress: 12%")));
		assertEquals(Optional.empty(), classify(item(5, "Fisherman", "Beginner Quest", "", "→ Catch 25 Fish")));
	}

	@Test void taskNeedsItsCounter() {
		assertEquals(Optional.of("tasks"), classify(item(10, "SLAYER TASK", "", "Slay 76 Mana Slimes in Wolfhaven",
				"⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛ (0/76)", "", "Time Left: 23h 59m 36s")));
		assertEquals(Optional.empty(), classify(item(10, "SLAYER TASK", "", "Slay 76 Mana Slimes in Wolfhaven")));
	}

	/** The hub's Task Board button talks about tasks but is no task. */
	@Test void taskIsTheItemNameNotItsLore() {
		assertEquals(Optional.empty(), classify(item(2, "Task Board", "", "Complete Adventure/Slayer tasks", "3/5")));
		assertEquals(Optional.empty(), classify(item(1, "Focus", "multitasking", "3/5")));
	}

	/** Tasks are named in capitals; a "Reroll Task" style button with a counter must not pass for one. */
	@Test void taskNameIsUpperCaseOnly() {
		assertEquals(Optional.empty(), classify(item(4, "Reroll Task", "Rerolls left (2/3)")));
		assertEquals(Optional.of("tasks"), classify(item(4, "SLAYER TASK", "", "Slay 76 Mana Slimes in Wolfhaven", "(0/76)")));
	}

	/** A challenge head alone is not the Season Challenges menu: its Current Level pane must follow. */
	@Test void challengeHeadNeedsItsCurrentLevel() {
		ItemView head = item(0, "Mob Challenge", "", "Total Completions: 0.0");
		ItemView current = item(2, "Current Level (1)", "Kill 164 / 3,500,000 Mobs");
		assertEquals(Optional.of("sbchallenges"), classify(head, current));
		assertEquals(Optional.empty(), classify(head));
		assertEquals(Optional.empty(), classify(head, item(3, "Next Level (2)", "Kill 164 / 4,000,000 Mobs")));
		assertEquals(Optional.empty(), classify(item(7, "Season Challenges", "Complete season-long challenges"), current));
	}

	@Test void onlyFillerIsEmpty() {
		assertEquals(Optional.empty(), classify(
				new ItemView(0, "minecraft:gray_stained_glass_pane", "", List.of()),
				new ItemView(1, "minecraft:arrow", "Back", List.of()),
				new ItemView(2, "minecraft:barrier", "Close", List.of())));
	}

	/** The hub's nav button (captured with a "Keystone" lore line). */
	@Test void navButtonIsNothing() {
		assertEquals(Optional.empty(), classify(new ItemView(22, "minecraft:arrow", "Go Back", List.of("Keystone"))));
	}
}
