package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * "Slay Monsters in Morend" counted 138 for 100 real kills (capture 2026-11-01; Michael 2026-11-04: "monster hunting
 * jobs pretty much always overcount"). Three things, all on the one recycled slime hitbox (#27910 all session):
 * <ul>
 * <li>the hitbox leaves and comes back on the same mob mid-fight (Cursed Watcher tag #194706626: removed at 144 HP,
 * hit again, removed at 72 HP), and the first removal, still waiting for its loot line, was confirmed by the kill's;
 * <li>ManaCube re-sends the action bar whenever a part changes (the cooldown digits), and every re-send of
 * "+5 Mana | +1 Cursed Goo" confirmed another pending removal (two kills 34 ms apart, three in 91 ms);
 * <li>on its way back the hitbox was named after the nearest named thing, a "1.63x" damage-multiplier armor stand
 * 0.28 blocks away, not the mob's own tag 0.85 blocks away, so the one-tag-one-kill guard never saw the same tag.
 * </ul>
 */
class MorendDoubleCountTest {
	static final int SLIME = 27910;
	static final int INTERACTION = 194706123;
	static final RemovalKills.Hint WATCHER = tag(194706626, "Cursed Watcher 144⺛");
	static final RemovalKills.Hint MORENDMAN = tag(194706862, "Morendman 150⺛");
	static final String LINE = "+5 Mana | +1  Cursed Goo";
	static final String RESENT = "TROLL GLAIVE CD: ⬛ (1s) | +5 Mana | +1  Cursed Goo";

	static RemovalKills.Hint tag(int id, String name) {
		return new RemovalKills.Hint(name, RemovalKills.Method.LINKED, "tag minecraft:text_display #" + id, id);
	}

	/** The adapter's recycled-hitbox removal: settled, its model settled, then waiting for a loot line. */
	static RemovalKills.Removed removed(RemovalKills r, int id, RemovalKills.Hint hint, long at) {
		r.settle(id);
		r.settleModel(id, sibling -> true);
		RemovalKills.Removed removed = new RemovalKills.Removed(id, "minecraft:slime", hint.name(), null, true, hint);
		return removed;
	}

	/** A slime the id already served once, so it is recycled: its removals need a loot line. */
	static RemovalKills recycled() {
		RemovalKills r = new RemovalKills();
		r.hit(SLIME, 0);
		r.remember(SLIME, tag(1, "Void Scuttler 20⺛"));
		r.left(SLIME);
		r.hit(SLIME, 10_000);
		assertTrue(r.recycled(SLIME));
		return r;
	}

	@Test void theSameLootLineShownAgainConfirmsNothingMore() {
		RemovalKills r = recycled();
		r.remember(SLIME, WATCHER);
		RemovalKills.Removed first = removed(r, SLIME, WATCHER, 10_500);
		assertEquals(Optional.empty(), r.awaitLoot(first, 10_500));
		assertEquals(1, r.onActionBar(LINE, 10_540).size());
		// The cooldown digits changed, so the bar came again with the same loot: not a second kill.
		r.hit(INTERACTION, 10_560);
		RemovalKills.Removed second = new RemovalKills.Removed(INTERACTION, "minecraft:interaction", "Interaction", null, true);
		assertEquals(Optional.empty(), r.awaitLoot(second, 10_580));
		assertEquals(List.of(), r.onActionBar(RESENT, 10_600));
		assertEquals(List.of(), r.onActionBar("TROLL GLAIVE CD: ⬛ (0s) | +5 Mana | +1  Cursed Goo", 10_650));
		assertTrue(r.awaitingLoot(INTERACTION));
		// A different drop is a new line.
		assertEquals(1, r.onActionBar("+5 Mana | +1  Void Claw", 10_700).size());
	}

	@Test void aLineWithMoreOfTheSameDropIsANewKill() {
		RemovalKills r = recycled();
		assertEquals(0, r.onActionBar(LINE, 10_000).size());
		RemovalKills.Removed removed = new RemovalKills.Removed(SLIME, "minecraft:slime", "", null, true);
		assertTrue(r.awaitLoot(removed, 10_100).isPresent()); // the line from 100 ms before
		RemovalKills.Removed again = new RemovalKills.Removed(SLIME, "minecraft:slime", "", null, true);
		assertEquals(Optional.empty(), r.awaitLoot(again, 10_200));
		assertEquals(1, r.onActionBar("+10 Mana | +2  Cursed Goo", 10_300).size());
	}

	@Test void aHitboxBackOnTheSameMobWasNotKilled() {
		RemovalKills r = recycled();
		r.remember(SLIME, WATCHER);
		RemovalKills.Removed first = removed(r, SLIME, WATCHER, 10_500);
		assertEquals(Optional.empty(), r.awaitLoot(first, 10_500));
		assertTrue(r.settled(WATCHER.sourceId()));
		// 600 ms later the slime is hit again and the probe finds the same tag, now "Cursed Watcher 72⺛": the mob is
		// alive, so the removal was the hitbox moving, not a kill, and its tag is free to name the real one.
		r.hit(SLIME, 11_100);
		assertEquals(Optional.of(first), r.remember(SLIME, tag(WATCHER.sourceId(), "Cursed Watcher 72⺛")));
		assertFalse(r.awaitingLoot(SLIME));
		assertFalse(r.settled(WATCHER.sourceId()));
		assertEquals(List.of(), r.onActionBar(LINE, 11_740)); // the kill's line, nothing pending: kept for the removal
		RemovalKills.Removed second = removed(r, SLIME, WATCHER, 11_770);
		assertFalse(r.tagUsedByOther(SLIME));
		assertEquals(Optional.of(List.of("Mana", "Cursed Goo")), r.awaitLoot(second, 11_770));
	}

	@Test void aHitboxBackOnAnotherMobLeavesTheFirstKillWaiting() {
		RemovalKills r = recycled();
		r.remember(SLIME, WATCHER);
		RemovalKills.Removed first = removed(r, SLIME, WATCHER, 10_500);
		assertEquals(Optional.empty(), r.awaitLoot(first, 10_500));
		r.hit(SLIME, 10_600);
		assertEquals(Optional.empty(), r.remember(SLIME, MORENDMAN));
		assertTrue(r.awaitingLoot(SLIME));
		assertTrue(r.settled(WATCHER.sourceId()));
		List<RemovalKills.Resolved> resolved = r.onActionBar(LINE, 10_700);
		assertEquals(1, resolved.size());
		assertEquals(WATCHER, resolved.get(0).removed().hint()); // named as it was when it went, not as the id is now
	}

	@Test void aDamageMultiplierHologramIsNotAName() {
		var multiplier = new NameResolver.Candidate(NameResolver.Relation.TAG, "1.63x", 0.28 * 0.28, "armor_stand #195464357", 195464357);
		var own = new NameResolver.Candidate(NameResolver.Relation.TAG, "Morendman 6⺛", 0.85 * 0.85, "text_display #194706862", 194706862);
		assertEquals(Optional.of(own), NameResolver.pick(List.of(multiplier, own)));
		assertEquals(Optional.empty(), NameResolver.pick(List.of(multiplier)));
		assertEquals(Optional.empty(), NameResolver.pick(List.of(new NameResolver.Candidate(NameResolver.Relation.RIDER, "2x", 0, "s", 3))));
		assertTrue(NameResolver.pick(List.of(new NameResolver.Candidate(NameResolver.Relation.TAG, "5x Tiger", 1, "s", 4))).isPresent());
	}
}
