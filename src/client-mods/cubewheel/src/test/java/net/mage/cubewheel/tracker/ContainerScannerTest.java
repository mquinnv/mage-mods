package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.tracker.ContainerScanner.ItemView;
import java.nio.file.Path;
import java.util.List;
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
		assertEquals("pquests:Carrot Grind", t.id());
		assertEquals(100, t.current());
		assertEquals(100, t.max());
		assertTrue(t.complete());
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
