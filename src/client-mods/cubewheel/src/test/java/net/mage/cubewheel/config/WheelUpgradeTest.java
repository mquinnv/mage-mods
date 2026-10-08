package net.mage.cubewheel.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WheelUpgradeTest {
	@TempDir Path dir;

	private static List<String> labels(List<WheelNode> nodes) {
		return nodes.stream().map(n -> n.label).toList();
	}

	private static WheelNode child(List<WheelNode> nodes, String label) {
		return nodes.stream().filter(n -> label.equals(n.label)).findFirst().orElseThrow(() -> new AssertionError(label));
	}

	@Test void newDefaultTopLevelOrder() {
		List<WheelNode> w = DefaultConfig.wheel();
		assertEquals(List.of("Sushi", "Homes", "Teleporter", "Jobs", "Shop", "Sell", "Vaults", "Heal",
				"Isles", "Party quests", "Daily reward", "Boss event", "TPA", "More"), labels(w));
		for (WheelNode n : w) assertNull(n.outer, n.label + " still sticks out"); // every second entry is an arc now
		assertEquals(List.of("Back"), labels(w.get(2).arc));
		assertEquals("/heal", w.get(7).command);
		assertEquals("minecraft:golden_apple", w.get(7).icon);
		assertEquals(List.of("/fly", "/feed"), w.get(7).arc.stream().map(n -> n.command).toList());
		assertEquals(List.of("Prestige", "Challenges"), labels(w.get(9).arc));
		assertEquals("vaults", w.get(6).dynamic);
		assertTrue(w.get(6).asArc);
		assertEquals(List.of("Party vault", "All vaults"), labels(w.get(6).children));
		assertEquals(List.of("PV 1", "PV 2", "PV 3", "PV 4", "Party vault", "All vaults"),
				labels(net.mage.cubewheel.wheel.WheelResolver.children(w.get(6), 4, List.of())));
		assertEquals("/warp spawners", w.get(0).arc.get(0).command);
		assertEquals(List.of("/sell hand", "/sell all"), w.get(5).arc.stream().map(n -> n.command).toList());
		assertEquals("/warp crops", w.get(0).command);
		assertEquals("homes", w.get(1).dynamic);
		assertTrue(w.get(1).asArc);
		assertEquals("/sell", w.get(5).command);
		assertEquals("/shop", w.get(4).command);
		assertEquals(List.of("/kilton", "/ah"), w.get(4).arc.stream().map(n -> n.command).toList());
		assertNull(w.get(8).command); // Isles is a ring since version 10 (its children fan out as the arc)
		assertEquals(List.of("Wolfhaven", "Tangleroot", "Sandara", "Icehaven", "Morend", "Burninglands", "Isles menu"), labels(w.get(8).children));
		assertEquals("/cow", w.get(10).command);
		assertEquals("/crates", w.get(10).arc.get(0).command);
		assertTrue(w.get(11).isSlice());
		assertEquals("boss", w.get(11).dynamic);
		assertEquals("tpa", w.get(12).dynamic);
		WheelNode more = w.get(13);
		assertEquals(List.of("Shops", "Warps", "Party", "Ender chest", "Settings"), labels(more.children));
		assertEquals(List.of("Alchemist", "Enchanter", "Shop", "Auction house", "Forge", "Fish shop"),
				labels(child(more.children, "Shops").children));
		assertEquals(List.of("Server warps", "Bosses"), labels(child(more.children, "Warps").children));
		assertEquals(List.of("Party menu", "Party home", "Party warps", "Claim", "Map", "Party vault"),
				labels(child(more.children, "Party").children));
	}

	@Test void chainsBecomeArcsInOrderKeepingAnyArcAlreadyThere() {
		List<WheelNode> w = new java.util.ArrayList<>();
		w.add(WheelNode.leaf("Sell", null, "/sell")
				.withOuter(WheelNode.leaf("Sell hand", null, "/sell hand").withOuter(WheelNode.leaf("Sell all", null, "/sell all"))));
		w.add(WheelNode.leaf("Isles", null, "/isles").withArc(WheelNode.leaf("Morend", null, "/warp morend"))
				.withOuter(WheelNode.leaf("Mine", null, "/is")));
		w.add(WheelNode.leaf("Fly", null, "/fly"));
		assertTrue(WheelUpgrade.chainsToArcs(w));
		assertEquals(List.of("Sell hand", "Sell all"), labels(w.get(0).arc));
		assertNull(w.get(0).outer);
		assertNull(w.get(0).arc.get(0).outer);
		assertEquals(List.of("Morend", "Mine"), labels(w.get(1).arc));
		assertNull(w.get(2).arc);
		assertFalse(WheelUpgrade.chainsToArcs(w));
	}

	@Test void everyRingBelowTheTopHoldsAtMostEight() {
		for (WheelNode n : WheelUpgrade.walk(DefaultConfig.wheel())) {
			if (n.children != null) assertTrue(n.children.size() <= 8, n.label + ": " + n.children.size());
		}
	}

	@Test void oldDefaultBecomesNewDefaultWithNothingMoved() {
		WheelUpgrade.Result r = WheelUpgrade.upgrade(DefaultConfig.wheelV2());
		assertTrue(r.moved().isEmpty(), r.moved().toString());
		assertEquals(new Gson().toJson(DefaultConfig.wheel()), new Gson().toJson(r.wheel()));
	}

	@Test void userLeavesAreKeptUnderMoreCustom() {
		List<WheelNode> old = DefaultConfig.wheelV2();
		old.add(WheelNode.leaf("Island", "minecraft:grass_block", "/is"));
		old.get(0).children.add(WheelNode.leaf("My farm", null, "/home farm"));
		old.get(0).children.add(WheelNode.leaf("Spawn again", null, "/SPAWN ")); // known command: dropped
		old.add(WheelNode.ring("Mine", null, WheelNode.leaf("Dup", null, "is"))); // same as /is: once
		WheelUpgrade.Result r = WheelUpgrade.upgrade(old);
		assertEquals(List.of("Island (/is)", "My farm (/home farm)"), r.moved());
		WheelNode custom = child(child(r.wheel(), "More").children, "Custom");
		assertEquals(List.of("Island", "My farm"), labels(custom.children));
		assertEquals("/is", custom.children.get(0).command);
	}

	@Test void manyUserLeavesAreSplitIntoRingsOfEight() {
		List<WheelNode> old = new java.util.ArrayList<>();
		for (int i = 0; i < 11; i++) old.add(WheelNode.leaf("C" + i, null, "/c" + i));
		WheelNode custom = child(child(WheelUpgrade.upgrade(old).wheel(), "More").children, "Custom");
		assertEquals(List.of("Custom 1", "Custom 2"), labels(custom.children));
		assertEquals(8, custom.children.get(0).children.size());
		assertEquals(3, custom.children.get(1).children.size());
	}

	@Test void loadingAVersion2FileUpgradesTheWheelOnceAndReportsIt() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		List<WheelNode> old = DefaultConfig.wheelV2();
		old.add(WheelNode.leaf("Island", null, "/is"));
		Files.writeString(f, new Gson().toJson(Map.of("configVersion", 2, "vaultCount", 5, "wheel", old)));
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertEquals(DefaultConfig.CONFIG_VERSION, s.current().configVersion);
		assertEquals(5, s.current().vaultCount); // other settings kept
		assertEquals("Sushi", s.current().wheel.get(0).label);
		assertTrue(s.warnings().stream().anyMatch(w -> w.contains("Island (/is)")), s.warnings().toString());
		assertTrue(Files.readString(f).contains("\"configVersion\": " + DefaultConfig.CONFIG_VERSION));
		// Once: an edit made afterwards (removing Kilton) survives the next load.
		s.current().wheel.remove(4); // Kilton
		s.save();
		assertNull(s.reload());
		assertEquals("Sell", s.current().wheel.get(4).label);
		assertTrue(s.warnings().isEmpty());
	}

	@Test void bossSliceSurvivesNormalisationButNotWithACommand() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, """
		  {"configVersion": 3, "wheel": [
		    {"label":"Boss","dynamic":"boss","children":[{"label":"x","command":"/x"}]},
		    {"label":"Bad","dynamic":"boss","command":"/warp boss"}
		  ]}""");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertEquals(List.of("Boss", "Settings"), labels(s.current().wheel));
		assertTrue(s.current().wheel.get(0).isSlice());
		assertNull(s.current().wheel.get(0).children);
	}

	@Test void bossWarpsAndDailyRewardDefaultsAndNormalisation() throws Exception {
		CubeWheelConfig d = DefaultConfig.create();
		assertEquals("/warp boss", d.events.bossWarps.get("(?i)boss arena"));
		assertEquals(12, d.events.bossWarps.size());
		assertEquals(5, d.events.bossMinutes);
		assertTrue(d.dailyReward.enabled);
		assertEquals(24, d.dailyReward.dailyHours);
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, """
		  {"configVersion": 3, "events": {"bossMinutes": 0, "bossWarps": {"(?i)mine": "warp mines", "([": "/x", "(?i)y": ""}},
		   "dailyReward": {"dailyHours": 0, "weeklyDays": 99, "menuTitlePattern": "(["}}""");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		// Only the boss rules new in versions 7, 9 and 12 are added; the world rules this file left out stay out.
		assertEquals(Map.of("(?i)cursed witch", "/warp cursedwitch", "(?i)golden knight", "/warp tanglerootoutside",
				"(?i)great ?wolf", "/warp greatwolf", "(?i)mine", "/warp mines"), s.current().events.bossWarps);
		assertEquals(1, s.current().events.bossMinutes);
		assertEquals(1, s.current().dailyReward.dailyHours);
		assertEquals(60, s.current().dailyReward.weeklyDays);
		assertEquals(DefaultConfig.COW_MENU_TITLE, s.current().dailyReward.menuTitlePattern);
		assertEquals(3, s.warnings().size(), s.warnings().toString());
	}

	/** 2026-10-08: "subwarps on the isles for known things under them. a 3rd ring?" */
	@Test void islesIsARingOfIslesWithThePlacesUnderEachAndFansOutWhenHovered() {
		WheelNode isles = DefaultConfig.wheel().get(8);
		assertEquals("Isles", isles.label);
		assertTrue(isles.isRing());
		assertTrue(isles.asArc);
		assertEquals(List.of("Wolfhaven", "Tangleroot", "Sandara", "Icehaven", "Morend", "Burninglands", "Isles menu"),
				labels(isles.children));
		assertEquals(List.of("Mana Golem", "Great Wolf"), labels(isles.children.get(0).arc));
		assertEquals("/warp managolem", isles.children.get(0).arc.get(0).command);
		assertEquals(List.of("Outside", "Volcano Golem"), labels(isles.children.get(1).arc));
		assertEquals("/warp tangleroot", isles.children.get(1).command); // singular on the server
		assertEquals("/warp tanglerootoutside", isles.children.get(1).arc.get(0).command);
		assertEquals(List.of("Mana Pond"), labels(isles.children.get(2).arc)); // "we need at least the pond on sandara"
		assertEquals("/warp pond", isles.children.get(2).arc.get(0).command);
		assertEquals(List.of("Cursed Witch"), labels(isles.children.get(4).arc));
		assertEquals("/isles", isles.children.get(6).command);
	}

	@Test void aVersion10IslesRingGainsTheMissingPlacesKeepingItsOwn() {
		List<WheelNode> w = new java.util.ArrayList<>();
		WheelNode ring = WheelNode.ring("Isles", "minecraft:filled_map",
				WheelNode.leaf("Wolfhaven", null, "/warp wolfhaven").withArc(WheelNode.leaf("Mana Golem", null, "/warp managolem")),
				WheelNode.leaf("Sandara", null, "/Warp Sandara").withArc(WheelNode.leaf("Mine", null, "/warp mysandara")),
				WheelNode.leaf("Icehaven", null, "/warp icehaven"));
		w.add(ring);
		assertTrue(WheelUpgrade.isleArcs(w));
		assertEquals(List.of("Mana Golem", "Great Wolf"), labels(ring.children.get(0).arc)); // the one it had is not doubled
		assertEquals(List.of("Mine", "Mana Pond"), labels(ring.children.get(1).arc)); // yours first, the default appended
		assertNull(ring.children.get(2).arc); // nothing under Icehaven by default
		assertTrue(!WheelUpgrade.isleArcs(w)); // once
	}

	@Test void theOldIslesSliceBecomesTheRingKeepingAnIsleWarpYouAdded() {
		List<WheelNode> w = new java.util.ArrayList<>();
		w.add(WheelNode.leaf("Isles", "minecraft:filled_map", "/isles").withArc(
				WheelNode.leaf("Wolfhaven", "minecraft:bone", "/warp wolfhaven"),
				WheelNode.leaf("Tangleroots", "minecraft:vine", "/warp tangleroots"),
				WheelNode.leaf("Secret", "minecraft:barrier", "/warp secret")));
		w.add(WheelNode.leaf("Heal", null, "/heal"));
		assertTrue(WheelUpgrade.islesRing(w));
		assertEquals(List.of("Isles", "Heal"), labels(w));
		assertTrue(w.get(0).isRing());
		assertEquals(List.of("Wolfhaven", "Tangleroot", "Sandara", "Icehaven", "Morend", "Burninglands", "Isles menu", "Secret"),
				labels(w.get(0).children));
		assertTrue(!WheelUpgrade.islesRing(w)); // once
	}

	@Test void tanglerootBecomesSingularWhereverItIs() {
		List<WheelNode> w = new java.util.ArrayList<>();
		w.add(WheelNode.ring("Mine", null, WheelNode.leaf("Tangleroots", null, "/warp tangleroots"),
				WheelNode.leaf("TR jungle", null, "/Warp Tangleroots ")));
		assertTrue(WheelUpgrade.tanglerootSingular(w));
		assertEquals("Tangleroot", w.get(0).children.get(0).label);
		assertEquals("/warp tangleroot", w.get(0).children.get(0).command);
		assertEquals("TR jungle", w.get(0).children.get(1).label); // your own label is yours
		assertEquals("/warp tangleroot", w.get(0).children.get(1).command);
		assertTrue(!WheelUpgrade.tanglerootSingular(w));
	}

	@Test void loadingAVersion9FileMakesIslesARingAndTheBossWarpSingular() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, """
		  {"configVersion": 9,
		   "wheel": [{"label":"Isles","icon":"minecraft:filled_map","command":"/isles",
		              "arc":[{"label":"Tangleroots","command":"/warp tangleroots"}]}],
		   "events": {"bossWarps": {"(?i)tangleroot": "/warp tangleroots", "(?i)morend": "/warp morend"}}}""");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertEquals(DefaultConfig.CONFIG_VERSION, s.current().configVersion);
		WheelNode isles = s.current().wheel.get(0);
		assertTrue(isles.isRing());
		assertEquals("Tangleroot", isles.children.get(1).label);
		assertEquals(Map.of("(?i)great ?wolf", "/warp greatwolf", "(?i)tangleroot", "/warp tangleroot", "(?i)morend", "/warp morend"),
				s.current().events.bossWarps); // version 12 adds the Great Wolf's rule too
		assertTrue(s.warnings().stream().anyMatch(w -> w.contains("Isles opens a ring")), s.warnings().toString());
	}
}
