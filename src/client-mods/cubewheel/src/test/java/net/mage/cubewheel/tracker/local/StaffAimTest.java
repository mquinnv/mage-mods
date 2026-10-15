package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A Phoenix Staff kill (capture 2026-10-14, Wolfhaven): the shot at a "Dwarven Wizard 80⺛" slime hitbox, the hitbox
 * removed 1.6 s later with no local hit, the loot line "+10 Mana | +3 Quarry Coal" 30 ms after that. The job
 * "Slay 15/43 Dwarven Wizards in Wolfhaven" got nothing: only the loot's generic monster credit.
 */
class StaffAimTest {
	static final WorldInfo WOLFHAVEN = WorldResolver.resolve("minecraft:wolfhaven", KillRuleTest.SIDEBAR, KillRuleTest.WORLDS);
	static final RemovalKills.Hint TAG = new RemovalKills.Hint("Dwarven Wizard 80⺛", RemovalKills.Method.LINKED,
			"nearby minecraft:text_display", 159853657);

	@Test void staffNamesAreStaffs() {
		assertTrue(StaffItems.isStaff("PHOENIX STAFF"));
		assertTrue(StaffItems.isStaff("§6§lArcane Staff"));
		assertFalse(StaffItems.isStaff("SAMURAI KATANA"));
		assertFalse(StaffItems.isStaff(null));
	}

	@Test void coneReachesSixteenBlocksAndSixtyDegrees() {
		// looking along +z
		assertTrue(StaffItems.inCone(0, 0, 10, 0, 0, 1));
		assertTrue(StaffItems.inCone(8, 0, 8, 0, 0, 1)); // 45 degrees off
		assertFalse(StaffItems.inCone(10, 0, 1, 0, 0, 1)); // 84 degrees off
		assertFalse(StaffItems.inCone(0, 0, -5, 0, 0, 1)); // behind
		assertFalse(StaffItems.inCone(0, 0, 17, 0, 0, 1)); // too far
		assertTrue(StaffItems.inCone(0, 0, 0, 0, 0, 1)); // on the eye
	}

	@Test void anAimedHitboxIsRememberedForTheWindowOnly() {
		RemovalKills r = new RemovalKills();
		r.aimed(7, 1000);
		assertTrue(r.aimed(7));
		assertTrue(r.aimedOnly(7));
		assertTrue(r.aimedWithin(7, 1000 + RemovalKills.AIM_WINDOW_MS));
		assertFalse(r.aimedWithin(7, 1000 + RemovalKills.AIM_WINDOW_MS + 1));
		assertFalse(r.aimedWithin(7, 999));
		assertFalse(r.aimed(8));
		r.hit(7, 1500);
		assertFalse(r.aimedOnly(7)); // attacked too: the melee path owns it
		r.clear();
		assertFalse(r.aimed(7));
	}

	@Test void aTagNamesAnAimedHitboxAndAnUnnamedOneIsStillAMonster() {
		RemovalKills r = new RemovalKills();
		assertFalse(r.nameCounts(7, TAG)); // a tag alone names no kill: a right-clicked NPC
		assertFalse(r.generic(7, true));
		r.aimed(7, 1000);
		assertTrue(r.nameCounts(7, TAG));
		assertTrue(r.generic(7, true));
		assertFalse(r.generic(7, false));
	}

	/** The loot line arriving after the removal confirms it; the line is then used up. */
	@Test void anAimedRemovalWaitsForItsLootLine() {
		RemovalKills r = new RemovalKills();
		long shot = 10_000;
		r.aimed(7, shot);
		r.remember(7, TAG);
		RemovalKills.Removed removed = new RemovalKills.Removed(7, "minecraft:slime", TAG.name(), WOLFHAVEN, true);
		assertEquals(Optional.empty(), r.awaitLoot(removed, shot + 1620));
		assertTrue(r.awaitingLoot(7));
		List<RemovalKills.Resolved> resolved = r.onActionBar("SAMURAI KATANA » Primed 1/4 | +10 Mana | +3  Quarry Coal", shot + 1650);
		assertEquals(1, resolved.size());
		assertEquals(removed, resolved.get(0).removed());
		assertEquals(List.of("Mana", "Quarry Coal"), resolved.get(0).loot());
		assertFalse(r.awaitingLoot(7));
	}

	/** No loot line for us: someone else's kill inside our cone. */
	@Test void anAimedRemovalWithoutLootExpires() {
		RemovalKills r = new RemovalKills();
		r.aimed(7, 10_000);
		RemovalKills.Removed removed = new RemovalKills.Removed(7, "minecraft:interaction", "Interaction", WOLFHAVEN, true);
		r.awaitLoot(removed, 11_000);
		assertEquals(List.of(), r.expire(11_000 + RemovalKills.LOOT_WAIT_MS));
		assertEquals(List.of(removed), r.expire(11_000 + RemovalKills.LOOT_WAIT_MS + 1));
		assertTrue(r.aimedOnly(7));
	}

	/** The named kill then credits the Wolfhaven wizard job through the ordinary kill signal. */
	@Test void theNamedKillCreditsTheWizardJob() {
		CounterRule wizards = ObjectiveParser.parse(new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Slay 15/43 Dwarven Wizards in Wolfhaven", null)),
				false, false), KillRuleTest.WORLDS).orElseThrow();
		String name = StackName.parse(TAG.name()).map(StackName.Parsed::name).orElse("");
		assertTrue(RuleMatcher.matches(wizards, new Signal.MobKilled("", name, Set.of("mob", "monster"), 1, WOLFHAVEN)));
	}
}
