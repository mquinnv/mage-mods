package net.mage.cubewheel.cooldown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.cooldown.ItemAbilities.Ability;
import net.mage.cubewheel.cooldown.ItemAbilities.Action;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Lore shapes follow ManaCube Survival items (plain text as Component.getString() returns it). */
class ItemAbilitiesTest {
	@Test void rightClickSection() {
		ItemAbilities a = ItemAbilities.parse(List.of(
				"Gjallarhorn", "", "ITEM EFFECTS: (Right-Click)", "➟ Duplicate a Monster", "", "Amount: 3 - 6", "Cooldown: 60s", "",
				"Season Vault Access ✔"));
		assertEquals(List.of(new Ability(Action.USE, false, 60_000, "Right-Click")), a.abilities());
		assertTrue(a.uses().isEmpty());
	}

	@Test void colourCodesAndSpacingVariants() {
		ItemAbilities a = ItemAbilities.parse(List.of("§f§lITEM EFFECTS: §7(Right Click)", "§fCooldown:§b 0.5s"));
		assertEquals(500, a.abilities().get(0).cooldownMs());
		assertEquals(Action.USE, a.abilities().get(0).action());
	}

	@Test void sneakRightClick() {
		for (String h : List.of("(Shift + Right Click)", "(Sneak + Right-Click)", "(Right-Click + Sneak)", "(Shift + Right-Click)")) {
			ItemAbilities a = ItemAbilities.parse(List.of("ITEM EFFECTS: " + h, "➟ Break a 5x5x15 trench tunnel", "Cooldown: 7s"));
			assertEquals(1, a.abilities().size(), h);
			assertEquals(Action.USE, a.abilities().get(0).action(), h);
			assertTrue(a.abilities().get(0).sneak(), h);
		}
	}

	@Test void attackAndLeftClick() {
		assertEquals(Action.ATTACK, ItemAbilities.parse(List.of("ITEM EFFECTS: (Attack)", "Cooldown: 5s")).abilities().get(0).action());
		assertEquals(Action.ATTACK, ItemAbilities.parse(List.of("ITEM EFFECTS: (Left-Click)", "Cooldown: 5s")).abilities().get(0).action());
		assertEquals(Action.ATTACK, ItemAbilities.parse(List.of("ITEM EFFECTS: (On Attack)", "Cooldown: 5s")).abilities().get(0).action());
		Ability s = ItemAbilities.parse(List.of("ITEM EFFECTS: (Shift + Left Click)", "Cooldown: 5s")).abilities().get(0);
		assertEquals(Action.ATTACK, s.action());
		assertTrue(s.sneak());
	}

	@Test void consumeAndSneak() {
		assertEquals(Action.CONSUME, ItemAbilities.parse(List.of("ITEM EFFECTS: (When Consumed)", "Cooldown: 5m")).abilities().get(0).action());
		assertEquals(Action.CONSUME, ItemAbilities.parse(List.of("ITEM EFFECTS: (Consume)", "Cooldown: 5m")).abilities().get(0).action());
		assertEquals(Action.CONSUME, ItemAbilities.parse(List.of("ITEM EFFECTS: (When Eaten)", "Cooldown: 5m")).abilities().get(0).action());
		assertEquals(Action.SNEAK, ItemAbilities.parse(List.of("ITEM EFFECTS: (Sneak)", "Cooldown: 20s")).abilities().get(0).action());
	}

	@Test void plainHeadingsWithoutParentheses() {
		ItemAbilities a = ItemAbilities.parse(List.of("Right-Click:", "Fires a beam", "Cooldown: 1m 30s", "", "Left-Click", "Dash", "Cooldown: 10s",
				"Consume:", "Cooldown: 2m"));
		assertEquals(3, a.abilities().size());
		assertEquals(new Ability(Action.USE, false, 90_000, "Right-Click"), a.abilities().get(0));
		assertEquals(new Ability(Action.ATTACK, false, 10_000, "Left-Click"), a.abilities().get(1));
		assertEquals(new Ability(Action.CONSUME, false, 120_000, "Consume"), a.abilities().get(2));
	}

