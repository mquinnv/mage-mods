package net.mage.cubewheel.cooldown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.cooldown.ItemAbilities.Ability;
import net.mage.cubewheel.cooldown.ItemAbilities.Action;
import java.util.List;
import org.junit.jupiter.api.Test;

class CooldownTrackerTest {
	private static final List<Ability> RIGHT = List.of(new Ability(Action.USE, false, 30_000, "Right-Click"));

	@Test void useStartsACountdownKeyedByItemName() {
		CooldownTracker t = new CooldownTracker();
		assertTrue(t.trigger("Samurai Katana", RIGHT, Action.USE, false, 1_000));
		List<CooldownTracker.Entry> a = t.active(1_000);
		assertEquals(1, a.size());
		assertEquals("Samurai Katana", a.get(0).label());
		assertEquals("R", a.get(0).trigger());
		assertEquals(31_000, a.get(0).endsAt());
		assertTrue(t.active(31_000).isEmpty());
	}

	@Test void aRunningCooldownIsNotRestarted() {
		// The server ignores uses while the ability is cooling down, so neither does the HUD.
		CooldownTracker t = new CooldownTracker();
		t.trigger("Wand", RIGHT, Action.USE, false, 0);
		assertFalse(t.trigger("Wand", RIGHT, Action.USE, false, 10_000));
		assertEquals(30_000, t.active(10_000).get(0).endsAt());
		assertTrue(t.trigger("Wand", RIGHT, Action.USE, false, 30_000)); // expired: starts again
	}

	@Test void otherActionsDoNothing() {
		CooldownTracker t = new CooldownTracker();
		assertFalse(t.trigger("Wand", RIGHT, Action.ATTACK, false, 0));
		assertFalse(t.trigger("Wand", RIGHT, Action.CONSUME, false, 0));
		assertFalse(t.trigger("Wand", List.of(), Action.USE, false, 0));
		assertFalse(t.trigger(null, RIGHT, Action.USE, false, 0));
		assertTrue(t.active(0).isEmpty());
	}

	@Test void sneakVariantWinsWhileSneaking() {
		List<Ability> both = List.of(new Ability(Action.USE, false, 20_000, "Right-Click"),
				new Ability(Action.USE, true, 45_000, "Shift + Right Click"));
		CooldownTracker t = new CooldownTracker();
		t.trigger("Axe", both, Action.USE, true, 0);
		assertEquals(1, t.active(0).size());
		assertEquals("Axe", t.active(0).get(0).label());
		assertEquals("\u21E7R", t.active(0).get(0).trigger());
		assertEquals(45_000, t.active(0).get(0).endsAt());
		t.trigger("Axe", both, Action.USE, false, 0);
		assertEquals(2, t.active(0).size());
		assertEquals("R", t.active(0).get(0).trigger()); // soonest first
	}

	@Test void sneakOnlyAbilityNeedsSneaking() {
		List<Ability> sneakOnly = List.of(new Ability(Action.USE, true, 7_000, "Sneak + Right-Click"));
		CooldownTracker t = new CooldownTracker();
		assertFalse(t.trigger("Pick", sneakOnly, Action.USE, false, 0));
		assertTrue(t.trigger("Pick", sneakOnly, Action.USE, true, 0));
		assertEquals("Pick", t.active(0).get(0).label());
	}

	@Test void longNamesAreCutAndAttacksAreL() {
		CooldownTracker t = new CooldownTracker();
		t.trigger("Phoenix Staff of Eternal Flame", List.of(new Ability(Action.ATTACK, false, 5_000,
				"Shoot a Flame that sets enemies ablaze")), Action.ATTACK, false, 0);
		CooldownTracker.Entry e = t.active(0).get(0);
		assertEquals("L", e.trigger());
		assertEquals(CooldownTracker.MAX_NAME, e.label().length());
		assertTrue(e.label().endsWith("\u2026"));
	}

	@Test void plainAbilityAlsoFiresWhileSneakingWhenThereIsNoSneakVariant() {
		CooldownTracker t = new CooldownTracker();
		assertTrue(t.trigger("Wand", RIGHT, Action.USE, true, 0));
	}

	@Test void consumeDetectorReportsFinishedEatingOnly() {
		CooldownTracker.ConsumeDetector<String> d = new CooldownTracker.ConsumeDetector<>();
		assertNull(d.tick(true, 32, "Magic Cookie"));
		assertNull(d.tick(true, 1, "Magic Cookie"));
		assertEquals("Magic Cookie", d.tick(false, 0, null)); // finished
		assertNull(d.tick(false, 0, null));
		assertNull(d.tick(true, 20, "Apple"));
		assertNull(d.tick(false, 0, null)); // let go early: not eaten
		assertNull(d.tick(true, 0, "Apple"));
		assertEquals("Apple", d.tick(false, 0, null));
	}

	@Test void risingEdge() {
		CooldownTracker.Edge e = new CooldownTracker.Edge();
		assertTrue(e.rose(true));
		assertFalse(e.rose(true));
		assertFalse(e.rose(false));
		assertTrue(e.rose(true));
	}

	@Test void clearForgetsEverything() {
		CooldownTracker t = new CooldownTracker();
		t.trigger("Wand", RIGHT, Action.USE, false, 0);
		t.clear();
		assertTrue(t.active(0).isEmpty());
	}
}
