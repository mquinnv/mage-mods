package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.mage.cubewheel.sidebar.Gamemode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackerStoresTest {
	@TempDir Path dir;

	@Test void survivalAndOtherShareOneStoreAndSkyblockHasItsOwn() {
		TrackerStores stores = new TrackerStores(dir);
		assertSame(stores.of(Gamemode.SURVIVAL), stores.of(Gamemode.OTHER));
		assertNotSame(stores.of(Gamemode.SKYBLOCK), stores.of(Gamemode.SURVIVAL));
	}

	@Test void skyblockProgressNeverReachesTheSurvivalFile() {
		TrackerStores stores = new TrackerStores(dir);
		stores.of(Gamemode.SKYBLOCK).update("jobs", "Miner", new ProgressExtractor.Progress(3, 10), 0);
		stores.of(Gamemode.SKYBLOCK).save();
		TrackerStores fresh = new TrackerStores(dir);
		assertEquals(List.of(), fresh.of(Gamemode.SURVIVAL).all());
		assertEquals(1, fresh.of(Gamemode.SKYBLOCK).all().size());
		assertTrue(dir.resolve("cubewheel-tracker-skyblock.json").toFile().exists());
	}

	@Test void forEachLoadedVisitsOnlyStoresOfHasReturned() {
		TrackerStores stores = new TrackerStores(dir);
		List<TrackerStore> seen = new ArrayList<>();
		stores.forEachLoaded(seen::add);
		assertEquals(List.of(), seen);
		TrackerStore sv = stores.of(Gamemode.OTHER);
		stores.forEachLoaded(seen::add);
		assertEquals(List.of(sv), seen);
		TrackerStore sb = stores.of(Gamemode.SKYBLOCK);
		seen.clear();
		stores.forEachLoaded(seen::add);
		assertEquals(2, seen.size());
		assertTrue(seen.contains(sv) && seen.contains(sb));
	}
}
