package com.github.nyuppo.group;

import com.github.nyuppo.config.HotbarCycleConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemGroupsTest {
	private static final ItemGroups DEFAULTS = ItemGroups.parse(HotbarCycleConfig.DEFAULT_ITEM_GROUPS);

	private static ItemGroups.Item item(String id, String name) {
		return new ItemGroups.Item(id, name, false, false);
	}

	// Michael's owned gear (ManaCube SVAS catalog, 2026-11-04): staves are netherite hoes, rods fishing rods.
	@Test void toolsAndWeaponsShareAGroup() {
		ItemGroups.Item staff = new ItemGroups.Item("minecraft:netherite_hoe", "PHOENIX STAFF", true, false);
		ItemGroups.Item pick = new ItemGroups.Item("minecraft:netherite_pickaxe", "ELDEN PICKAXE", true, false);
		ItemGroups.Item sword = new ItemGroups.Item("minecraft:netherite_sword", "GOD KILLER", true, true);
		assertEquals(0, DEFAULTS.groupOf(staff));
		assertEquals(0, DEFAULTS.groupOf(pick));
		assertEquals(0, DEFAULTS.groupOf(sword));
		assertEquals(0, DEFAULTS.groupOf(item("minecraft:bow", "SPIRIT BOW")));
		assertEquals(0, DEFAULTS.groupOf(item("minecraft:crossbow", "SOUL CROSSBOW")));
		assertEquals(0, DEFAULTS.groupOf(item("minecraft:fishing_rod", "BLOOM FISHINGROD")));
		assertEquals(0, DEFAULTS.groupOf(item("minecraft:paper", "ZOMBIE HAND"))); // "Unarmed Weapon", but paper
	}

	@Test void theConsumablesShareTheirOwnGroup() {
		assertEquals(1, DEFAULTS.groupOf(item("minecraft:golden_apple", "CANDY APPLE")));
		assertEquals(1, DEFAULTS.groupOf(item("minecraft:honey_bottle", "BOTTLE OF NECTAR")));
		assertEquals(1, DEFAULTS.groupOf(item("minecraft:honey_bottle", "Champion Potion")));
	}

	// "other stuff shouldn't rotate through the main bar"
	@Test void everythingElseIsInNoGroup() {
		assertEquals(-1, DEFAULTS.groupOf(item("minecraft:cobblestone", "Cobblestone")));
		assertEquals(-1, DEFAULTS.groupOf(item("minecraft:golden_apple", "Golden Apple")));
		assertEquals(-1, DEFAULTS.groupOf(item("minecraft:paper", "ONE WISH WILLOW")));
		assertEquals(-1, DEFAULTS.groupOf(item("minecraft:golden_apple", "PUMPKIN CANDY APPLE"))); // anchored
		assertEquals(-1, DEFAULTS.groupOf(null));
	}

	@Test void idsMayLeaveOutTheMinecraftNamespaceAndBadPatternsAreReported() {
		ItemGroups g = ItemGroups.parse(List.of("torch | name:([", "  ", "Minecraft:Bow"));
		assertEquals(2, g.size());
		assertEquals(0, g.groupOf(item("minecraft:torch", "Torch")));
		assertEquals(1, g.groupOf(item("minecraft:bow", "Bow")));
		assertEquals(1, g.problems().size());
	}

	@Test void nextSlotWalksForwardAndWraps() {
		boolean[] in = {false, true, false, true, false}; // slots 9..13: matches at 10 and 12
		assertEquals(10, ItemGroups.nextSlot(in, 9, 8, 1)); // fresh start: the first match
		assertEquals(12, ItemGroups.nextSlot(in, 9, 10, 1));
		assertEquals(10, ItemGroups.nextSlot(in, 9, 12, 1)); // wraps
		assertEquals(12, ItemGroups.nextSlot(in, 9, 14, -1)); // fresh start backwards: the last match
		assertEquals(10, ItemGroups.nextSlot(in, 9, 12, -1));
		assertEquals(-1, ItemGroups.nextSlot(new boolean[5], 9, 8, 1));
	}

	/** Three of a group, A in hand and B, C in the inventory: presses go B, C, A, B (each swap leaves the old hand item behind). */
	@Test void repeatedPressesVisitEveryItemOfTheGroup() {
		String[] slots = {null, "B", null, "C"}; // slots 9..12
		String hand = "A";
		int cursor = 8;
		StringBuilder seen = new StringBuilder();
		for (int press = 0; press < 4; press++) {
			boolean[] in = new boolean[slots.length];
			for (int i = 0; i < slots.length; i++) in[i] = slots[i] != null;
			int slot = ItemGroups.nextSlot(in, 9, cursor, 1);
			String tmp = slots[slot - 9];
			slots[slot - 9] = hand;
			hand = tmp;
			cursor = slot;
			seen.append(hand);
		}
		assertEquals("BCAB", seen.toString());
	}
}
