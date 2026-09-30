package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.mage.cubewheel.config.DefaultConfig;
import net.mage.cubewheel.tracker.ContainerScanner;
import net.mage.cubewheel.tracker.ProgressExtractor.Progress;
import net.mage.cubewheel.tracker.TrackerStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** "Harvest 594/799 Warped Wart Blocks" broken in the Nether, end to end through the pure path. */
class NetherBlocksTest {
	@TempDir Path dir;
	static final List<String> WORLDS = DefaultConfig.manaWorlds();
	static final WorldInfo NETHER = WorldResolver.resolve("minecraft:the_nether",
			List.of("ManaCube", "World: world_nether"), DefaultConfig.manaWorlds());
	static final String ID = "jobs:Farming Experienced · Harvest Warped Wart Blocks";

	static ObjectiveInfo obj(String line) {
		return new ObjectiveInfo(List.of(new ObjectiveInfo.Sub(line, null)), false, false);
	}

	@Test void warpedWartBlockObjectiveParsesToABreakRule() {
		Optional<CounterRule> r = ObjectiveParser.parse(obj("Harvest 594/799 Warped Wart Blocks"), WORLDS);
		assertEquals(Optional.of(new CounterRule(CounterRule.Kind.BREAK, 799,
				new CounterRule.Named("warped wart block"), new CounterRule.AnyWorld())), r);
	}

	@Test void aWarpedWartBlockBrokenInTheNetherCounts() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Farming Experienced · Harvest Warped Wart Blocks", new Progress(594, 799), 0);
		s.setObjective(ID, obj("Harvest 594/799 Warped Wart Blocks"));
		Signal.BlockBroken b = new Signal.BlockBroken("minecraft:warped_wart_block", "Warped Wart Block", Set.of(),
				false, false, false, NETHER);
		assertEquals(List.of(new LocalCounter.Contribution(ID, 1)), LocalCounter.onSignal(b, s, WORLDS, 5));
	}

	/** The listing as captured 2026-09-30 17:07 (job just taken, 0/799), then a break in resource_world_nether. */
	@Test void theCapturedListingYieldsAnActiveRuleThatCounts() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		List<ContainerScanner.ItemView> items = List.of(
				new ContainerScanner.ItemView(0, "minecraft:paper", "FARMING INDUSTRY", List.of("", "Level: 8 420 XP")),
				new ContainerScanner.ItemView(14, "minecraft:filled_map", "Experienced Objective",
						List.of("Harvest 0/799 Warped Wart Blocks", "", "Hand In:", "- \u2716 0/400 Warped Wart Block", "",
								"Items can be deposited from", "inventory, shulkers & PVs", "", "Reward", "- 20 Job XP",
								"- $190,000", "- 1 Golden Key", "", "\u338b Click to complete job")));
		ContainerScanner.scan("jobs", items, s, 0);
		WorldInfo resourceNether = WorldResolver.resolve("minecraft:resource_world_nether", List.of(), WORLDS);
		Signal.BlockBroken b = new Signal.BlockBroken("minecraft:warped_wart_block", "Warped Wart Block", Set.of(),
				false, false, false, resourceNether);
		assertEquals(List.of(new LocalCounter.Contribution(ID, 1)), LocalCounter.onSignal(b, s, WORLDS, 5));
	}

	@Test void netherVariantsCount() {
		assertCounts("Harvest 10 Crimson Stems", "minecraft:crimson_stem", "Crimson Stem", Set.of("logs"), false, false);
		assertCounts("Harvest 10 Warped Stems", "minecraft:warped_stem", "Warped Stem", Set.of("logs"), false, false);
		assertCounts("Harvest 10 Nether Wart Blocks", "minecraft:nether_wart_block", "Nether Wart Block", Set.of(),
				false, false);
		assertCounts("Harvest 10 Nether Wart", "minecraft:nether_wart", "Nether Wart", Set.of("crop"), true, true);
		assertCounts("Harvest 10 Shroomlights", "minecraft:shroomlight", "Shroomlight", Set.of(), false, false);
	}

	private static void assertCounts(String line, String id, String name, Set<String> groups, boolean crop,
			boolean mature) {
		CounterRule r = ObjectiveParser.parse(obj(line), WORLDS).orElseThrow(() -> new AssertionError(line));
		assertEquals(true, RuleMatcher.matches(r, new Signal.BlockBroken(id, name, groups, crop, mature, false, NETHER)),
				line);
	}
}
