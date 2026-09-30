package com.mage.cubewheel.tracker;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mage.cubewheel.tracker.ContainerScanner.ItemView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real ManaCube Survival menu contents, captured 2026-09-30 (titles are custom-font glyphs, not text). */
class ManaCubeMenusTest {
	@TempDir Path dir;

	static final String GLYPH_TITLE = "⻔⻔⻔⻔⻔⻔⻔⻔䮄";
	static final Map<String, String> TITLE_SOURCES = Map.of("jobs", "(?i)jobs", "pquests", "(?i)quest");

	static ItemView item(int slot, String name, String... lore) {
		return new ItemView(slot, "minecraft:paper", name, List.of(lore));
	}

	static final List<ItemView> PRESTIGE_RANKS = List.of(
			item(11, "Rank [✪1]", "", "OBJECTIVE", "Harvest 21,250 Crops [49,394/21,250]", "", "COMPLETED",
					"➟ Read the next rankup objective"),
			item(14, "Rank [✪4]", "", "OBJECTIVE", "Reach 2,500 Skill Level [Lvl 1851/2,500]", "", "➟ Click to rankup"),
			item(20, "Rank [✪6]", "", "OBJECTIVE", "Slay 1,000 Monsters [4,377/1,000]", "",
					"Slay monsters in one of the", "special worlds (/worlds)", "", "➟ Click to rankup"));

	static final List<ItemView> PARTY_QUESTS = List.of(
			item(0, "Beginner Quests", "", "Complete all beginner quests", "to receive these rewards", "", "Rewards:",
					"● +20 Claims", "● +1 Party Warp", "", "COMPLETED"),
			item(2, "Discoverer", "Beginner Quest", "", "100% → Discover Wolfhaven", "40% → Discover Huey", "",
					"Progress: 70%", "", "Rewards:", "● $10,000"),
			item(4, "Miner", "Beginner Quest", "", "→ Mine 300 Stone", "", "Progress: 100%", "", "Rewards:",
					"● 1,000 Mana", "", "QUEST COMPLETED"),
			item(3, "Icey Lands", "", "Complete previous tier to", "unlock these insane quests"));

	static final List<ItemView> JOBS_MAIN = List.of(
			item(10, "JOBS PROFILE", "Jobs Completed: 52", "Chests Opened: 10", "Money Earned: $6,733,000"),
			item(13, "FARMING INDUSTRY", "", "Jobs Completed: 16", "Streak: 16", "", "㎋ Browse Job Listings"));

	static final List<ItemView> WARPS = List.of(
			item(4, "WORLDS", "◎ Overworld", "", "Land Claiming: Enabled", "Border Size: 40k x 40k",
					"➟ Teleport [Left-Click]"),
			item(5, "MANA WORLDS", "◎ Wolfhaven", "◎ Tangleroot", "➟ Select World"));

	@Test void glyphTitlesAreClassifiedByContent() {
		assertEquals(Optional.of("prestige"), MenuClassifier.classify(GLYPH_TITLE, PRESTIGE_RANKS, TITLE_SOURCES));
		assertEquals(Optional.of("pquests"), MenuClassifier.classify(GLYPH_TITLE, PARTY_QUESTS, TITLE_SOURCES));
		assertEquals(Optional.of("jobs"), MenuClassifier.classify(GLYPH_TITLE, JOBS_MAIN, TITLE_SOURCES));
	}

	@Test void unrelatedMenusAreNotTracked() {
		assertEquals(Optional.empty(), MenuClassifier.classify(GLYPH_TITLE, WARPS, TITLE_SOURCES));
	}

	@Test void configuredTitlePatternStillWins() {
		assertEquals(Optional.of("jobs"), MenuClassifier.classify("Your Jobs", WARPS, TITLE_SOURCES));
	}

	@Test void unknownMenuWithSeveralProgressItemsIsTrackedGenerically() {
		List<ItemView> listings = List.of(
				item(10, "Carrot Harvester", "Hand in carrots", "Progress: 120/500"),
				item(11, "Wheat Grinder", "Hand in wheat", "Progress: 40/200"));
		assertEquals(Optional.of("menu"), MenuClassifier.classify(GLYPH_TITLE, listings, TITLE_SOURCES));
	}

	@Test void prestigeObjectivesAreTrackedWithReadableNames() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		assertEquals(3, ContainerScanner.scan("prestige", PRESTIGE_RANKS, store, 0));
		Trackable skills = find(store, "Rank [✪4] · Reach 2,500 Skill Level");
		assertEquals(1851, skills.current(), 1e-9);
		assertEquals(2500, skills.max(), 1e-9);
		assertTrue(find(store, "Rank [✪1] · Harvest 21,250 Crops").complete());
	}

	@Test void partyQuestsUseTheProgressLineAndCompletionMarker() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		assertEquals(2, ContainerScanner.scan("pquests", PARTY_QUESTS, store, 0));
		Trackable discoverer = find(store, "Discoverer");
		assertEquals(70, discoverer.current(), 1e-9); // the Progress line, not the first sub-objective's 100%
		assertTrue(find(store, "Miner").complete());
		assertTrue(store.all().stream().noneMatch(t -> t.name().equals("Icey Lands") || t.name().equals("Beginner Quests")));
	}

	@Test void progressLineIsPreferredOverOtherNumbers() {
		assertEquals(Optional.of(new ProgressExtractor.Progress(70, 100)),
				ProgressExtractor.extract(List.of("100% → Discover Wolfhaven", "Progress: 70%")));
		assertEquals(Optional.of(new ProgressExtractor.Progress(3, 8)),
				ProgressExtractor.extract(List.of("Reward: 1/2 claims", "Progress: 3/8")));
	}

	static Trackable find(TrackerStore store, String name) {
		return store.all().stream().filter(t -> t.name().equals(name)).findFirst()
				.orElseThrow(() -> new AssertionError("no trackable named '" + name + "' in " + store.all()));
	}
}
