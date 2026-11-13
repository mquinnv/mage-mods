package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * One Mana Wolf counted twice toward "Slay Monsters in Wolfhaven" (issue #5, capture 2026-11-13). ManaCube's
 * per-player invisible slime (#82124, the same id all session) is a hitbox it moves from mob to mob. It was hit at
 * t=600839 and removed at t=605505 while the wolf lived on (its tag #143134270 read 25⺛ 40 ms later). That was its
 * first removal in the session, so it was not yet known as recycled, and it counted at once off its tag ("removal,
 * method b ... local hit"), with no loot line. The real death at t=613754 was confirmed by "+3 Mana | +2 Mana Wolf
 * Pelt" and counted again.
 * <p>
 * {@link Adapter} runs the same decisions as LocalSignals.onRemoveEntities / onStaffKill / creditLoot / the tick's
 * expiry, with the inputs the capture shows (each removal: hit within the window, near, in survival).
 */
class ManaWolfMoveOffTest {
	static final int SLIME = 82124;
	static final int SLIME_CLOUD = 82121;
	static final int BONE_CLOUD = 143133233;
	static final int INTERACTION = 143133269;
	static final int INTERACTION_CLOUD = 143133268;
	static final int TAG = 143134270;
	static final String LOOT = "+3 Mana | +2  Mana Wolf Pelt";

	static RemovalKills.Hint wolf(int hp) {
		return new RemovalKills.Hint("Mana Wolf " + hp + "⺛", RemovalKills.Method.LINKED, "tag minecraft:text_display #" + TAG, TAG);
	}

	/** LocalSignals' kill decisions over {@link RemovalKills}, counting what it would count. */
	static final class Adapter {
		final RemovalKills r = new RemovalKills();
		int kills;

		/** onAttack: hit, then probe (once per id until it comes back) and remember its model. */
		void attack(int id, RemovalKills.Hint tag, List<Integer> modelKeys, long at) {
			r.hit(id, at);
			if (r.seen(id)) return;
			r.remember(id, tag);
			r.model(id, modelKeys);
		}

		/** onRemoveEntities for one id, hit within the window and near; {@code invisibleMob}: ManaCube's slime. */
		void removed(int id, boolean invisibleMob, long now) {
			r.tick(now);
			if (r.hasLeft(id)) return;
			if (invisibleMob) r.left(id);
			if (r.awaitingLoot(id) || r.settled(id)) return;
			if (r.lootConfirms(id)) {
				staffKill(id, now);
			} else if (r.claim(id, true, true, sibling -> true)) {
				RemovalKills.Hint h = r.hint(id).orElse(null);
				if (h != null && r.tagUsedByOther(id, h)) return;
				if (h != null && r.nameCounts(id, h)) {
					kills++;
					return;
				}
				RemovalKills.Removed rm = new RemovalKills.Removed(id, "minecraft:slime", "", null, true, h);
				r.awaitLoot(rm, now).ifPresent(loot -> credit(rm));
			}
		}

		/** onStaffKill: settles, then waits for the loot line. */
		void staffKill(int id, long now) {
			if (r.tagUsedByOther(id)) {
				r.settle(id);
				return;
			}
			r.settle(id);
			r.settleModel(id, sibling -> true);
			RemovalKills.Removed rm = new RemovalKills.Removed(id, "minecraft:slime", "", null, true, r.hint(id).orElse(null));
			r.awaitLoot(rm, now).ifPresent(loot -> credit(rm));
		}

		void credit(RemovalKills.Removed rm) {
			if (r.lootConfirms(rm.entityId()) && rm.hint() != null && r.tagUsedByOther(rm.entityId(), rm.hint())) return;
			kills++;
		}

		void actionBar(String text, long now) {
			for (RemovalKills.Resolved res : r.onActionBar(text, now)) credit(res.removed());
		}

		/** onEndTick's expiry: a loot-confirmed removal with no line is no kill; any other counts unnamed. */
		void expire(long now) {
			for (RemovalKills.Removed rm : r.expire(now)) if (!r.lootConfirms(rm.entityId())) kills++;
		}
	}

	@Test void theSlimeMovingOffALiveWolfIsNoKill() {
		Adapter a = new Adapter();
		a.attack(SLIME, wolf(60), List.of(SLIME_CLOUD, BONE_CLOUD), 600_839);
		a.attack(SLIME, wolf(60), List.of(SLIME_CLOUD, BONE_CLOUD), 600_842);
		// 4.7 s later the slime goes; the wolf is still alive. Its first removal this session.
		a.removed(SLIME, true, 605_505);
		assertEquals(0, a.kills, "the slime moving off a live wolf, with no loot line, is no kill");
		a.expire(605_505 + RemovalKills.LOOT_WAIT_MS + 1);
		assertEquals(0, a.kills);
		// The slime comes back to the same wolf and the wolf dies: loot line, then the removal 15 ms later.
		a.attack(SLIME, wolf(20), List.of(SLIME_CLOUD, BONE_CLOUD), 613_589);
		a.attack(SLIME, wolf(20), List.of(SLIME_CLOUD, BONE_CLOUD), 613_639);
		a.actionBar(LOOT, 613_739);
		a.removed(SLIME, true, 613_754);
		a.expire(613_754 + RemovalKills.LOOT_WAIT_MS + 1);
		assertEquals(1, a.kills, "one Mana Wolf, one kill");
	}

	@Test void theFirstRemovalOfTheSlimeStillCountsWhenItsLootLineComes() {
		// The same slime's first removal being the real death: the loot line confirms it, once.
		Adapter a = new Adapter();
		a.attack(SLIME, wolf(60), List.of(SLIME_CLOUD, BONE_CLOUD), 600_839);
		a.removed(SLIME, true, 605_505);
		a.actionBar(LOOT, 605_560);
		a.expire(605_505 + RemovalKills.LOOT_WAIT_MS + 1);
		assertEquals(1, a.kills);
	}

	@Test void aMoveOffDoesNotKeepTheWolfsInteractionFromCountingItsDeath() {
		// Both hitboxes hit; the slime moves off (it settled its model's interaction and tag while it waited), then
		// the wolf dies and its interaction goes first, with no loot line seen (as for "Mana Wolf 37⺛" later the
		// same day). The interaction is the only evidence of that kill and must still count it.
		Adapter a = new Adapter();
		a.attack(INTERACTION, wolf(60), List.of(INTERACTION_CLOUD, BONE_CLOUD), 600_800);
		a.attack(SLIME, wolf(60), List.of(SLIME_CLOUD, BONE_CLOUD), 600_839);
		assertEquals(java.util.Set.of(INTERACTION), a.r.siblings(SLIME));
		a.removed(SLIME, true, 605_505);
		a.expire(605_505 + RemovalKills.LOOT_WAIT_MS + 1);
		assertEquals(0, a.kills);
		assertFalse(a.r.settled(INTERACTION), "a move-off that counted nothing settles nothing");
		assertFalse(a.r.settled(TAG));
		a.attack(SLIME, wolf(20), List.of(SLIME_CLOUD, BONE_CLOUD), 613_589);
		a.attack(INTERACTION, wolf(20), List.of(INTERACTION_CLOUD, BONE_CLOUD), 613_600);
		a.removed(INTERACTION, false, 613_700);
		assertEquals(1, a.kills);
		a.removed(SLIME, true, 613_754); // the same model: the tag already named the counted kill
		a.expire(613_754 + RemovalKills.LOOT_WAIT_MS + 1);
		assertEquals(1, a.kills);
	}

	@Test void aSlimeBackOnTheSameWolfReleasesWhatItsRemovalSettled() {
		// The Cursed Watcher case (MorendDoubleCountTest) on a first removal: hit again within the loot wait under the
		// same tag, the removal is dropped; its model's interaction is free to count the real kill.
		Adapter a = new Adapter();
		a.attack(INTERACTION, wolf(60), List.of(INTERACTION_CLOUD, BONE_CLOUD), 600_800);
		a.attack(SLIME, wolf(60), List.of(SLIME_CLOUD, BONE_CLOUD), 600_839);
		a.removed(SLIME, true, 605_505);
		assertTrue(a.r.settled(INTERACTION));
		a.attack(SLIME, wolf(25), List.of(SLIME_CLOUD, BONE_CLOUD), 605_900);
		assertFalse(a.r.awaitingLoot(SLIME));
		assertFalse(a.r.settled(INTERACTION));
		assertFalse(a.r.settled(TAG));
	}
}
