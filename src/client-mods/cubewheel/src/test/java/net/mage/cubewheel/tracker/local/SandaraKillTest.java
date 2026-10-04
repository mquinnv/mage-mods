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
import java.util.function.IntPredicate;
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

	static final IntPredicate IN_WINDOW = id -> true;

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
		assertTrue(r.claim(62315, true, true, IN_WINDOW));
		assertFalse(r.claim(73770303, true, true, IN_WINDOW), "the interaction belongs to the Viper already counted");
		assertTrue(r.settled(73772565), "the name tag is settled too");
		// Another snake nearby is untouched.
		r.remember(80000001, viperTag(80000009));
		r.model(80000001, List.of(80000002, 80000003));
		assertTrue(r.claim(80000001, true, true, IN_WINDOW));
	}

	@Test void hitboxesSharingOnlyTheirNameTagAreNotSiblings() {
		// NameResolver takes the nearest tag within 3 blocks: two mobs side by side can both pick the same one.
		RemovalKills r = new RemovalKills();
		r.remember(1, viperTag(9));
		r.model(1, List.of(101, 102));
		r.remember(2, viperTag(9));
		r.model(2, List.of(201, 202));
		assertEquals(Set.of(), r.siblings(1));
		r.remember(3, viperTag(9)); // no model keys at all
		assertEquals(Set.of(), r.siblings(3));
	}

	@Test void twoManaWolvesSideBySideBothCount() {
		// Each wolf has its own hitbox cloud and bone cloud; both resolved the nearer wolf's name tag (method b).
		RemovalKills r = new RemovalKills();
		RemovalKills.Hint wolf = new RemovalKills.Hint("Mana Wolf", RemovalKills.Method.LINKED, "tag minecraft:text_display #50", 50);
		r.remember(1, wolf);
		r.model(1, List.of(11, 12));
		r.remember(2, wolf);
		r.model(2, List.of(21, 22));
		assertTrue(r.claim(1, true, true, IN_WINDOW));
		assertTrue(r.claim(2, true, true, IN_WINDOW));
	}

	@Test void aSharedBoneCloudWithDifferentTagsIsNotOneModel() {
		// A slime can pick up a neighbouring model's bone cloud; its own tag tells it apart.
		RemovalKills r = new RemovalKills();
		r.remember(1, viperTag(9));
		r.model(1, List.of(101, 284));
		r.remember(2, viperTag(8));
		r.model(2, List.of(201, 284));
		assertEquals(Set.of(), r.siblings(1));
		assertTrue(r.claim(1, true, true, IN_WINDOW));
		assertTrue(r.claim(2, true, true, IN_WINDOW));
	}

	@Test void onlySiblingsHitWithinTheWindowAreSettled() {
		RemovalKills r = new RemovalKills();
		r.model(1, List.of(101, 284));
		r.model(2, List.of(201, 284));
		r.model(3, List.of(301, 284));
		assertTrue(r.claim(1, true, true, id -> id == 2));
		assertFalse(r.claim(2, true, true, IN_WINDOW), "hit recently: the same model");
		assertTrue(r.claim(3, true, true, IN_WINDOW), "not hit within the window: left alone");
	}

	@Test void unknownModelsHaveNoSiblings() {
		RemovalKills r = new RemovalKills();
		r.remember(1, null);
		r.remember(2, null);
		r.model(3, List.of());
		assertEquals(Set.of(), r.siblings(1));
		assertEquals(Set.of(), r.siblings(3));
		assertTrue(r.claim(1, true, true, IN_WINDOW));
		assertTrue(r.claim(2, true, true, IN_WINDOW));
	}

	// ---- attack, not use ----

	@Test void aRightClickedModelIsNoMonsterKill() {
		// ModelEngine NPCs, mounts and crates are interaction-on-cloud too: right-clicking one, then its removal
		// (a dismount, a despawn), is not a kill.
		RemovalKills r = new RemovalKills();
		r.used(5, 1_000);
		assertFalse(r.attacked(5));
		assertFalse(r.generic(5, true));
		r.hit(6, 1_000);
		assertTrue(r.attacked(6));
		assertTrue(r.generic(6, true));
		assertFalse(r.generic(6, false), "not a model hitbox");
		// A Firefly Bottle used on it: a catch.
		r.hit(7, 1_000);
		r.fallback(7, "Firefly");
		assertFalse(r.generic(7, true));
	}

	@Test void aUseStillOpensTheLootWindowLikeAHit() {
		RemovalKills r = new RemovalKills();
		r.used(5, 1_000);
		r.onActionBar("+1 Firefly", 1_200);
		assertEquals(java.util.Optional.of(List.of("Firefly")),
				r.awaitLoot(new RemovalKills.Removed(5, "minecraft:interaction", "Interaction", SANDARA), 1_400));
	}

	// ---- looking again on removal ----

	@Test void aTagFoundOnRemovalCountsOnlyWhenTheTagIsTiedToTheModel() {
		// tagTies: what the found tag is tied to (itself, the entity it rides, the hitbox if it rides it or carries it).
		RemovalKills r = new RemovalKills();
		r.model(1, List.of(101, 284));
		// A tag riding the model's bone cloud, or riding the hitbox itself, is the model's own.
		assertTrue(r.acceptReprobe(1, viperTag(9), List.of(9, 284)));
		assertTrue(r.acceptReprobe(1, viperTag(9), List.of(9, 1)));
		// A neighbour's tag is nearest at removal while this model's clouds are still there: it rides its own cloud.
		assertFalse(r.acceptReprobe(1, viperTag(8), List.of(8, 555)));
		// Nothing known of the hitbox's model at hit time, and the tag is not on the hitbox: cannot be checked.
		assertFalse(r.acceptReprobe(2, viperTag(8), List.of(8, 555)));
		assertTrue(r.acceptReprobe(2, viperTag(8), List.of(8, 2)), "a tag riding the hitbox is its own");
		// A tag already counted (a neighbour's) is never used again.
		r.settle(7);
		assertFalse(r.acceptReprobe(1, viperTag(7), List.of(7, 284)));
		assertFalse(r.acceptReprobe(1, null, List.of(101, 284)));
		assertFalse(r.acceptReprobe(1, viperTag(9), null));
		// Its own custom name is always its own.
		assertTrue(r.acceptReprobe(3, new RemovalKills.Hint("Tiger", RemovalKills.Method.OWN, "custom name"), List.of()));
	}

	// ---- a name needs an attack ----

	@Test void aRightClickedHitboxWithATagNearbyIsNoKill() {
		// A ModelEngine NPC/mount/crate: right-clicked (no Firefly Bottle), named by its tag, then removed.
		RemovalKills r = new RemovalKills();
		r.used(5, 1_000);
		r.remember(5, viperTag(9));
		assertFalse(r.nameCounts(5, r.hint(5).orElseThrow()));
		// The same hitbox attacked is a kill named by its tag.
		r.hit(5, 1_100);
		assertTrue(r.nameCounts(5, r.hint(5).orElseThrow()));
	}

	@Test void aBottledFireflyAndAnOwnNameStillCountWithoutAnAttack() {
		RemovalKills r = new RemovalKills();
		r.used(6, 1_000);
		r.fallback(6, "Firefly");
		assertTrue(r.nameCounts(6, new RemovalKills.Hint("Firefly", RemovalKills.Method.LINKED, "rider", 60)));
		assertTrue(r.nameCounts(7, new RemovalKills.Hint("Tiger", RemovalKills.Method.OWN, "custom name")));
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
