package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.tracker.ProgressExtractor.Progress;
import net.mage.cubewheel.tracker.TrackerStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kills by ability weapons (Phoenix Staff, magic book): the server's ability code deals the damage, so the client sees
 * no attack and no kill, only the action-bar loot line "+7 Mana | +1 Moose Skin" that ManaCube shows the looter alone
 * (capture 2026-10-04 14:00:37, Icehaven). Real lines carry an icon glyph (U+F895) before the drop's name.
 */
class LootKillsTest {
	@TempDir Path dir;
	static final List<String> WORLDS = KillRuleTest.WORLDS;
	static final WorldInfo ICEHAVEN = new WorldInfo(Set.of("icehaven"), true, true);
	static final String MOOSE = "jobs:Hunting Experienced · Slay Zombie Moose in Icehaven";
	static final String MONSTERS = "jobs:Hunting Heavy · Slay Monsters in Icehaven";
	static final String ICON = "";

	static String loot(int mana, int n, String drop) {
		return "+" + mana + " Mana | +" + n + " " + ICON + " " + drop;
	}

	TrackerStore icehaven() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Hunting Experienced · Slay Zombie Moose in Icehaven", new Progress(0, 74), 0);
		s.setObjective(MOOSE, new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Slay 0/74 Zombie Moose in Icehaven", null)), false, false));
		s.update("jobs", "Hunting Heavy · Slay Monsters in Icehaven", new Progress(10, 500), 0);
		s.setObjective(MONSTERS, new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Slay 10/500 Monsters in Icehaven", null)), false, false));
		return s;
	}

	static List<String> ids(List<LocalCounter.Contribution> c) {
		return c.stream().map(LocalCounter.Contribution::id).toList();
	}

	// ---- reading the line ----

	@Test void dropsBehindAnIconGlyphAreRead() {
		assertEquals(List.of("Mana", "Moose Skin"), LootLine.items("+7 Mana | +2 " + ICON + " Moose Skin"));
		assertEquals(List.of("Mana", "Moose Skin"), LootLine.items("SAMURAI KATANA » Primed 3/4 | +7 Mana | +2 " + ICON + " Moose Skin"));
		assertEquals(List.of(new LootLine.Entry("7", "Mana"), new LootLine.Entry("2", "Moose Skin")),
				LootLine.entries("SAMURAI KATANA » Primed 3/4 | +7 Mana | +2 " + ICON + " Moose Skin"));
	}

	@Test void mooseSkinNamesTheZombieMoose() {
		// The drop carries the mob's last word only: "Moose Skin" for "Zombie Moose" ("Frog Leg" for "Dart Frog").
		assertEquals(Optional.of(new LootMatch.Credit("zombie moose", List.of(MOOSE))),
				LootMatch.match(List.of("Mana", "Moose Skin"), icehaven().activeRules(WORLDS), ICEHAVEN));
	}

	@Test void aFullNameMatchWinsOverALastWordOne() {
		java.util.Map<String, CounterRule> rules = java.util.Map.of(
				"a", RemovalKillTest.kill("moose", new CounterRule.AnyWorld()),
				"b", RemovalKillTest.kill("zombie moose", new CounterRule.AnyWorld()));
		assertEquals(Optional.of(new LootMatch.Credit("moose", List.of("a"))), LootMatch.match(List.of("Moose Skin"), rules, ICEHAVEN));
	}

	// ---- the capture ----

	@Test void theCapturedSwordKillCountsOnceAndTheStaffKillCountsFromItsLootLine() {
		TrackerStore s = icehaven();
		LootKills k = new LootKills();
		k.onActionBar("SAMURAI KATANA » Primed 1/4", ICEHAVEN, 7_903);
		k.onActionBar("SAMURAI KATANA » Primed 3/4 | " + loot(7, 2, "Moose Skin"), ICEHAVEN, 8_805);
		k.onActionBar(loot(7, 2, "Moose Skin"), ICEHAVEN, 9_800); // the server repeating the same kill's line
		// 10004: the removal kill, method b (named by its tag), counted the usual way.
		Signal.MobKilled sword = new Signal.MobKilled("", "Zombie Moose 78⺛", Set.of("mob", "monster"), 1, ICEHAVEN);
		assertEquals(Set.of(MOOSE, MONSTERS), Set.copyOf(ids(LocalCounter.onSignal(sword, s, WORLDS, 10_004))));
		k.counted(10_004);
		for (long t = 10_050; t < 25_000; t += 50) assertEquals(List.of(), k.expire(t), "at " + t);
		assertEquals(1, s.counted(MOOSE));
		assertEquals(1, s.counted(MONSTERS));

		// 25706: the staff kill, no attack, no kill event: only its loot line.
		k.onActionBar(loot(7, 1, "Moose Skin"), ICEHAVEN, 25_706);
		assertEquals(List.of(), k.expire(25_706 + LootKills.GRACE_MS - 1)); // still in its grace window
		List<LootKills.Kill> got = k.expire(25_706 + LootKills.GRACE_MS);
		assertEquals(1, got.size());
		assertEquals(ICEHAVEN, got.get(0).world());
		LootKills.Credited c = LootKills.credit(got.get(0), s, WORLDS, 27_706);
		assertEquals(Optional.of("zombie moose"), c.target());
		assertEquals(List.of(MOOSE, MONSTERS), ids(c.added()));
		assertEquals(2, s.counted(MOOSE));
		assertEquals(2, s.counted(MONSTERS));
		assertEquals(List.of(), k.expire(40_000));
	}

	@Test void aLocalKillJustBeforeItsLineConsumesIt() {
		LootKills k = new LootKills();
		k.counted(1_000); // a removal is usually counted ~25 ms before the line
		k.onActionBar(loot(5, 2, "Gorilla Fur"), ICEHAVEN, 1_030);
		assertEquals(List.of(), k.expire(10_000));
	}

	@Test void aLocalKillLongBeforeTheLineDoesNotConsumeIt() {
		LootKills k = new LootKills();
		k.counted(1_000);
		k.onActionBar(loot(5, 2, "Gorilla Fur"), ICEHAVEN, 1_000 + LootKills.GRACE_MS + 1);
		assertEquals(1, k.expire(10_000).size());
	}

	@Test void eachLocalKillConsumesOneLine() {
		LootKills k = new LootKills();
		k.onActionBar(loot(5, 2, "Gorilla Fur"), ICEHAVEN, 1_000);
		k.onActionBar(loot(5, 3, "Gorilla Fur"), ICEHAVEN, 1_400);
		k.counted(1_420);
		assertEquals(List.of("Gorilla Fur"), k.expire(10_000).stream().flatMap(x -> x.loot().stream()).toList());
	}

	// ---- not kills, and repeats ----

	@Test void aToolStatusAloneCountsNothing() {
		LootKills k = new LootKills();
		assertFalse(k.onActionBar("SAMURAI KATANA » Primed 3/4", ICEHAVEN, 0));
		assertFalse(k.onActionBar("SAMURAI KATANA CD: ⬛⬛⬛ (3s)", ICEHAVEN, 100));
		assertFalse(k.onActionBar("", ICEHAVEN, 200));
		assertFalse(k.onActionBar(null, ICEHAVEN, 300));
		assertEquals(List.of(), k.expire(10_000));
	}

	@Test void manaAloneIsMiningNotAKill() {
		LootKills k = new LootKills();
		assertFalse(k.onActionBar("+2 Mana", ICEHAVEN, 0));
		assertFalse(k.onActionBar("+2 Mana | +4 Mana", ICEHAVEN, 500));
		assertEquals(List.of(), k.expire(10_000));
	}

	@Test void aDropWithoutManaIsACatchNotAKill() {
		LootKills k = new LootKills();
		assertFalse(k.onActionBar("+1 " + ICON + " Sad Firefly", ICEHAVEN, 0));
		assertEquals(List.of(), k.expire(10_000));
	}

	@Test void theSameLineShownAgainWhileItLingersIsOneKill() {
		// 2026-10-01: "+3 Mana | +6 Quarry Coal" at 7536, 8536, then again 10376-10477 with only the tool status changing.
		LootKills k = new LootKills();
		assertTrue(k.onActionBar("SAMURAI KATANA » Primed 2/4 | " + loot(3, 6, "Quarry Coal"), ICEHAVEN, 7_536));
		assertFalse(k.onActionBar(loot(3, 6, "Quarry Coal"), ICEHAVEN, 8_536));
		assertFalse(k.onActionBar(loot(3, 6, "Quarry Coal") + " | SAMURAI KATANA » Primed 1/4", ICEHAVEN, 10_376));
		assertFalse(k.onActionBar(loot(3, 6, "Quarry Coal") + " | SAMURAI KATANA » Primed 3/4", ICEHAVEN, 10_477));
		assertEquals(1, k.expire(20_000).size());
	}

	@Test void aNewKillAppendedToALingeringLineIsANewKill() {
		// 2026-10-01: "+5 Mana | +2 Gorilla Fur" then "+5 Mana | +2 Gorilla Fur | +2 Mana" (mining): one kill only.
		LootKills k = new LootKills();
		assertTrue(k.onActionBar(loot(5, 2, "Gorilla Fur"), ICEHAVEN, 0));
		assertFalse(k.onActionBar(loot(5, 2, "Gorilla Fur") + " | +2 Mana", ICEHAVEN, 1_500));
		assertTrue(k.onActionBar(loot(5, 2, "Gorilla Fur") + " | " + loot(4, 1, "Moose Skin"), ICEHAVEN, 2_000));
		assertEquals(2, k.expire(20_000).size());
	}

	@Test void twoDistinctLinesHalfASecondApartAreTwoKills() {
		LootKills k = new LootKills();
		assertTrue(k.onActionBar(loot(7, 1, "Moose Skin"), ICEHAVEN, 1_000));
		assertTrue(k.onActionBar(loot(7, 2, "Moose Skin"), ICEHAVEN, 1_500));
		assertEquals(2, k.expire(10_000).size());
	}

	@Test void theSameLineAfterItsLingerIsANewKill() {
		LootKills k = new LootKills();
		assertTrue(k.onActionBar(loot(7, 1, "Moose Skin"), ICEHAVEN, 0));
		assertTrue(k.onActionBar(loot(7, 1, "Moose Skin"), ICEHAVEN, LootKills.LINGER_MS + 1));
		assertEquals(2, k.expire(20_000).size());
	}

	@Test void clearForgetsHeldLines() {
		LootKills k = new LootKills();
		k.onActionBar(loot(7, 1, "Moose Skin"), ICEHAVEN, 0);
		k.clear();
		assertEquals(List.of(), k.expire(10_000));
	}

	// ---- crediting ----

	@Test void dropsThatNameNoRuleCountOneGenericMonsterKill() {
		TrackerStore s = icehaven();
		LootKills.Credited c = LootKills.credit(new LootKills.Kill(List.of("Quarry Coal"), ICEHAVEN, 0), s, WORLDS, 5);
		assertEquals(Optional.empty(), c.target());
		assertEquals(List.of(MONSTERS), ids(c.added()));
		assertEquals(0, s.counted(MOOSE));
	}

	@Test void creditIsScopedToTheWorldOfTheLine() {
		TrackerStore s = icehaven();
		WorldInfo sandara = new WorldInfo(Set.of("sandara"), true, true);
		assertTrue(LootKills.credit(new LootKills.Kill(List.of("Moose Skin"), sandara, 0), s, WORLDS, 5).added().isEmpty());
	}
}