	@Test void passiveSectionsStartNothing() {
		for (String h : List.of("(While Worn)", "(When Held)", "(Block Attack)", "(When Attacked)", "(Take Damage)", "(Shoot)",
				"(Full Set Required)", "(Shift Click to Toggle)", "(While Sneaking)", "(Kill Mob)")) {
			ItemAbilities a = ItemAbilities.parse(List.of("ITEM EFFECTS: " + h, "Trigger: <4❤", "Cooldown: 60s"));
			assertTrue(a.abilities().isEmpty(), h);
		}
	}

	@Test void cooldownValueVariants() {
		assertEquals(10_000, only("Cooldown: 10 seconds"));
		assertEquals(10_000, only("Cooldown: 10 (Seconds)"));
		assertEquals(60_000, only("Cooldown: 60s (Confusion)"));
		assertEquals(270_000, only("Cooldown: 4.5m"));
		assertEquals(300_000, only("Cooldown: 5min"));
		assertEquals(90_000, only("Cooldown: 1m 30s"));
		assertTrue(ItemAbilities.parse(List.of("ITEM EFFECTS: (Right-Click)", "Cooldown: None")).abilities().isEmpty());
		assertEquals(10_000, ItemAbilities.parse(List.of("ITEM EFFECTS: (Right-Click)", "Infinite Uses, 10s Cooldown")).abilities().get(0).cooldownMs());
	}

	private static long only(String line) {
		List<Ability> a = ItemAbilities.parse(List.of("ITEM EFFECTS: (Right-Click)", line)).abilities();
		assertEquals(1, a.size(), line);
		return a.get(0).cooldownMs();
	}

	@Test void unsectionedCooldownIsRightClick() {
		ItemAbilities a = ItemAbilities.parse(List.of("A magic wand", "Cooldown: 30s"));
		assertEquals(List.of(new Ability(Action.USE, false, 30_000, "")), a.abilities());
	}

	@Test void severalSections() {
		ItemAbilities a = ItemAbilities.parse(List.of(
				"ITEM EFFECTS: (Right-Click)", "➟ Launch", "Cooldown: 20s", "", "ITEM EFFECTS: (While Held)", "➟ Speed II",
				"", "ITEM EFFECTS: (Shift + Right Click)", "Cooldown: 45s"));
		assertEquals(2, a.abilities().size());
		assertFalse(a.abilities().get(0).sneak());
		assertTrue(a.abilities().get(1).sneak());
	}

	@Test void descriptiveLinesAreNotHeadings() {
		// "➟ Nausea (60s)" and "Cooldown: 60s (Confusion)" end in parentheses but are not headings.
		ItemAbilities a = ItemAbilities.parse(List.of("ITEM EFFECTS: (Right-Click)", "➟ Nausea (60s)", "Cooldown: 30s"));
		assertEquals(Action.USE, a.abilities().get(0).action());
		assertTrue(ItemAbilities.parse(List.of("Left-Click to Open Crate", "Right-Click to Look Inside")).abilities().isEmpty());
	}

	@Test void uses() {
		assertEquals(42, ItemAbilities.parse(List.of("Uses: 42")).uses().getAsLong());
		assertEquals(1234, ItemAbilities.parse(List.of("§fUses Left: §e1,234")).uses().getAsLong());
		assertEquals(7, ItemAbilities.parse(List.of("Remaining Uses: 7")).uses().getAsLong());
		assertEquals(300, ItemAbilities.parse(List.of("- Uses Left: 300")).uses().getAsLong());
		assertEquals(4999, ItemAbilities.parse(List.of("Uses: 4999/50000")).uses().getAsLong());
		assertTrue(ItemAbilities.parse(List.of("Uses: Infinite")).uses().isEmpty());
		assertTrue(ItemAbilities.parse(List.of("Infinite Uses")).uses().isEmpty());
		assertTrue(ItemAbilities.parse(null).uses().isEmpty());
	}
}
