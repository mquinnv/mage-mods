package com.mage.cubewheel.config;

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

	@Test void missingFileWritesDefaults() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		ConfigStore s = new ConfigStore(f);
		assertNull(s.reload());
		assertTrue(Files.exists(f));
		assertEquals(3, s.current().vaultCount);
		assertEquals(8, s.current().wheel.size());
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
		  {"vaultCount": 500, "listThreshold": 1, "wheel": [
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
		while (!q.isEmpty()) { WheelNode n = q.pop(); if (n.command != null) all.add(n.command); if (n.children != null) q.addAll(n.children); }
		for (String cmd : List.of("/sell", "/kilton", "/alchemist", "/enchanter", "/warp crops", "/warp spawners", "/warp wolfhaven", "/warp tangleroots", "/warp morend", "/warp boss", "/rtp", "/jobs", "/pquests", "/prestige"))
			assertTrue(all.contains(cmd), cmd);
		assertTrue(c.wheel.stream().anyMatch(n -> "homes".equals(n.dynamic)));
		assertTrue(c.wheel.stream().anyMatch(n -> "vaults".equals(n.dynamic)));
	}
}
