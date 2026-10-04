package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.tracker.ProgressExtractor.Progress;
import net.mage.cubewheel.tracker.ProgressLabel;
import net.mage.cubewheel.tracker.TrackerStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Sandara's custom-model mobs (capture 2026-10-01, 14:14-14:47): rattlesnakes and vipers are an interaction hitbox
 * riding a cloud plus invisible slimes, named by a text_display on its own cloud, removed without a death event.
 * Three entries count them: "Slay Rattle Snakes in Sandara" (job, named), "Slay Monsters in Sandara" (job, generic)
 * and "Slay 1,000 Sandara Monsters" (Sandara Slayer pquest, generic, shown as a percent).
 */
class SandaraKillTest {
	@TempDir Path dir;
	static final List<String> WORLDS = KillRuleTest.WORLDS;
	static final WorldInfo SANDARA = new WorldInfo(Set.of("sandara"), true, true);
	static final String SLAYER = "pquests:Sandara Slayer";
	static final String MONSTERS = "jobs:Hunting Heavy · Slay Monsters in Sandara";
	static final String SNAKES = "jobs:Hunting Experienced · Slay Rattle Snakes in Sandara";

	static ObjectiveInfo obj(String line, boolean special) {
		return new ObjectiveInfo(List.of(new ObjectiveInfo.Sub(line, null)), special, false);
	}

