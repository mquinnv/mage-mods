package net.mage.cubewheel.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigStoreTest {
	@TempDir Path dir;

	@Test void outerEntriesAreKeptValidatedAndAtMostFourDeep() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, """
		  {"configVersion": 6, "wheel": [
		    {"label":"Sell","command":"sell","outer":{"label":"Hand","command":"/sell hand",
		      "outer":{"label":"All","command":"/sell all","outer":{"label":"Three","command":"/3",
		      "outer":{"label":"Four","command":"/4","outer":{"label":"Too far","command":"/x"}}}}}},
		    {"label":"Bad outer","command":"/a","outer":{"label":"","command":"/b"}}
		  ]}""");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		WheelNode sell = s.current().wheel.get(0);
		assertEquals("/sell", sell.command);
		assertEquals("/sell hand", sell.outer.command);
		assertEquals("/sell all", sell.outer.outer.command);
		assertEquals("/4", sell.outer.outer.outer.outer.command);
		assertNull(sell.outer.outer.outer.outer.outer); // five tiers at most
		assertNull(s.current().wheel.get(1).outer);
	}

	@Test void aVersion5ChainLoadsAsAnArcAndIsWrittenBack() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, """
		  {"configVersion": 5, "wheel": [
		    {"label":"Shop","command":"/shop","outer":{"label":"Kilton","command":"/kilton",
		      "outer":{"label":"Auction house","command":"/ah"}}}
		  ]}""");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		WheelNode shop = s.current().wheel.get(0);
		assertNull(shop.outer);
		assertEquals(List.of("/kilton", "/ah"), shop.arc.stream().map(n -> n.command).toList());
		assertTrue(Files.readString(f).replaceAll("\\s", "").contains("\"configVersion\":6"));
	}

	@Test void arcEntriesKeepOnlyPlainCommands() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, """
		  {"configVersion": 5, "wheel": [
		    {"label":"Isles","command":"/isles","arc":[
		      {"label":"Wolfhaven","command":"warp wolfhaven"},
		      {"label":"Ring","children":[{"label":"x","command":"/x"}]},
		      {"label":"","command":"/blank"}]},
		    {"label":"Empty arc","command":"/a","arc":[{"label":"","command":"/b"}]}
		  ]}""");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		WheelNode isles = s.current().wheel.get(0);
		assertEquals(1, isles.arc.size());
		assertEquals("/warp wolfhaven", isles.arc.get(0).command);
		assertNull(s.current().wheel.get(1).arc);
	}

	@Test void missingFileWritesDefaults() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertTrue(Files.exists(f));
		assertEquals(3, s.current().vaultCount);
		assertEquals(14, s.current().wheel.size()); // top level incl. Homes, Shops, Sell, Isles and TPA
	}

	@Test void localCountingDefaultsAndNormalisation() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, "{\"tracker\": {\"local\": {\"kills\": false, \"worlds\": [\" Sandara \", null, \"\"]}}}");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		CubeWheelConfig.Local l = s.current().tracker.local;
		assertTrue(l.enabled);
		assertFalse(l.kills);
		assertEquals(List.of("Sandara"), l.worlds);
		assertTrue(l.specialWorlds.contains("wolfhaven"));
		Files.writeString(f, "{\"tracker\": {}}");
		assertNull(s.reload());
		assertTrue(s.current().tracker.local.fish);
		assertEquals(6, s.current().tracker.local.worlds.size());
	}

	@Test void roundTripKeepsEdits() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		ConfigStore s = new ConfigStore(f); s.reload();
		s.current().vaultCount = 5; s.save();
		ConfigStore t = new ConfigStore(f);
		assertNull(t.reload());
		assertEquals(5, t.current().vaultCount);
	}

	@Test void badJsonKeepsPreviousAndReportsError() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		ConfigStore s = new ConfigStore(f); s.reload();
		s.current().vaultCount = 4; s.save(); s.reload();
		Files.writeString(f, "{ \"vaultCount\": 2, }}}");
		String err = s.reload();
		assertNotNull(err);
		assertEquals(4, s.current().vaultCount);
	}

	@Test void clampsNumbersAndDropsInvalidNodes() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, """
		  {"configVersion": 3, "vaultCount": 500, "listThreshold": 1, "wheel": [
		    {"label":"ok","command":"/spawn"},
		    {"label":"both","command":"/x","children":[]},
		    {"label":"none"},
		    {"label":"ring","children":[{"label":"bad"},{"label":"sell","command":"sell"}]}
		  ]}""");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		CubeWheelConfig c = s.current();
		assertEquals(54, c.vaultCount);
		assertEquals(3, c.listThreshold);
		assertEquals(List.of("ok", "ring"), c.wheel.stream().map(n -> n.label).toList());
		assertEquals(1, c.wheel.get(1).children.size());
		assertEquals("/sell", c.wheel.get(1).children.get(0).command); // leading slash added
		assertNotNull(c.serverHosts); assertFalse(c.serverHosts.isEmpty());
		assertNotNull(c.tracker.sources);
	}

	@Test void defaultsContainRequestedEntries() {
		CubeWheelConfig c = DefaultConfig.create();
		List<String> all = new ArrayList<>();
		Deque<WheelNode> q = new ArrayDeque<>(c.wheel);
		while (!q.isEmpty()) { WheelNode n = q.pop(); if (n.command != null) all.add(n.command); if (n.children != null) q.addAll(n.children); if (n.outer != null) q.add(n.outer); if (n.arc != null) q.addAll(n.arc); }
		for (String cmd : List.of("/sell", "/kilton", "/alchemist", "/enchanter", "/warp crops", "/warp spawners", "/warp wolfhaven", "/warp tangleroots", "/warp morend", "/warp boss", "/teleporter", "/back", "/jobs", "/pquests", "/prestige"))
			assertTrue(all.contains(cmd), cmd);
		assertTrue(WheelUpgrade.walk(c.wheel).stream().anyMatch(n -> "homes".equals(n.dynamic)));
		assertTrue(WheelUpgrade.walk(c.wheel).stream().anyMatch(n -> "vaults".equals(n.dynamic))); // PV 1…N
		assertTrue(all.containsAll(List.of("/p vault", "/pv", "/ec")));
	}

	@Test void refreshAndSidebarDefaults() throws Exception {
		CubeWheelConfig c = DefaultConfig.create();
		assertEquals(List.of("/pquests", "/prestige", "/jobs"), c.tracker.refreshCommands);
		assertEquals("(?i)reach [\\d,]+ skill level", c.tracker.sidebarLinks.get("Skills"));
		// A file written before these keys existed gets the defaults.
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, "{ \"tracker\": { \"hudMaxLines\": 4 } }");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertEquals(List.of("/pquests", "/prestige", "/jobs"), s.current().tracker.refreshCommands);
		assertEquals(java.util.Map.of("Skills", "(?i)reach [\\d,]+ skill level"), s.current().tracker.sidebarLinks);
	}

	@Test void oldHudMaxLinesDefaultsAreUpgradedOnce() throws Exception {
		assertEquals(10, DefaultConfig.create().tracker.hudMaxLines);
		Path f = dir.resolve("cubewheel.json");
		for (int old : new int[] {6, 8}) {
			Files.writeString(f, "{ \"tracker\": { \"hudMaxLines\": " + old + " } }");
			ConfigStore s = new ConfigStore(f);
			assertNull(s.reload());
			assertEquals(10, s.current().tracker.hudMaxLines, "from " + old);
			assertTrue(Files.readString(f).contains("\"hudMaxLines\": 10"), "upgrade written back");
			// Once: a 6 the user writes afterwards is kept.
			Files.writeString(f, Files.readString(f).replace("\"hudMaxLines\": 10", "\"hudMaxLines\": 6"));
			assertNull(s.reload());
			assertEquals(6, s.current().tracker.hudMaxLines);
		}
		// Any other value is the user's choice.
		Files.writeString(f, "{ \"tracker\": { \"hudMaxLines\": 7 } }");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertEquals(7, s.current().tracker.hudMaxLines);
	}

	@Test void survivalSidebarPatternDefaultsAndStaysEditable() throws Exception {
		assertEquals("(?i)survival", DefaultConfig.create().tracker.survivalSidebarPattern);
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, "{ \"tracker\": { \"hudMaxLines\": 4 } }");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertEquals("(?i)survival", s.current().tracker.survivalSidebarPattern); // older file: default
		Files.writeString(f, "{ \"tracker\": { \"survivalSidebarPattern\": \"\" } }");
		assertNull(s.reload());
		assertEquals("", s.current().tracker.survivalSidebarPattern); // explicit "" switches the gate off
	}

	@Test void oldBroadSkillsLinkIsUpgraded() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, "{\"tracker\": {\"sidebarLinks\": {\"Skills\": \"(?i)skill level\", \"Mana\": \"(?i)mana\"}}}");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertEquals("(?i)reach [\\d,]+ skill level", s.current().tracker.sidebarLinks.get("Skills"));
		assertEquals("(?i)mana", s.current().tracker.sidebarLinks.get("Mana"));
	}

	@Test void refreshCommandsAreNormalised() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, """
		  {"tracker": {"refreshCommands": [" jobs ", "", null, "/prestige"], "sidebarLinks": {}}}
		""");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertEquals(List.of("/jobs", "/prestige"), s.current().tracker.refreshCommands);
		assertTrue(s.current().tracker.sidebarLinks.isEmpty()); // an explicit empty map switches linking off
	}

	@Test void refreshCommandsAreCapped() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		StringBuilder sb = new StringBuilder("{\"tracker\": {\"refreshCommands\": [");
		for (int i = 0; i < 20; i++) sb.append(i == 0 ? "" : ",").append("\"/c").append(i).append('"');
		Files.writeString(f, sb.append("]}}").toString());
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertEquals(ConfigStore.MAX_REFRESH_COMMANDS, s.current().tracker.refreshCommands.size());
	}

	@Test void lastLoadOkTracksMostRecentReload() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertTrue(s.lastLoadOk());
		Files.writeString(f, "{ \"vaultCount\": 2, }}}");
		assertNotNull(s.reload());
		assertFalse(s.lastLoadOk());
		Files.writeString(f, "{ \"vaultCount\": 2 }");
		assertNull(s.reload());
		assertTrue(s.lastLoadOk());
	}

	@Test void boostersDefaultOnAndPositionNormalised() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, "{}");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertTrue(s.current().boosters.enabled);
		assertEquals("top_left", s.current().boosters.position.corner);
		Files.writeString(f, "{\"boosters\": {\"enabled\": false, \"position\": {\"corner\": \"Bottom-Right\", \"x\": -5, \"y\": 30}}}");
		assertNull(s.reload());
		assertFalse(s.current().boosters.enabled);
		assertEquals("bottom_right", s.current().boosters.position.corner);
		assertEquals(0, s.current().boosters.position.x);
		assertEquals(30, s.current().boosters.position.y);
		Files.writeString(f, "{\"boosters\": {\"position\": null}}");
		assertNull(s.reload());
		assertEquals(4, s.current().boosters.position.y);
	}

	@Test void eventsDefaultToTheWikiSchedule() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload()); // missing file: defaults written
		CubeWheelConfig.Events e = s.current().events;
		assertTrue(e.enabled);
		assertTrue(e.hudVisible);
		assertEquals(3, e.show);
		assertEquals(5, e.alertMinutes);
		assertEquals("America/New_York", e.timezone);
		assertEquals(List.of("LPS", "KOTH", "Boss", "Golden Knight", "Cursed Witch", "Desert Golem"),
				e.schedule.stream().map(d -> d.name).toList());
		for (CubeWheelConfig.EventDef d : e.schedule) net.mage.cubewheel.events.EventSchedule.parse(d.when);
		Files.writeString(f, "{\"events\": {}}");
		assertNull(s.reload());
		assertEquals(6, s.current().events.schedule.size());
		assertTrue(s.warnings().isEmpty());
	}

	@Test void badEventEntriesAreDroppedWithWarnings() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, "{\"events\": {\"show\": 99, \"alertMinutes\": -3, \"timezone\": \"Mars/Base\", \"schedule\": ["
				+ "{\"name\": \" KOTH \", \"when\": \"every 2h from 00:30\"},"
				+ "{\"name\": \"Broken\", \"when\": \"sometimes\"},"
				+ "{\"name\": \"Elsewhere\", \"when\": \"at 10:00\", \"timezone\": \"Nowhere/City\"},"
				+ "{\"when\": \"at 10:00\"},"
				+ "{\"name\": \"UTC thing\", \"when\": \"at 10:00\", \"timezone\": \"UTC\"}]}}");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		CubeWheelConfig.Events e = s.current().events;
		assertEquals(10, e.show);
		assertEquals(0, e.alertMinutes);
		assertEquals("America/New_York", e.timezone);
		assertEquals(List.of("KOTH", "UTC thing"), e.schedule.stream().map(d -> d.name).toList());
		assertEquals(4, s.warnings().size(), s.warnings().toString());
		assertTrue(s.warnings().stream().anyMatch(w -> w.contains("Broken")));
	}

	@Test void cooldownsDefaultOn() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, "{\"cooldowns\": {\"showUses\": false, \"position\": {\"corner\": \"bottom_left\", \"y\": 60}}}");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertTrue(s.current().cooldowns.enabled);
		assertTrue(s.current().cooldowns.mcmmo);
		assertFalse(s.current().cooldowns.showUses);
		assertEquals("bottom_left", s.current().cooldowns.position.corner);
		assertEquals(4, s.current().cooldowns.position.x);
		assertEquals(60, s.current().cooldowns.position.y);
		Files.writeString(f, "{\"cooldowns\": null}");
		assertNull(s.reload());
		assertTrue(s.current().cooldowns.showUses);
		assertEquals("top_left", s.current().cooldowns.position.corner);
	}

	@Test void worldFilterDefaultsToSortAndIsNormalised() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertEquals("sort", s.current().tracker.worldFilter);
		Files.writeString(f, "{\"tracker\": {\"worldFilter\": \" HIDE \"}}");
		assertNull(s.reload());
		assertEquals("hide", s.current().tracker.worldFilter);
		Files.writeString(f, "{\"tracker\": {\"worldFilter\": \"nonsense\"}}");
		assertNull(s.reload());
		assertEquals("sort", s.current().tracker.worldFilter);
	}

	@Test void jobsPanelDefaultsAndStacksBelowYourTopLeftPanelsOnUpgrade() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertTrue(s.current().tracker.jobsPanel.enabled);
		assertEquals("top_left", s.current().tracker.jobsPanel.position.corner);
		assertEquals(4, s.current().tracker.jobsPanel.position.y);
		// A version 3 file whose left panels sit 80 px down: the new panel takes that offset (it stacks below them).
		Files.writeString(f, "{\"configVersion\": 3, \"events\": {\"position\": {\"corner\": \"top_left\", \"x\": 6, \"y\": 80}},"
				+ " \"boosters\": {\"position\": {\"corner\": \"top_right\", \"x\": 4, \"y\": 200}}}");
		assertNull(s.reload());
		assertEquals(DefaultConfig.CONFIG_VERSION, s.current().configVersion);
		CubeWheelConfig.Position p = s.current().tracker.jobsPanel.position;
		assertEquals("top_left", p.corner);
		assertEquals(6, p.x);
		assertEquals(80, p.y);
		// Once: a later edit survives, and the toggle is kept.
		Files.writeString(f, "{\"configVersion\": " + DefaultConfig.CONFIG_VERSION + ", \"events\": {\"position\": {\"y\": 80}},"
				+ " \"tracker\": {\"jobsPanel\": {\"enabled\": false, \"position\": {\"corner\": \"bottom_left\", \"y\": 10}}}}");
		assertNull(s.reload());
		assertFalse(s.current().tracker.jobsPanel.enabled);
		assertEquals("bottom_left", s.current().tracker.jobsPanel.position.corner);
		assertEquals(10, s.current().tracker.jobsPanel.position.y);
	}

	@Test void trackerPanelDefaultsTopLeftAndTakesTheJobsPanelOffsetOnUpgrade() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertEquals("top_left", s.current().tracker.position.corner);
		assertEquals(4, s.current().tracker.position.y);
		Files.writeString(f, "{\"configVersion\": 4, \"tracker\": {\"jobsPanel\": {\"position\": "
				+ "{\"corner\": \"top_left\", \"x\": 6, \"y\": 80}}}}");
		assertNull(s.reload());
		assertEquals(DefaultConfig.CONFIG_VERSION, s.current().configVersion);
		CubeWheelConfig.Position p = s.current().tracker.position;
		assertEquals("top_left", p.corner);
		assertEquals(6, p.x);
		assertEquals(80, p.y);
		// Once: a later edit survives.
		Files.writeString(f, "{\"configVersion\": " + DefaultConfig.CONFIG_VERSION
				+ ", \"tracker\": {\"position\": {\"corner\": \"bottom_right\", \"y\": 10}}}");
		assertNull(s.reload());
		assertEquals("bottom_right", s.current().tracker.position.corner);
		assertEquals(10, s.current().tracker.position.y);
	}
}
