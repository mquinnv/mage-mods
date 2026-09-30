package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