	/** Sandara Slayer 37/100 (percent) with objective 1,000; jobs Monsters 330/670; Rattle Snakes 29/58. */
	TrackerStore sandara() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("pquests", "Sandara Slayer", new Progress(37, 100), 0);
		s.setObjective(SLAYER, obj("Slay 1,000 Sandara Monsters", false));
		s.update("jobs", "Hunting Heavy · Slay Monsters in Sandara", new Progress(330, 670), 0);
		s.setObjective(MONSTERS, obj("Slay 330/670 Monsters in Sandara", false));
		s.update("jobs", "Hunting Experienced · Slay Rattle Snakes in Sandara", new Progress(29, 58), 0);
		s.setObjective(SNAKES, obj("Slay 29/58 Rattle Snakes in Sandara", false));
		return s;
	}

	static List<String> ids(List<LocalCounter.Contribution> c) {
		return c.stream().map(LocalCounter.Contribution::id).toList();
	}

	@Test void aNamedSandaraKillCreditsAllThreeEntriesAndTheSlayerShowsItsObjectiveScale() {
		TrackerStore s = sandara();
		Signal.MobKilled snake = new Signal.MobKilled("", "Rattle Snake 30⺛", Set.of("mob", "monster"), 1, SANDARA);
		List<LocalCounter.Contribution> added = LocalCounter.onSignal(snake, s, WORLDS, 5);
		assertEquals(Set.of(SNAKES, MONSTERS, SLAYER), Set.copyOf(ids(added)));
		assertEquals("~371/1,000", ProgressLabel.of(s, SLAYER, WORLDS).orElseThrow().count());
		assertEquals("~331/670", ProgressLabel.of(s, MONSTERS, WORLDS).orElseThrow().count());
		assertEquals("~30/58", ProgressLabel.of(s, SNAKES, WORLDS).orElseThrow().count());
	}

	@Test void theNamedRuleIsCreditedFirstThenTheRestInEntryOrder() {
		TrackerStore s = sandara();
		assertEquals(List.of(SLAYER, MONSTERS, SNAKES), List.copyOf(s.activeRules(WORLDS).keySet()));
		Signal.MobKilled snake = new Signal.MobKilled("", "Rattle Snake", Set.of("mob", "monster"), 1, SANDARA);
		assertEquals(List.of(SNAKES, SLAYER, MONSTERS), ids(LocalCounter.onSignal(snake, s, WORLDS, 5)));
	}

	@Test void anUnnamedHitboxKillCreditsExactlyTheTwoGenericRules() {
		TrackerStore s = sandara();
		List<LocalCounter.Contribution> added = LocalCounter.genericKill(SANDARA, s, WORLDS, 5, Set.of());
		assertEquals(List.of(SLAYER, MONSTERS), ids(added));
		assertEquals(0, s.counted(SNAKES));
		// Elsewhere it counts nothing: both generic rules are scoped to Sandara.
		WorldInfo tangleroot = new WorldInfo(Set.of("tangleroot"), true, true);
		assertTrue(LocalCounter.genericKill(tangleroot, sandara(), WORLDS, 5, Set.of()).isEmpty());
	}

	@Test void genericCreditSkipsExcludedIds() {
		TrackerStore s = sandara();
		assertEquals(List.of(MONSTERS), ids(LocalCounter.genericKill(SANDARA, s, WORLDS, 5, Set.of(SLAYER))));
	}

	@Test void aLootNamedSnakeCountsOnceInEachOfTheThreeEntries() {
		TrackerStore s = sandara();
		LootMatch.Credit credit = LootMatch.match(List.of("Mana", "Rattlesnake Meat"), s.activeRules(WORLDS), SANDARA)
				.orElseThrow();
		assertEquals(List.of(SNAKES), credit.ruleIds());
		List<LocalCounter.Contribution> added = LocalCounter.lootKill(credit.ruleIds(), true, SANDARA, s, WORLDS, 5);
		assertEquals(List.of(SNAKES, SLAYER, MONSTERS), ids(added));
		assertEquals(1, s.counted(SNAKES));
		assertEquals(1, s.counted(MONSTERS));
		assertEquals(1, s.counted(SLAYER)); // not 2
	}

	@Test void aLootNamedKillThatIsNoModelHitboxCreditsOnlyTheNamedRules() {
		TrackerStore s = sandara();
		assertEquals(List.of(SNAKES), ids(LocalCounter.lootKill(List.of(SNAKES), false, SANDARA, s, WORLDS, 5)));
		assertEquals(0, s.counted(MONSTERS));
	}

	// ---- the hitbox window ----

	@Test void aRemovalEightyTwoTicksAfterTheHitIsStillThePlayersKill() {
		KillAttribution k = new KillAttribution();
		k.onDamage(5, 1, 100);
		k.expire(182);
		assertTrue(k.hitByWithin(5, 1, 182, RemovalKills.HITBOX_WINDOW_TICKS));
		// The capture's slowest hitbox removal was 4.6 s (92 ticks); the window keeps a margin past it.
		k.expire(215);
		assertTrue(k.hitByWithin(5, 1, 215, RemovalKills.HITBOX_WINDOW_TICKS));
		assertFalse(k.hitByWithin(5, 1, 100 + RemovalKills.HITBOX_WINDOW_TICKS + 1, RemovalKills.HITBOX_WINDOW_TICKS));
	}

	@Test void aDeathStillNeedsAHitWithinVanillasHundredTicks() {
		// Plugins read vanilla's getKiller(): the last player hit is remembered 100 ticks, whatever the hitbox window.
		KillAttribution k = new KillAttribution();
		k.onDamage(5, 1, 100);
		k.expire(201);
		assertEquals(KillAttribution.Verdict.EXPIRED, k.onDeathVerdict(5, 1, 201));
	}

	// ---- one model, several hitboxes ----

	static RemovalKills.Hint viperTag(int tagId) {
		return new RemovalKills.Hint("Viper 75⺛", RemovalKills.Method.LINKED, "tag minecraft:text_display #" + tagId, tagId);
	}

	@Test void siblingHitboxesOfOneModelCountOnce() {
		// Real ids, 14:14:00: interaction 73770303 on cloud 73770302, slime 62315 on cloud 62314, both by the model's
		// bone cloud 73770284 and named by text_display 73772565. The slime's removal and the interaction's both
		// claimed the same Viper.
		RemovalKills r = new RemovalKills();
		r.remember(73770303, viperTag(73772565));
		r.model(73770303, List.of(73770302, 73770284));
		r.remember(62315, viperTag(73772565));
		r.model(62315, List.of(62314, 73770284));
		assertEquals(Set.of(73770303), r.siblings(62315));
		assertTrue(r.claim(62315, true, true));
		assertFalse(r.claim(73770303, true, true), "the interaction belongs to the Viper already counted");
		assertTrue(r.settled(73772565), "the name tag is settled too");
		// Another snake nearby is untouched.
		r.remember(80000001, viperTag(80000009));
		r.model(80000001, List.of(80000002, 80000003));
		assertTrue(r.claim(80000001, true, true));
	}

	@Test void hitboxesSharingOnlyTheirNameTagAreSiblings() {
		RemovalKills r = new RemovalKills();
		r.remember(1, viperTag(9));
		r.remember(2, viperTag(9));
		assertEquals(Set.of(2), r.siblings(1));
		assertTrue(r.claim(1, true, true));
		assertFalse(r.claim(2, true, true));
	}

	@Test void unknownModelsHaveNoSiblings() {
		RemovalKills r = new RemovalKills();
		r.remember(1, null);
		r.remember(2, null);
		r.model(3, List.of());
		assertEquals(Set.of(), r.siblings(1));
		assertEquals(Set.of(), r.siblings(3));
		assertTrue(r.claim(1, true, true));
		assertTrue(r.claim(2, true, true));
	}

	@Test void hittingASlimeKeepsTheModelsInteractionInsideItsWindow() {
		// The player hits the interaction once, then only the model's slimes; the interaction is removed 140 ticks
		// after its own hit. A hit on any sibling refreshes the others (what the adapter does on each local hit).
		RemovalKills r = new RemovalKills();
		KillAttribution k = new KillAttribution();
		r.model(10, List.of(302, 284));
		r.model(20, List.of(314, 284));
		k.onDamage(10, 1, 100);
		for (long t = 110; t <= 200; t += 10) {
			k.onDamage(20, 1, t);
			for (int sibling : r.siblings(20)) k.onDamage(sibling, 1, t);
			k.expire(t);
		}
		k.expire(240);
		assertTrue(k.hitByWithin(10, 1, 240, RemovalKills.HITBOX_WINDOW_TICKS));
	}
}
