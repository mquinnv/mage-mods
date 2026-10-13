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
}
