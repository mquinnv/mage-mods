package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.mage.cubewheel.config.DefaultConfig;
import net.mage.cubewheel.tracker.ContainerScanner.ItemView;
import net.mage.cubewheel.tracker.FishLog.Habitat;
import net.mage.cubewheel.tracker.local.WorldInfo;
import net.mage.cubewheel.tracker.local.WorldResolver;
import net.mage.cubewheel.tracker.local.WorldScope.Relevance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Fishing Log's "Found in" lines (capture 2026-10-08, /fish, Common and Uncommon pages). */
class FishLogTest {
	static final List<String> WORLDS = DefaultConfig.manaWorlds();
	static final WorldInfo ICEHAVEN = WorldResolver.resolve("minecraft:icehaven", List.of(), WORLDS);
	static final WorldInfo TANGLEROOTS = WorldResolver.resolve("minecraft:tangleroots", List.of(), WORLDS);
	static final WorldInfo OVERWORLD = WorldResolver.resolve("minecraft:overworld", List.of(), WORLDS);

	static ItemView fish(int slot, String name, String... lore) {
		return new ItemView(slot, "minecraft:cooked_cod", name, List.of(lore));
	}

	/** The log page as captured: tier tabs, species with totals, an undiscovered slot, the vanilla fish. */
	static final List<ItemView> PAGE = List.of(
			new ItemView(1, "minecraft:lime_stained_glass_pane", "Common Fish", List.of("Currently Viewing")),
			fish(23, "Burbot", "Total: 66", "", "Found in: Icehaven & Cold Biomes"),
			fish(19, "Albacore", "Total: 72", "", "Found in: Wolfhaven & Plains, Forest, Mountains"),
			fish(22, "Blobfish", "Total: 7", "", "Found in: Morend & the End"),
			fish(33, "Hermit Crab", "Total: 12", "", "Found in: Sandara & Hot Biomes"),
			fish(25, "Catla", "Total: 23", "", "Found in: Tangleroots & Jungles Biomes"),
			new ItemView(22, "minecraft:iron_bars", "Undiscovered", List.of("", "Found in: Burninglands & the Nether")),
			new ItemView(48, "minecraft:cod", "Cod", List.of("Total: 167", "", "Found in: Everywhere")));

	@Test void parsesWorldAndBiomesFromTheFoundInLine() {
		assertEquals(Optional.of(new Habitat("Burbot", "icehaven", "Cold Biomes")),
				FishLog.parse("§bBurbot", List.of("Total: 66", "", "§7Found in: §bIcehaven & Cold Biomes")));
		assertEquals(Optional.of(new Habitat("Catla", "tangleroot", "Jungles Biomes")),
				FishLog.parse("Catla", List.of("Found in: Tangleroots & Jungles Biomes")));
		assertEquals(Optional.of(new Habitat("Cod", "", "Everywhere")), FishLog.parse("Cod", List.of("Found in: Everywhere")));
		assertTrue(FishLog.parse("Cod", List.of("Found in: Everywhere")).orElseThrow().everywhere());
		assertEquals(Optional.empty(), FishLog.parse("Undiscovered", List.of("", "Found in: Burninglands & the Nether")));
		assertEquals(Optional.empty(), FishLog.parse("Common Fish", List.of("Currently Viewing")));
	}

	@Test void scanRecordsEverySpeciesOnceAndFindsThemByAnySpelling() {
		FishLog log = new FishLog(null);
		assertEquals(6, log.scan(PAGE));
		assertEquals(0, log.scan(PAGE)); // nothing new
		assertEquals(6, log.size());
		assertEquals(Optional.of(new Habitat("Hermit Crab", "sandara", "Hot Biomes")), log.habitat("HermitCrab"));
		assertEquals(Optional.of(new Habitat("Hermit Crab", "sandara", "Hot Biomes")), log.habitat("hermit crabs"));
		assertEquals(Optional.of(new Habitat("Burbot", "icehaven", "Cold Biomes")), log.habitat("burbot"));
		assertEquals(Optional.empty(), log.habitat("Piranha"));
		assertEquals(Optional.empty(), log.habitat(""));
	}

	@Test void aSpeciesIsCurrentInItsOwnManaWorldOtherInAnotherAndNeutralOutside() {
		Habitat burbot = new Habitat("Burbot", "icehaven", "Cold Biomes");
		assertEquals(Relevance.CURRENT, FishLog.relevance(burbot, ICEHAVEN));
		assertEquals(Relevance.OTHER, FishLog.relevance(burbot, TANGLEROOTS));
		assertEquals(Relevance.NEUTRAL, FishLog.relevance(burbot, OVERWORLD)); // cold biomes exist there
		assertEquals(Relevance.NEUTRAL, FishLog.relevance(burbot, WorldInfo.UNKNOWN));
		assertEquals(Relevance.NEUTRAL, FishLog.relevance(burbot, null));
		Habitat cod = new Habitat("Cod", "", "Everywhere");
		assertEquals(Relevance.NEUTRAL, FishLog.relevance(cod, ICEHAVEN));
		assertEquals(Relevance.NEUTRAL, FishLog.relevance(null, ICEHAVEN));
	}

	@Test void persistsAsJsonAndLoadsBack(@TempDir Path dir) {
		Path f = dir.resolve("cubewheel-fishlog.json");
		FishLog log = new FishLog(f);
		log.scan(PAGE);
		assertTrue(log.save());
		FishLog back = new FishLog(f);
		back.load();
		assertEquals(6, back.size());
		assertEquals(log.habitat("Albacore"), back.habitat("Albacore"));
		assertEquals(Optional.of(new Habitat("Cod", "", "Everywhere")), back.habitat("Cod"));
	}
}
