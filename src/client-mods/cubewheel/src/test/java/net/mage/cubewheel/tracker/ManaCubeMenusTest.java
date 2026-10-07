package net.mage.cubewheel.tracker;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.mage.cubewheel.tracker.ContainerScanner.ItemView;

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

	/** The industry filler item of a job listings page (shown 4 times, around the listings). */
	static ItemView industry(int slot) {
		return item(slot, "FARMING INDUSTRY", "", "Level: 8 360 XP", "Jobs Completed: 18", "Streak: 18",
				"Highest Streak: 18", "Money Earned: 2,293,000", "", "#1 FatherTerra - 4,437 Jobs");
	}

	static final ItemView BEGINNER = item(12, "Beginner Objective", "Harvest 0/506 Acacia Logs", "", "Hand In:",
			"- ✖ 0/253 Acacia Log", "", "Items can be deposited from", "inventory, shulkers & PVs", "", "Reward",
			"- 20 Job XP", "- $58,000", "- 1 Golden Key", "", "㎋ Click to complete job");
	static final ItemView EXPERIENCED = item(14, "Experienced Objective", "Catch 0/61 Tangleroots Fireflies", "",
			"Hand In:", "", "Reward", "- 40 Job XP", "", "㎋ Click to complete job");
	static final ItemView HEAVY = item(16, "Heavy Objective", "Harvest 3,127/4,773 Cherry Logs", "", "Hand In:",
			"- ✔ 2387/2387 Cherry Log", "", "Reward", "- 80 Job XP", "", "㎋ Click to complete job");

	/** /jobs -> an industry: the listings page, title glyph "⻔⻔⻔⻔⻔⻔⻔⻔㏽" (captured 2026-09-30). */
	static final List<ItemView> JOB_LISTINGS = List.of(
			industry(0), industry(8), BEGINNER, industry(9), EXPERIENCED, HEAVY, industry(17),
			item(18, "Go Back", "Main Menu"),
			item(19, "REFRESH JOB LISTINGS", "Change the 3 jobs that are listed", "to new random ones", "",
					"Price: FREE", "", "㎋ Click to refresh"));

	static final List<ItemView> WARPS = List.of(
			item(4, "WORLDS", "◎ Overworld", "", "Land Claiming: Enabled", "Border Size: 40k x 40k",
					"➟ Teleport [Left-Click]"),
			item(5, "MANA WORLDS", "◎ Wolfhaven", "◎ Tangleroot", "➟ Select World"));

	@Test void glyphTitlesAreClassifiedByContent() {
		assertEquals(Optional.of("prestige"), MenuClassifier.classify(GLYPH_TITLE, PRESTIGE_RANKS, TITLE_SOURCES));
		assertEquals(Optional.of("pquests"), MenuClassifier.classify(GLYPH_TITLE, PARTY_QUESTS, TITLE_SOURCES));
		assertEquals(Optional.of("jobs"), MenuClassifier.classify(GLYPH_TITLE, JOBS_MAIN, TITLE_SOURCES));
	}

	@Test void jobListingsAreClassifiedAsJobs() {
		assertEquals(Optional.of("jobs"), MenuClassifier.classify("⻔⻔⻔⻔⻔⻔⻔⻔㏽", JOB_LISTINGS, TITLE_SOURCES));
		// Any one listing is enough, by name or by its "Click to complete job" line.
		assertEquals(Optional.of("jobs"), MenuClassifier.classify(GLYPH_TITLE, List.of(HEAVY), Map.of()));
		assertEquals(Optional.of("jobs"), MenuClassifier.classify(GLYPH_TITLE,
				List.of(item(3, "Odd Name", "Mine 1/2 Stone", "㎋ Click to complete job")), Map.of()));
	}

	@Test void jobListingsAreNamedByIndustryTierAndObjective() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		assertEquals(3, ContainerScanner.scan("jobs", JOB_LISTINGS, store, 0));
		Trackable heavy = find(store, "Farming Heavy · Harvest Cherry Logs");
		assertEquals(3127, heavy.current(), 1e-9); // the objective line, not the hand-in line (2387/2387)
		assertEquals(4773, heavy.max(), 1e-9);
		Trackable beginner = find(store, "Farming Beginner · Harvest Acacia Logs");
		assertEquals(0, beginner.current(), 1e-9);
		assertEquals(506, beginner.max(), 1e-9);
		assertEquals(61, find(store, "Farming Experienced · Catch Tangleroots Fireflies").max(), 1e-9);
		// Industry fillers ("Level: 8 360 XP", "#1 FatherTerra - 4,437 Jobs"), Go Back and Refresh are not entries.
		assertEquals(3, store.all().size(), store.all().toString());
	}

	@Test void jobListingsGetLocalEstimates() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		ContainerScanner.scan("jobs", JOB_LISTINGS, store, 0);
		String heavy = Trackable.idOf("jobs", "Farming Heavy · Harvest Cherry Logs");
		String beginner = Trackable.idOf("jobs", "Farming Beginner · Harvest Acacia Logs");
		String fireflies = Trackable.idOf("jobs", "Farming Experienced · Catch Tangleroots Fireflies");
		Map<String, net.mage.cubewheel.tracker.local.CounterRule> rules = store.activeRules(List.of("tangleroots"));
		assertTrue(rules.containsKey(heavy), rules.toString());
		assertTrue(rules.containsKey(beginner), rules.toString());
		// "Catch Fireflies" is a hit-and-remove entity rule in Tangleroot (fireflies are not fish).
		assertEquals(new net.mage.cubewheel.tracker.local.CounterRule(net.mage.cubewheel.tracker.local.CounterRule.Kind.KILL, 61,
				new net.mage.cubewheel.tracker.local.CounterRule.Named("firefly"),
				new net.mage.cubewheel.tracker.local.CounterRule.NamedWorld("tangleroot")), rules.get(fireflies));
		var overworld = new net.mage.cubewheel.tracker.local.WorldInfo(Set.of("overworld"), false, true);
		var cherry = new net.mage.cubewheel.tracker.local.Signal.BlockBroken(
				"minecraft:cherry_log", "Cherry Log", Set.of("logs"), false, false, overworld);
		net.mage.cubewheel.tracker.local.LocalCounter.onSignal(cherry, store, List.of("tangleroots"), 5);
		assertEquals(1, store.estimate(heavy).orElseThrow().count());
		assertTrue(store.estimate(beginner).isEmpty());
	}

	@Test void shearListingStoresItsObjectiveAndCountsShears() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		ItemView shear = item(14, "Experienced Objective", "Shear 10/84 Sheep", "", "Hand In:", "", "Reward",
				"- 40 Job XP", "", "㎋ Click to complete job");
		ContainerScanner.scan("jobs", List.of(industry(0), shear), store, 0);
		String id = Trackable.idOf("jobs", "Farming Experienced · Shear Sheep");
		assertEquals("Shear 10/84 Sheep", store.objective(id).orElseThrow().subs().get(0).text());
		var rule = store.activeRules(List.of()).get(id);
		assertEquals(new net.mage.cubewheel.tracker.local.CounterRule(net.mage.cubewheel.tracker.local.CounterRule.Kind.SHEAR, 84,
				new net.mage.cubewheel.tracker.local.CounterRule.Named("sheep"),
				new net.mage.cubewheel.tracker.local.CounterRule.AnyWorld()), rule);
		var overworld = new net.mage.cubewheel.tracker.local.WorldInfo(Set.of("overworld"), false, true);
		net.mage.cubewheel.tracker.local.LocalCounter.onSignal(
				new net.mage.cubewheel.tracker.local.Signal.Sheared("minecraft:sheep", "Sheep", overworld), store, List.of(), 5);
		assertEquals(1, store.estimate(id).orElseThrow().count());
		// A listing with a verb nothing knows is still stored as its objective line.
		ContainerScanner.scan("jobs", List.of(industry(0), item(12, "Beginner Objective", "Milk 3/40 Cows", "",
				"㎋ Click to complete job")), store, 10);
		assertEquals("Milk 3/40 Cows", store.objective(Trackable.idOf("jobs", "Farming Beginner · Milk Cows"))
				.orElseThrow().subs().get(0).text());
	}

	@Test void jobListingsWithoutAnIndustryItemOmitTheIndustry() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		ContainerScanner.scan("jobs", List.of(HEAVY), store, 0);
		assertEquals(3127, find(store, "Heavy · Harvest Cherry Logs").current(), 1e-9);
	}

	@Test void industryFillerAloneIsNoProgress() {
		assertTrue(ProgressExtractor.extract(industry(0).lore()).isEmpty());
	}

	@Test void rerolledOrCompletedListingsAreForgottenUnlessPinned() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		store.update("jobs", "Farming Heavy · Harvest Birch Logs", new ProgressExtractor.Progress(10, 900), 0); // rerolled
		store.update("jobs", "Farming Beginner · Mine Stone", new ProgressExtractor.Progress(1, 50), 0);        // pinned
		store.togglePin(Trackable.idOf("jobs", "Farming Beginner · Mine Stone"));
		store.update("jobs", "Mining Heavy · Mine Iron Ore", new ProgressExtractor.Progress(5, 100), 0);        // other industry
		store.update("jobs", "GOLDEN CRATE", new ProgressExtractor.Progress(4, 5), 0);                          // JOBS PROFILE menu
		store.update("pquests", "Farming Heavy · Harvest Birch Logs", new ProgressExtractor.Progress(1, 2), 0); // other source
		ContainerScanner.scan("jobs", JOB_LISTINGS, store, 1_000);
		List<String> names = store.all().stream().map(t -> t.source() + ":" + t.name()).sorted().toList();
		assertEquals(List.of(
				"jobs:Farming Beginner · Harvest Acacia Logs",
				"jobs:Farming Beginner · Mine Stone",
				"jobs:Farming Experienced · Catch Tangleroots Fireflies",
				"jobs:Farming Heavy · Harvest Cherry Logs",
				"jobs:GOLDEN CRATE",
				"jobs:Mining Heavy · Mine Iron Ore",
				"pquests:Farming Heavy · Harvest Birch Logs"), names);
		// A removal alone is a change worth saving.
		store.update("jobs", "Farming Heavy · Harvest Oak Logs", new ProgressExtractor.Progress(1, 2), 1_000);
		assertEquals(1, ContainerScanner.scan("jobs", JOB_LISTINGS, store, 1_500));
		assertTrue(store.all().stream().noneMatch(t -> t.name().endsWith("Oak Logs")));
	}

	/** The Mining listings page, captured 2026-10-01 14:34. */
	static ItemView miningIndustry(int slot) {
		return item(slot, "MINING INDUSTRY", "", "Level: 3 120 XP", "Jobs Completed: 7", "Streak: 7");
	}
	static final ItemView MINING_BEGINNER = item(12, "Beginner Objective", "Mine 51/245 Coal", "", "Reward",
			"- 20 Job XP", "", "㎋ Click to complete job");
	static final ItemView MINING_HEAVY = item(14, "Heavy Objective", "Mine 0/830 resources in Icehaven", "",
			"Hand In:", "", "Reward", "- 80 Job XP", "", "㎋ Click to complete job");
	static final ItemView MINING_EXPERIENCED = item(16, "Experienced Objective", "Mine 160/201 Sandara Iron", "",
			"Reward", "- 40 Job XP", "", "㎋ Click to complete job");

	@Test void listingMissingMidClickIsNotForgotten() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		ContainerScanner.scan("jobs", List.of(miningIndustry(0), MINING_BEGINNER, MINING_HEAVY, MINING_EXPERIENCED), store, 0);
		String iron = Trackable.idOf("jobs", "Mining Experienced · Mine Sandara Iron");
		assertTrue(store.addEstimate(iron, 7, 5));
		// Clicking a listing makes ManaCube re-send the page with that slot empty for a moment (14:34:25 in
		// the capture): the Experienced tier is simply absent, not replaced, so its entry and estimate stay.
		assertEquals(0, ContainerScanner.scan("jobs", List.of(miningIndustry(0), MINING_BEGINNER, MINING_HEAVY), store, 1_000));
		assertEquals(160, find(store, "Mining Experienced · Mine Sandara Iron").current(), 1e-9);
		assertEquals(7, store.estimate(iron).orElseThrow().count());
		// A different job in the same tier is a hand-in replacement or a reroll: the old one is forgotten.
		ItemView crystals = item(16, "Experienced Objective", "Mine 0/85 Icehaven Ice Crystals", "", "Reward",
				"- 40 Job XP", "", "㎋ Click to complete job");
		ContainerScanner.scan("jobs", List.of(miningIndustry(0), MINING_BEGINNER, MINING_HEAVY, crystals), store, 2_000);
		assertTrue(store.all().stream().noneMatch(t -> t.name().endsWith("Sandara Iron")), store.all().toString());
		assertEquals(85, find(store, "Mining Experienced · Mine Icehaven Ice Crystals").max(), 1e-9);
	}

	@Test void jobsMainMenuKeepsItsGoldenCrateEntry() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		store.update("jobs", "GOLDEN CRATE", new ProgressExtractor.Progress(4, 5), 0);
		ContainerScanner.scan("jobs", JOBS_MAIN, store, 1_000); // the main menu is not a listings page
		ContainerScanner.scan("jobs", JOB_LISTINGS, store, 2_000);
		assertEquals(4, find(store, "GOLDEN CRATE").current(), 1e-9);
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

	@Test void scansRecordObjectivesAndSnapEstimatesBack() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		ContainerScanner.scan("prestige", PRESTIGE_RANKS, store, 0);
		String slay = Trackable.idOf("prestige", "Rank [✪6] · Slay 1,000 Monsters");
		assertTrue(store.objective(slay).orElseThrow().special());
		// 4,377/1,000 is already complete: no rule, no estimate.
		assertTrue(store.activeRules(List.of("sandara")).isEmpty());
		List<ItemView> sandara = List.of(item(12, "Sandara Slayer", "Monster Slaying Challenge", "",
				"→ Slay 1,000 Sandara Monsters", "", "Progress: 21%", "", "Rewards:", "● 3,000 Mana"));
		ContainerScanner.scan("challenges", sandara, store, 0);
		String id = Trackable.idOf("challenges", "Sandara Slayer");
		assertEquals(Set.of(id), store.activeRules(List.of("sandara")).keySet());
		assertTrue(store.addEstimate(id, 12, 5));
		assertEquals(1, ContainerScanner.scan("challenges", sandara, store, 10)); // same values, but the estimate snaps back
		assertTrue(store.estimate(id).isEmpty());
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
