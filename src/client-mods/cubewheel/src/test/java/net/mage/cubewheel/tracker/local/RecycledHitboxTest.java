package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * ManaCube keeps one slime id per session and moves it from mob to mob: every Dwarven Miner in Wolfhaven was slime
 * #18627 (capture 2026-10-31). After one kill the id stayed settled for {@link RemovalKills#STALE_MS}, so the next
 * miners were "already counted or refused" and only those killed more than 30 s apart counted. The slime also goes
 * with no kill when it moves on: no loot line, no soul.
 */
class RecycledHitboxTest {
	static final int SLIME = 18627;
	static final RemovalKills.Hint MINER_A = tag(228199358);
	static final RemovalKills.Hint MINER_B = tag(228350353);

	static RemovalKills.Hint tag(int id) {
		return new RemovalKills.Hint("Dwarven Miner 50⺛", RemovalKills.Method.LINKED, "tag minecraft:text_display #" + id, id);
	}

	/** The first kill on the id, as the adapter runs it: hit, probe, claim, removed. */
	static RemovalKills killed(long at) {
		RemovalKills r = new RemovalKills();
		r.hit(SLIME, at);
		r.remember(SLIME, MINER_A);
		assertTrue(r.claim(SLIME, true, true, id -> true));
		r.left(SLIME);
		return r;
	}

	@Test void theNextMinerUnderTheSameIdIsNotSettled() {
		RemovalKills r = killed(13_090);
		assertTrue(r.settled(SLIME)); // until the id is touched again
		r.hit(SLIME, 16_000);
		assertFalse(r.settled(SLIME));
		assertTrue(r.recycled(SLIME));
		assertTrue(r.lootConfirms(SLIME));
	}

	@Test void theNextMinerIsProbedAgainAndKeepsItsOwnTag() {
		RemovalKills r = killed(13_090);
		r.hit(SLIME, 16_000);
		assertFalse(r.seen(SLIME)); // the adapter probes on this hit
		assertEquals(Optional.empty(), r.hint(SLIME)); // never the last miner's tag
		r.remember(SLIME, MINER_B);
		assertFalse(r.tagUsedByOther(SLIME));
		assertTrue(r.nameCounts(SLIME, MINER_B));
	}

	@Test void aRemovalWithNoHitSinceIsTheHitboxMovingOn() {
		RemovalKills r = killed(13_090);
		assertTrue(r.hasLeft(SLIME));
		r.hit(SLIME, 16_000);
		assertFalse(r.hasLeft(SLIME));
		r.left(SLIME);
		assertTrue(r.hasLeft(SLIME));
	}

	@Test void aStaffShotOrAnItemUseAlsoBringsTheIdBack() {
		RemovalKills aim = killed(0);
		aim.aimed(SLIME, 1_000);
		assertFalse(aim.settled(SLIME));
		assertTrue(aim.aimedOnly(SLIME));
		RemovalKills use = killed(0);
		use.used(SLIME, 1_000);
		assertFalse(use.settled(SLIME));
		assertFalse(use.attacked(SLIME)); // the last miner's attack is not this one's
	}

	@Test void aLootLineFromTheLastKillDoesNotConfirmTheNext() {
		RemovalKills r = killed(13_090);
		r.onActionBar("+3 Mana | +4 Quarry Iron", 13_800);
		r.hit(SLIME, 16_000);
		RemovalKills.Removed removed = new RemovalKills.Removed(SLIME, "minecraft:slime", "", null, true);
		assertEquals(Optional.empty(), r.awaitLoot(removed, 16_500));
		assertEquals(1, r.onActionBar("+3 Mana | +3 Quarry Coal", 16_530).size());
	}

	@Test void noLootLineMeansItOnlyMovedOn() {
		RemovalKills r = killed(13_090);
		r.hit(SLIME, 16_000);
		RemovalKills.Removed removed = new RemovalKills.Removed(SLIME, "minecraft:slime", "", null, true);
		assertEquals(Optional.empty(), r.awaitLoot(removed, 16_130));
		assertEquals(List.of(removed), r.expire(16_130 + RemovalKills.LOOT_WAIT_MS + 1));
		assertTrue(r.lootConfirms(SLIME)); // the adapter drops it
	}

	@Test void aTagAnotherHitboxCountedIsNotCountedAgain() {
		RemovalKills r = killed(13_090);
		r.hit(SLIME, 16_000);
		r.remember(SLIME, MINER_B);
		int interaction = 228549000;
		r.hit(interaction, 16_000);
		r.remember(interaction, MINER_B);
		assertTrue(r.claim(interaction, true, true, id -> true));
		assertTrue(r.tagUsedByOther(SLIME));
	}

	@Test void onlyAnIdThatCameBackIsRecycled() {
		RemovalKills r = new RemovalKills();
		r.hit(SLIME, 0);
		assertFalse(r.recycled(SLIME));
		assertFalse(r.lootConfirms(SLIME));
	}
}
