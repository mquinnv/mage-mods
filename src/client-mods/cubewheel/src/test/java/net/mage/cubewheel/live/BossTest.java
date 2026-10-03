package net.mage.cubewheel.live;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import net.mage.cubewheel.config.DefaultConfig;
import net.mage.cubewheel.config.WheelNode;
import net.mage.cubewheel.wheel.SliceViews;
import org.junit.jupiter.api.Test;

class BossTest {
	private static final String GOLEM = "\n---------------------\nA BOSS SPAWNED\n\nBoss Mana Golem\nLocation: Wolfhaven Mines\n"
			+ "● Deal 50 damage for rewards…\n---------------------";
	private static final long MIN = 60_000;

	@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir;

	/** 2026-10-03: the Mana Golem spawned at 15:35:04, the client restarted at 15:36, its kills went on after. */
	@Test void aBossSurvivesAClientRestart() {
		String spawn = "\n---------------------\nA BOSS SPAWNED\n\nBoss Mana Golem\nLocation: Wolfhaven Mines\n"
				+ "● Deal 75 damage for rewards\n● Top 10 damage have a increased \nchance at legendary loot\n---------------------";
		long t0 = 1_000_000L, min = 60_000L;
		java.nio.file.Path file = dir.resolve("cubewheel-boss.json");
		BossSlice before = new BossSlice();
		before.spawned(BossParser.parse(spawn).orElseThrow(), t0);
		assertTrue(before.save(file));
		BossSlice after = new BossSlice();
		after.load(file);
		assertTrue(after.onChat("⺝ HumaneElm5707 was slain by Mana Golem [maces]", t0 + 6 * min));
		SliceViews.View v = after.view(DefaultConfig.bossWarps(), 5 * min, t0 + 7 * min);
		assertEquals("/warp managolem", v.command());
		BossSlice none = new BossSlice();
		none.load(dir.resolve("missing.json"));
		assertEquals(BossSlice.NONE, none.view(DefaultConfig.bossWarps(), 5 * min, t0).label());
	}

	@Test void parsesRealAnnouncements() {
		BossParser.Spawn g = BossParser.parse(GOLEM).orElseThrow();
		assertEquals(new BossParser.Spawn("Mana Golem", "Wolfhaven Mines", false), g);
		assertEquals(new BossParser.Spawn("Cursed Witch", "Morend", true),
				BossParser.parse("A MINI BOSS SPAWNED\n\nBoss Cursed Witch\nLocation: Morend\n● Deal 20 damage").orElseThrow());
		assertEquals(new BossParser.Spawn("Sahuagin", "Boss Arena", false),
				BossParser.parse("§c§lA BOSS SPAWNED\n§fBoss §eSahuagin\n§7Location: §fBoss Arena").orElseThrow());
		assertEquals("Sandara Canyon",
				BossParser.parse("A MINI BOSS SPAWNED\r\nBoss Desert Golem\r\nLocation: Sandara Canyon").orElseThrow().location());
		assertNull(BossParser.parse("A BOSS SPAWNED\nBoss Thing").orElseThrow().location());
	}

	@Test void ignoresOtherMessages() {
		assertTrue(BossParser.parse("Boss Mana Golem\nLocation: Wolfhaven Mines").isEmpty()); // no header
		assertTrue(BossParser.parse("Player: A BOSS SPAWNED lol").isEmpty());
		assertTrue(BossParser.parse("A BOSS SPAWNED\n\n").isEmpty());
		assertTrue(BossParser.parse(null).isEmpty());
	}

	/** 2026-10-03 16:15: "A MINI BOSS SPAWNED / Boss Cursed Witch / Location: Morend" has its own warp. */
	@Test void aBossWithItsOwnWarpGoesThereNotToItsWorld() {
		Map<String, String> w = DefaultConfig.bossWarps();
		assertEquals("/warp cursedwitch", BossSlice.warpFor(new BossParser.Spawn("Cursed Witch", "Morend", true), w));
		assertEquals("/warp morend", BossSlice.warpFor(new BossParser.Spawn("Void Beholder", "Morend", false), w));
		assertEquals("/warp managolem", BossSlice.warpFor(new BossParser.Spawn("Mana Golem", "Wolfhaven Mines", false), w));
	}

