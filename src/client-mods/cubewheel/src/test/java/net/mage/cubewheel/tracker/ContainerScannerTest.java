package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.tracker.ContainerScanner.ItemView;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContainerScannerTest {
	@TempDir Path dir;

	@Test void scansItemsWithProgressOnly() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		List<ItemView> items = List.of(
			new ItemView(0, "minecraft:carrot", "Carrot Grind", List.of("Harvest carrots", "1,200/1,500")),
			new ItemView(1, "minecraft:paper", "Deco", List.of("Nothing here")));
		assertEquals(1, ContainerScanner.scan("jobs", items, store, 42));
		List<Trackable> all = store.all();
		assertEquals(1, all.size());
		Trackable t = all.get(0);
		assertEquals("jobs:Carrot Grind", t.id());
		assertEquals(1200, t.current());
		assertEquals(1500, t.max());
		assertEquals(42, t.seenAt());
	}

	@Test void stripsFormattingCodesAndSkipsBlankNames() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		List<ItemView> items = List.of(
			new ItemView(0, "minecraft:wheat", "§6§lWheat §rFarmer", List.of("§7Progress: §a5§7/§a10")),
			new ItemView(1, "minecraft:glass_pane", "§7 ", List.of("1/2")),
			new ItemView(2, "minecraft:stone", null, List.of("1/2")));
		assertEquals(1, ContainerScanner.scan("jobs", items, store, 0));
		Trackable t = store.all().get(0);
		assertEquals("jobs:Wheat Farmer", t.id());
		assertEquals(5, t.current());
		assertEquals(10, t.max());
	}

	@Test void rescanOfUnchangedItemsReportsNoChange() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		List<ItemView> items = List.of(new ItemView(0, "minecraft:carrot", "Carrot Grind", List.of("1,200/1,500")));
		assertEquals(1, ContainerScanner.scan("jobs", items, store, 0));
		assertEquals(0, ContainerScanner.scan("jobs", items, store, 750));
		List<ItemView> progressed = List.of(new ItemView(0, "minecraft:carrot", "Carrot Grind", List.of("1,300/1,500")));
		assertEquals(1, ContainerScanner.scan("jobs", progressed, store, 1_500));
	}

	// --- Skyblock, from Michael's first Skyblock capture (2026-11-06, see SkyblockCapture) ---

	/** Classifies a captured Skyblock menu as the mod does and scans it when it is a tracker menu; the source. */
	private static Optional<String> readSkyblock(String capture, TrackerStore store, long now) {
		List<ItemView> items = SkyblockCapture.load(capture);
		Optional<String> source = MenuClassifier.classify(net.mage.cubewheel.sidebar.Gamemode.SKYBLOCK, "x", items,
				java.util.Map.of());
		source.ifPresent(s -> ContainerScanner.scan(s, items, store, now));
		return source;
	}

	private static Trackable entry(TrackerStore store, String id) {
		return store.all().stream().filter(t -> t.id().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError(id + " not in " + store.all()));
	}

	private static java.util.Set<String> ids(TrackerStore store) {
		java.util.Set<String> out = new java.util.TreeSet<>();
		for (Trackable t : store.all()) out.add(t.id());
		return out;
	}

	/**
	 * Island Team Quests read as party quests do: quests with a Progress line, "QUEST COMPLETED" wins, and the
	 * bottom row's "Weekly Farm Challenge" is a challenge. Tier headers and locked iron-bar quests are no entries.
	 */
	@Test void skyblockIslandTeamQuests() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		assertEquals(Optional.of("isquests"), readSkyblock(SkyblockCapture.ISLAND_QUESTS, store, 0));
		assertEquals(java.util.Set.of("isquests:Discoverer", "isquests:Learning Spawners", "isquests:Miner",
				"isquests:Fisherman", "isquests:Honest Work", "challenges:Wart Grind"), ids(store));
		assertTrue(entry(store, "isquests:Miner").complete());
		assertTrue(entry(store, "isquests:Discoverer").complete());
		Trackable fisherman = entry(store, "isquests:Fisherman");
		assertEquals(12, fisherman.current());
		assertEquals(100, fisherman.max());
		Trackable wart = entry(store, "challenges:Wart Grind");
		assertEquals(0, wart.current());
		assertEquals(100, wart.max());
	}

	/** The weekly challenge rotates: last week's is forgotten on the read that shows the new one, as on /pquests. */
	@Test void skyblockIslandQuestsWeeklyChallengeRotates() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		store.update("challenges", "Carrot Grind", new ProgressExtractor.Progress(40, 100), 0);
		store.update("isquests", "Wart Grind", new ProgressExtractor.Progress(0, 100), 0); // a stale copy as a quest
		readSkyblock(SkyblockCapture.ISLAND_QUESTS, store, 10);
		java.util.Set<String> ids = ids(store);
		assertTrue(ids.contains("challenges:Wart Grind"), ids.toString());
		assertTrue(!ids.contains("challenges:Carrot Grind"), ids.toString());
		assertTrue(!ids.contains("isquests:Wart Grind"), ids.toString());
	}

	/** An island quest without a Progress line (a hub-style button) is skipped, as on /pquests. */
	@Test void skyblockIslandQuestsNeedAProgressLine() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		List<ItemView> items = List.of(new ItemView(0, "minecraft:book", "Beginner Quests",
				List.of("", "Complete all beginner quests", "to receive these rewards", "", "Rewards:", "● 250x250 Island Expansion")),
				new ItemView(1, "minecraft:paper", "Upgrade", List.of("Island size 3/5")));
		assertEquals(0, ContainerScanner.scan("isquests", items, store, 0));
		assertEquals(0, store.all().size());
	}

	/**
	 * The Task Board: one entry per task, named after its objective (each task fills a 2x2 block, so 4 copies), its
	 * "(0/76)" counter and its "Time Left" as the deadline. Stats, Streak and Points Shop are no entries.
	 */
	@Test void skyblockTaskBoard() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		long now = 1_000_000;
		assertEquals(Optional.of("tasks"), readSkyblock(SkyblockCapture.TASK_BOARD, store, now));
		String slay = "tasks:Slay 76 Mana Slimes in Wolfhaven";
		String anchovy = "tasks:Catch an Anchovy while fishing (Found in Wolfhaven)";
		assertEquals(java.util.Set.of(slay, anchovy), ids(store));
		Trackable t = entry(store, slay);
		assertEquals(0, t.current());
		assertEquals(76, t.max());
		assertEquals(1, entry(store, anchovy).max());
		long left = (23 * 3600 + 59 * 60 + 36) * 1000L;
		assertEquals(now + left, store.due(slay).getAsLong());
		assertEquals(now + left, store.due(anchovy).getAsLong());
		assertTrue(store.objective(slay).isPresent());
		assertEquals("Slay 76 Mana Slimes in Wolfhaven", store.objective(slay).get().subs().get(0).text());
	}

	/** Tasks expire and rotate ("Your task has expired."): a stored task the board no longer shows is forgotten. */
	@Test void skyblockTaskBoardForgetsTasksItNoLongerShows() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		store.update("tasks", "Slay 40 Mana Wolves in Wolfhaven", new ProgressExtractor.Progress(12, 40), 0);
		store.update("tasks", "Mine 200 Ores in Tangleroot", new ProgressExtractor.Progress(5, 200), 0);
		store.togglePin("tasks:Mine 200 Ores in Tangleroot");
		readSkyblock(SkyblockCapture.TASK_BOARD, store, 10);
		java.util.Set<String> ids = ids(store);
		assertTrue(!ids.contains("tasks:Slay 40 Mana Wolves in Wolfhaven"), ids.toString());
		assertTrue(ids.contains("tasks:Mine 200 Ores in Tangleroot"), ids.toString()); // pinned
		assertTrue(ids.contains("tasks:Slay 76 Mana Slimes in Wolfhaven"), ids.toString());
		// A second read of the same board changes nothing.
		assertEquals(0, ContainerScanner.scan("tasks", SkyblockCapture.load(SkyblockCapture.TASK_BOARD), store, 10));
	}

	/** A board read that shows no tasks (a page still loading, a custom source keyed "tasks") forgets nothing. */
	@Test void taskBoardWithNoTasksForgetsNothing() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		store.update("tasks", "Slay 40 Mana Wolves in Wolfhaven", new ProgressExtractor.Progress(12, 40), 0);
		assertEquals(0, ContainerScanner.scan("tasks", java.util.List.of(
				new ContainerScanner.ItemView(0, "minecraft:gray_stained_glass_pane", "", java.util.List.of())), store, 10));
		assertTrue(ids(store).contains("tasks:Slay 40 Mana Wolves in Wolfhaven"), ids(store).toString());
	}

	/** Skyblock's rank page is Survival's: one entry per rank with a counter, named after its objective. */
	@Test void skyblockPrestigeRanks() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		assertEquals(Optional.of("prestige"), readSkyblock(SkyblockCapture.PRESTIGE_RANKS, store, 0));
		assertEquals(java.util.Set.of(
				"prestige:Rank [✪1] · Harvest 25,000 crops",
				"prestige:Rank [✪2] · Kill 10,000 mobs",
				"prestige:Rank [✪3] · Harvest 5,000 resources",
				"prestige:Rank [✪4] · Reach 2,500 Skill Level",
				"prestige:Rank [✪5] · Complete 25 tasks",
				"prestige:Rank [✪6] · Earn 250,000 Slayer XP",
				"prestige:Rank [✪7] · Get 20 Boss Kills",
				"prestige:Rank [✪8] · Play for 10 hours",
				"prestige:Rank [✪9] · Reach Island Level 78"), ids(store)); // Rank [✪10]'s "[$25.14k balance]" is no counter
		Trackable skills = entry(store, "prestige:Rank [✪4] · Reach 2,500 Skill Level");
		assertEquals(50, skills.current());
		assertEquals(2500, skills.max());
		assertEquals(164, entry(store, "prestige:Rank [✪2] · Kill 10,000 mobs").current());
	}

	@Test void skyblockPrestigeListGivesNoEntries() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		assertEquals(Optional.of("prestige"), readSkyblock(SkyblockCapture.PRESTIGE_LIST, store, 0));
		assertEquals(0, store.all().size());
	}

	/** Season Challenges: one entry per category with its Current Level's counter; Next Level panes are no entries. */
	@Test void skyblockSeasonChallenges() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		assertEquals(Optional.of("sbchallenges"), readSkyblock(SkyblockCapture.SEASON_CHALLENGES, store, 0));
		assertEquals(java.util.Set.of("sbchallenges:Mob Challenge", "sbchallenges:Resource Challenge",
				"sbchallenges:Monster Challenge", "sbchallenges:Crop Challenge", "sbchallenges:Dungeon Challenge",
				"sbchallenges:Fishing Challenge", "sbchallenges:Task Challenge", "sbchallenges:Mini Boss Challenge",
				"sbchallenges:Mining Challenge", "sbchallenges:Boss Challenge"), ids(store));
		Trackable mob = entry(store, "sbchallenges:Mob Challenge");
		assertEquals(164, mob.current());
		assertEquals(3_500_000, mob.max());
		Trackable mining = entry(store, "sbchallenges:Mining Challenge");
		assertEquals(1015, mining.current());
		assertEquals(5_000_000, mining.max());
	}

	/** The next level keeps the entry: same id, the new level's counter. */
	@Test void skyblockSeasonChallengeKeepsItsIdAcrossLevels() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		ItemView head = new ItemView(0, "minecraft:zombie_head", "Mob Challenge", List.of("", "Total Completions: 0.0"));
		ContainerScanner.scan("sbchallenges", List.of(head,
				new ItemView(2, "minecraft:yellow_stained_glass_pane", "Current Level (1)", List.of("Kill 164 / 3,500,000 Mobs")),
				new ItemView(3, "minecraft:lime_stained_glass_pane", "Next Level (2)", List.of("Kill 164 / 4,000,000 Mobs"))),
				store, 0);
		ContainerScanner.scan("sbchallenges", List.of(head,
				new ItemView(2, "minecraft:yellow_stained_glass_pane", "Current Level (2)", List.of("Kill 3,500,010 / 4,000,000 Mobs"))),
				store, 10);
		assertEquals(1, store.all().size());
		Trackable mob = entry(store, "sbchallenges:Mob Challenge");
		assertEquals(3_500_010, mob.current());
		assertEquals(4_000_000, mob.max());
	}

	/** The /jobs hub and Boss Challenges (no counters) are not tracker menus and give no entries. */
	@Test void skyblockHubAndBossChallengesGiveNoEntries() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		assertEquals(Optional.empty(), readSkyblock(SkyblockCapture.HUB, store, 0));
		assertEquals(Optional.empty(), readSkyblock(SkyblockCapture.HUB_WITHOUT_QUESTS, store, 0));
		assertEquals(Optional.empty(), readSkyblock(SkyblockCapture.BOSS_CHALLENGES, store, 0));
		assertEquals(0, store.all().size());
	}

	@Test void everyReadEntryIsReportedEvenWhenUnchanged() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		List<ItemView> items = List.of(
			new ItemView(0, "minecraft:carrot", "Carrot Grind", List.of("1,200/1,500")),
			new ItemView(1, "minecraft:wheat", "Wheat Grind", List.of("5/10")),
			new ItemView(2, "minecraft:paper", "Deco", List.of("Nothing here")));
		ContainerScanner.scan("jobs", items, store, 0);
		java.util.Set<String> seen = new java.util.LinkedHashSet<>();
		assertEquals(0, ContainerScanner.scan("jobs", items, store, 750, seen::add)); // nothing changed...
		assertEquals(java.util.Set.of("jobs:Carrot Grind", "jobs:Wheat Grind"), seen); // ...but both were confirmed
	}

	@Test void nullOrEmptyInputUpdatesNothing() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		assertEquals(0, ContainerScanner.scan("jobs", null, store, 0));
		assertEquals(0, ContainerScanner.scan("jobs", List.of(new ItemView(0, "x", "A", null)), store, 0));
		assertEquals(0, store.all().size());
	}

	// --- Quest menus only track items with a "Progress:" line (auction house leak, 2026-10-07) ---

	/** The auction house once classified as pquests: its category button has a percentage but no progress. */
	@Test void questMenuSkipsItemsWithoutProgressLine() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		List<ItemView> items = List.of(new ItemView(0, "minecraft:gold_block", "AUCTION HOUSE",
				List.of("Item sales are taxed 3%", "", "To sell an item, hold it and type:", "/ah sell [price] [amount]")));
		assertEquals(0, ContainerScanner.scan("pquests", items, store, 0));
		assertEquals(0, store.all().size());
	}

	/** An auction listing with "+10% Woodcutting MCMMO XP" in its lore is not a quest at 10%. */
	@Test void questMenuSkipsListingsWithPercentagesInEffects() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		List<ItemView> items = List.of(new ItemView(1, "minecraft:diamond_axe", "Oakstriker",
				List.of("Unbreakable", "Oakstriker", "", "ITEM EFFECTS: (When Held)", "➟ +10% Woodcutting MCMMO XP", "",
						"Seller: BawsarLv", "Price: 0.1 cubits")));
		assertEquals(0, ContainerScanner.scan("pquests", items, store, 0));
		assertEquals(0, store.all().size());
	}

	/** The real weekly challenge item (captured 2026-10-07) still tracks, complete. */
	@Test void questMenuTracksCompletedQuestWithProgressLine() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		List<ItemView> items = List.of(new ItemView(0, "minecraft:carrot", "Carrot Grind",
				List.of("Weekly Farm Challenge", "", "→ Harvest 10,000 Carrots", "", "Progress: 100%", "", "Rewards:",
						"● 3,000 Mana", "● $50,000", "", "QUEST COMPLETED")));
		assertEquals(1, ContainerScanner.scan("pquests", items, store, 0));
		Trackable t = store.all().get(0);
		assertEquals("challenges:Carrot Grind", t.id()); // the weekly challenge slot, not a party quest (2026-10-10)
		assertEquals(100, t.current());
		assertEquals(100, t.max());
		assertTrue(t.complete());
	}

	/**
	 * The auction house searched for "challenge" (2026-10-13): its title matched the "challenges" title source, and
	 * the filler pane ("Item sales are taxed 3%") and a listing with "+5%" in its lore became challenges at 3% and 5%.
	 * A challenge item always carries a Progress line, as a quest does.
	 */
	@Test void challengeMenuSkipsItemsWithoutProgressLine() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		List<ItemView> items = List.of(
				new ItemView(1, "minecraft:gray_stained_glass_pane", "AUCTION HOUSE",
						List.of("Item sales are taxed 3%", "", "To sell an item, hold it and type:", "/ah sell [price] [amount]")),
				new ItemView(10, "minecraft:leather_leggings", "CHALLENGE LEGGINGS",
						List.of("ITEM EFFECTS: (When Worn)", "➟ +5% Mob Damage", "", "Seller: Someone", "Price: $1,000,000")));
		assertEquals(0, ContainerScanner.scan("challenges", items, store, 0));
		assertEquals(0, store.all().size());
	}

	/** A challenge item read under the "challenges" title source itself still tracks, as it does from the quests menu. */
	@Test void challengeMenuTracksItemWithProgressLine() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		List<ItemView> items = List.of(new ItemView(0, "minecraft:fishing_rod", "Fisherman",
				List.of("Weekly Fishing Challenge", "", "→ Catch 1,000 Fish", "", "Progress: 52%")));
		assertEquals(1, ContainerScanner.scan("challenges", items, store, 0));
		assertEquals("challenges:Fisherman", store.all().get(0).id());
		assertEquals(52, store.all().get(0).current());
	}

	@Test void questMenuTracksQuestWithProgressLine() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		List<ItemView> items = List.of(new ItemView(0, "minecraft:paper", "Skill Level Hunter", List.of("Some quest", "Progress: 37%")));
		assertEquals(1, ContainerScanner.scan("pquests", items, store, 0));
		Trackable t = store.all().get(0);
		assertEquals(37, t.current());
		assertEquals(100, t.max());
	}

	/** Prestige ranks carry their counter inside the objective, never a Progress line: they are not gated. */
	/**
	 * 2026-10-10: the quests menu's bottom row holds the weekly challenge ("Monster Slaying Challenge" on Oct 3,
	 * "Weekly Farm Challenge" on Oct 7). It is no party quest and the old week is gone once the slot shows a new one.
	 */
	/**
	 * The Halloween 2026 event calendar (/calendar, capture 2026-10-11): one paper per day. Today's says "Task: Catch
	 * 100 Fish (8/100)", "Time Left: 23h", "Reward: Large Money Pouch"; later days "Task: ???" and "Task will be
	 * revealed October 12th"; the 31st "Claim all Previous Days"; a summary "2026 Halloween Calendar", "Days Claimed: 0".
	 */
	@Test void theEventCalendarTracksTodaysTaskWithItsDeadline() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		ItemView today = new ItemView(10, "minecraft:paper", "October 11th",
				List.of("", "Task: Catch 100 Fish (8/100)", "", "Time Left: 23h", "", "Reward: Large Money Pouch"));
		ItemView tomorrow = new ItemView(11, "minecraft:paper", "October 12th",
				List.of("", "Task: ???", "Reward: Hiding from Ghost (Status)", "", "Task will be revealed October 12th"));
		ItemView last = new ItemView(34, "minecraft:paper", "October 31st",
				List.of("", "Task: Claim all Previous Days", "Reward: Halloween Crate Key & [Halloween26] (Title)", ""));
		ItemView summary = new ItemView(49, "minecraft:paper", "2026 Halloween Calendar", List.of("Days Claimed: 0"));
		List<ItemView> items = List.of(today, tomorrow, last, summary);
		assertEquals(Optional.of("calendar"), MenuClassifier.classify("\ue007\uf7be", items, java.util.Map.of()));
		long now = 1_000_000_000L;
		assertTrue(ContainerScanner.scan("calendar", items, store, now) > 0);
		String id = "calendar:October 11th · Catch 100 Fish";
		assertEquals(List.of(id), store.all().stream().map(Trackable::id).toList());
		Trackable t = store.row(id).orElseThrow().item();
		assertEquals(8, t.current());
		assertEquals(100, t.max());
		assertEquals("Catch 100 Fish", store.objective(id).orElseThrow().subs().get(0).text());
		assertEquals(List.of("Large Money Pouch"), store.rewards(id));
		assertEquals(now + 23 * 3_600_000L, store.due(id).orElseThrow());
		// The task counts locally: every catch is a fish.
		assertTrue(store.activeRules(TrackerStoreWorldFilterTest.WORLDS).containsKey(id));
		// A re-read seconds later moves the deadline by seconds: nothing to save.
		assertEquals(0, ContainerScanner.scan("calendar", items, store, now + 5_000));
		// The deadline survives a save and a load.
		assertTrue(store.save());
		TrackerStore again = new TrackerStore(dir.resolve("t.json"));
		again.load();
		assertEquals(now + 23 * 3_600_000L, again.due(id).orElseThrow());
		// Tomorrow: the 12th is revealed; the 11th no longer runs (no time left): it is over and forgotten.
		ItemView yesterday = new ItemView(10, "minecraft:paper", "October 11th",
				List.of("", "Task: Catch 100 Fish (100/100)", "", "Reward: Large Money Pouch"));
		ItemView revealed = new ItemView(11, "minecraft:paper", "October 12th",
				List.of("", "Task: Slay 50 Zombies (0/50)", "", "Time Left: 22h 30m", "", "Reward: Hiding from Ghost (Status)"));
		long tomorrowNow = now + 86_400_000L;
		assertTrue(ContainerScanner.scan("calendar", List.of(yesterday, revealed, last, summary), store, tomorrowNow) > 0);
		String zombies = "calendar:October 12th · Slay 50 Zombies";
		assertEquals(List.of(zombies), store.all().stream().map(Trackable::id).toList());
		assertTrue(store.due(id).isEmpty());
		assertEquals(tomorrowNow + 22 * 3_600_000L + 30 * 60_000L, store.due(zombies).orElseThrow());
		assertEquals(23 * 3_600_000L, ContainerScanner.timeLeftMs("23h"));
		assertEquals(2 * 3_600_000L + 15 * 60_000L, ContainerScanner.timeLeftMs("2h 15m"));
		assertEquals(26 * 3_600_000L, ContainerScanner.timeLeftMs("1d 2h"));
		assertEquals(45 * 60_000L, ContainerScanner.timeLeftMs("45 mins"));
		assertEquals(0, ContainerScanner.timeLeftMs("soon"));
	}

	@Test void theWeeklyChallengeInTheQuestsMenuIsAChallengeAndTheOldWeekIsForgotten() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		ItemView quest = new ItemView(13, "minecraft:paper", "Jungle Pursuit",
				List.of("Experienced Quest", "→ Mine 15,000 Tangleroots Resources", "Progress: 24%", "Rewards:", "● 5,000 Mana"));
		ItemView slayer = new ItemView(31, "minecraft:paper", "Sandara Slayer",
				List.of("Monster Slaying Challenge", "→ Slay 1,000 Sandara Monsters", "Progress: 36%", "Rewards:", "● 3,000 Mana", "● $50,000"));
		ItemView carrots = new ItemView(31, "minecraft:carrot", "Carrot Grind",
				List.of("Weekly Farm Challenge", "→ Harvest 10,000 Carrots", "Progress: 100%", "Rewards:", "● 3,000 Mana", "● $50,000"));
		// Read as a party quest by an older build: the same name is the stale copy and goes.
		store.update("pquests", "Sandara Slayer", new ProgressExtractor.Progress(30, 100), 0);
		assertTrue(ContainerScanner.scan("pquests", List.of(quest, slayer), store, 1) > 0);
		assertEquals(List.of("challenges:Sandara Slayer", "pquests:Jungle Pursuit"),
				store.all().stream().map(Trackable::id).sorted().toList());
		assertEquals(36, store.row("challenges:Sandara Slayer").orElseThrow().item().current());
		assertEquals("Slay 1,000 Sandara Monsters", store.objective("challenges:Sandara Slayer").orElseThrow().subs().get(0).text());
		// A week later the slot shows the farm challenge: the slayer is over, whatever its count.
		store.addEstimate("challenges:Sandara Slayer", 645, 2);
		assertTrue(ContainerScanner.scan("pquests", List.of(quest, carrots), store, 3) > 0);
		assertEquals(List.of("challenges:Carrot Grind", "pquests:Jungle Pursuit"),
				store.all().stream().map(Trackable::id).sorted().toList());
		assertTrue(store.estimate("challenges:Sandara Slayer").isEmpty());
		// A quests page without the slot (page 2) says nothing about the week: the challenge stays.
		assertEquals(0, ContainerScanner.scan("pquests", List.of(quest), store, 4));
		assertTrue(store.row("challenges:Carrot Grind").isPresent());
		// A pinned challenge is the player's to keep.
		store.togglePin("challenges:Carrot Grind");
		ContainerScanner.scan("pquests", List.of(quest, slayer), store, 5);
		assertTrue(store.row("challenges:Carrot Grind").isPresent());
		assertTrue(ContainerScanner.isWeeklyChallenge(List.of("", "Weekly Farm Challenge", "→ x")));
		assertTrue(!ContainerScanner.isWeeklyChallenge(List.of("Experienced Quest", "Challenge yourself")));
	}

	@Test void prestigeRankWithoutProgressLineStillTracks() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		List<ItemView> items = List.of(new ItemView(14, "minecraft:paper", "Rank [✪4]",
				List.of("", "OBJECTIVE", "Reach 2,500 Skill Level [Lvl 1851/2,500]", "", "➟ Click to rankup")));
		assertEquals(1, ContainerScanner.scan("prestige", items, store, 0));
		Trackable t = store.all().get(0);
		assertEquals("prestige:Rank [✪4] · Reach 2,500 Skill Level", t.id());
		assertEquals(1851, t.current());
		assertEquals(2500, t.max());
	}

	/**
	 * Rank [✪4]'s objective line arrives as "Reach 5,000 Skill Level [Lvl 3814/5,000" (tracker store 2026-11-03), the
	 * counter's "]" cut off. The counter still leaves the name, or every level-up mints a new entry: Michael saw the
	 * 5,000 quest twice, at "Lvl 3814" and "Lvl 3819".
	 */
	@Test void prestigeRankWithACutOffCounterIsNamedWithoutIt() {
		TrackerStore store = new TrackerStore(dir.resolve("t.json"));
		List<ItemView> items = List.of(new ItemView(14, "minecraft:paper", "Rank [✪4]",
				List.of("", "OBJECTIVE", "Reach 5,000 Skill Level [Lvl 3814/5,000", "", "➟ Click to rankup")));
		assertEquals(1, ContainerScanner.scan("prestige", items, store, 0));
		Trackable t = store.all().get(0);
		assertEquals("prestige:Rank [✪4] · Reach 5,000 Skill Level", t.id());
		assertEquals(3814, t.current());
		assertEquals(5000, t.max());
		assertEquals("Reach 5,000 Skill Level", store.objective(t.id()).orElseThrow().subs().get(0).text());
	}
}