	@Test void defaultWarpsForEachLocation() {
		Map<String, String> w = DefaultConfig.bossWarps();
		assertEquals("/warp boss", BossSlice.warpFor("Boss Arena", w));
		assertEquals("/warp managolem", BossSlice.warpFor("Wolfhaven Mines", w));      // the boss's own warp
		assertEquals("/warp volcanogolem", BossSlice.warpFor("Tangleroot Volcano", w));
		assertEquals("/warp wolfhaven", BossSlice.warpFor("Wolfhaven Village", w));
		assertEquals("/warp tangleroots", BossSlice.warpFor("Tangleroots Forest", w));
		assertEquals("/warp sandara", BossSlice.warpFor("Sandara Canyon", w));
		assertEquals("/warp icehaven", BossSlice.warpFor("Icehaven Peaks", w));
		assertEquals("/warp morend", BossSlice.warpFor("Morend", w));
		assertEquals("/warp burninglands", BossSlice.warpFor("Burning Lands", w));
		assertNull(BossSlice.warpFor("Somewhere", w));
		assertNull(BossSlice.warpFor((String) null, w));
		assertNull(BossSlice.warpFor((BossParser.Spawn) null, w));
	}

	@Test void sliceShowsRecentSpawnAndSendsItsWarp() {
		BossSlice s = new BossSlice();
		Map<String, String> w = DefaultConfig.bossWarps();
		SliceViews.View none = s.view(w, 15 * MIN, 0);
		assertEquals(BossSlice.NONE, none.label());
		assertTrue(none.inert());
		WheelNode node = WheelNode.slice("Boss event", null, "boss");
		assertNull(SliceViews.command(node, none)); // placeholder: nothing to send
		s.spawned(BossParser.parse(GOLEM).orElseThrow(), 1_000_000);
		SliceViews.View v = s.view(w, 15 * MIN, 1_000_000 + 2 * MIN + 5_000);
		assertEquals("Mana Golem · 2m", v.label());
		assertEquals("/warp managolem", SliceViews.command(node, v));
		assertEquals(BossSlice.NONE, s.view(w, 15 * MIN, 1_000_000 + 16 * MIN).label()); // too old
		s.spawned(new BossParser.Spawn("X", "Nowhere", false), 2_000_000);
		SliceViews.View unmapped = s.view(w, 15 * MIN, 2_000_000);
		assertEquals("X · <1m (no warp)", unmapped.label());
		assertNull(SliceViews.command(node, unmapped));
	}

	@Test void deathAnnouncementClearsTheSlice() {
		BossSlice s = new BossSlice();
		Map<String, String> w = DefaultConfig.bossWarps();
		s.spawned(BossParser.parse(GOLEM).orElseThrow(), 0);
		assertFalse(s.onChat("\nBOSSES \u00BB 11 teamed up to take down a SAHUAGIN BOSS\n", MIN)); // another boss
		assertTrue(s.view(w, 5 * MIN, MIN).label().startsWith("Mana Golem"));
		assertTrue(s.onChat("\nBOSSES \u00BB 9 teamed up to take down a MANA GOLEM BOSS\n", 2 * MIN));
		assertEquals(BossSlice.NONE, s.view(w, 5 * MIN, 2 * MIN).label());
		s.spawned(new BossParser.Spawn("Lava Beast", "Tangleroot Volcano", false), 3 * MIN);
		assertTrue(s.view(w, 5 * MIN, 3 * MIN).label().startsWith("Lava Beast")); // a new spawn shows again
		assertTrue(s.onChat("BOSSES \u00BB 7 teamed up to take down a LAVA BEAST", 4 * MIN));
		assertEquals(BossSlice.NONE, s.view(w, 5 * MIN, 4 * MIN).label());
	}

	@Test void killsByTheBossKeepItUpOthersDoNot() {
		BossSlice s = new BossSlice();
		Map<String, String> w = DefaultConfig.bossWarps();
		s.spawned(BossParser.parse(GOLEM).orElseThrow(), 0);
		assertEquals(BossSlice.NONE, s.view(w, 5 * MIN, 6 * MIN).label()); // no sign of life: gone after 5 min
		assertTrue(s.onChat("\u2E5D KamishsRank was slain by Mana Golem Boss [axes]", 4 * MIN));
		assertTrue(s.view(w, 5 * MIN, 8 * MIN).label().startsWith("Mana Golem"));    // still fighting at 4 min
		assertFalse(s.onChat("\u2E5D Danix_005 was slain by Mana Wolf", 9 * MIN));  // another mob
		assertEquals(BossSlice.NONE, s.view(w, 5 * MIN, 10 * MIN).label());
	}
}
