package com.github.nyuppo.group;

import com.github.nyuppo.config.HotbarCycleConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemGroupsTest {
	private static final ItemGroups DEFAULTS = ItemGroups.parse(HotbarCycleConfig.DEFAULT_ITEM_GROUPS);
	private static final int MELEE = 0, RANGED = 1, TOOLS = 2, CONSUMABLES = 3;

	private static ItemGroups.Item item(String id, String name) {
		return new ItemGroups.Item(id, name, false, false);
	}

	/** A ManaCube item: a vanilla tool/weapon base, its display name and its lore (from the SVAS catalog, 2026-11-04). */
	private static ItemGroups.Item gear(String id, String name, boolean tool, boolean weapon, String... lore) {
		return new ItemGroups.Item(id, name, List.of(lore), tool, weapon);
	}

	private static final String SWORD = "minecraft:netherite_sword", AXE = "minecraft:netherite_axe", HOE = "minecraft:netherite_hoe";

	// "melee and ranged weapons separate"
	@Test void meleeWeapons() {
		assertEquals(MELEE, DEFAULTS.groupOf(gear(SWORD, "GOD KILLER", true, true, "➟ Take Damage to Prime", "➟ Next hit Deals 3x Damage")));
		assertEquals(MELEE, DEFAULTS.groupOf(gear(SWORD, "SAMURAI KATANA", true, true, "➟ Every 5th hit deals 2x Damage")));
		assertEquals(MELEE, DEFAULTS.groupOf(gear(SWORD, "VAMPIRE SWORD", true, true, "➟ +20% Sword McMMO XP")));
		assertEquals(MELEE, DEFAULTS.groupOf(gear(AXE, "TROLL GLAIVE", true, true, "➟ 15% Extra damage to Bosses",
				"➟ 10% Chance to Deal 1.33x Damage")));
		assertEquals(MELEE, DEFAULTS.groupOf(gear("minecraft:paper", "ZOMBIE HAND", false, false, "Unarmed Weapon")));
		assertEquals(MELEE, DEFAULTS.groupOf(item("minecraft:mace", "Mace")));
		assertEquals(MELEE, DEFAULTS.groupOf(item("minecraft:iron_spear", "Iron Spear")));
	}

	@Test void rangedAndMagicWeapons() {
		assertEquals(RANGED, DEFAULTS.groupOf(item("minecraft:bow", "SPIRIT BOW")));
		assertEquals(RANGED, DEFAULTS.groupOf(item("minecraft:crossbow", "SOUL CROSSBOW")));
		assertEquals(MELEE, DEFAULTS.groupOf(item("minecraft:trident", "Trident"))); // "primarily melee"
		assertEquals(RANGED, DEFAULTS.groupOf(gear(HOE, "PHOENIX STAFF", true, false, "➟ Shoot a Flame (Left-Click)", "PVE Damage: 20")));
		assertEquals(RANGED, DEFAULTS.groupOf(gear(HOE, "STORMCALLER STAFF", true, false, "PVE Damage: 10dps")));
		assertEquals(RANGED, DEFAULTS.groupOf(gear(HOE, "CYBORG'S SONIC CANNON", true, false, "➟ High Damage Attack (Right-Click)", "PVE Damage: 65")));
		assertEquals(RANGED, DEFAULTS.groupOf(gear(HOE, "☠ CURSED BOOK ☠", true, false, "➟ Summon Magic Circle", "Damage: 14dps")));
		assertEquals(RANGED, DEFAULTS.groupOf(gear(HOE, "HERMES CADUCEUS", true, false, "➟ Pull all monsters towards you (Right-Click)")));
		assertEquals(RANGED, DEFAULTS.groupOf(gear(HOE, "PHOTON BARRIER", true, false, "➟ Spawns a protective Barrier")));
		assertEquals(RANGED, DEFAULTS.groupOf(gear("minecraft:book", "▲ WIZARDS BOOK ▼", false, false, "Damage: 10dps")));
		assertEquals(RANGED, DEFAULTS.groupOf(gear("minecraft:blaze_rod", "MAGICAL BOOK", false, false, "that damages all mobs!")));
		assertEquals(RANGED, DEFAULTS.groupOf(item("minecraft:blaze_rod", "ENHANCED MAGICAL BOOK"))); // by name too
	}

	// "you have to separate weapon axes from farming axes i guess. scythes too"
	@Test void toolsIncludingFarmingAxesAndScythes() {
		assertEquals(TOOLS, DEFAULTS.groupOf(gear(AXE, "IRIDIUM SCYTHE", true, true, "➟ Plants a 3x3 patch of crops",
				"➟ Vein Mine Herbalism Blocks (Break Block)", "➟ Gain McMMO from each Block")));
		assertEquals(TOOLS, DEFAULTS.groupOf(gear("minecraft:netherite_pickaxe", "ELDEN PICKAXE", true, false, "➟ 2x Ore Drops")));
		assertEquals(TOOLS, DEFAULTS.groupOf(gear("minecraft:netherite_pickaxe", "CYBORG'S PICKAXE", true, false, "➟ Receive 3x Ores")));
		assertEquals(TOOLS, DEFAULTS.groupOf(gear("minecraft:diamond_axe", "Diamond Axe", true, true)));
		assertEquals(TOOLS, DEFAULTS.groupOf(gear("minecraft:diamond_hoe", "Diamond Hoe", true, false)));
		assertEquals(TOOLS, DEFAULTS.groupOf(gear("minecraft:fishing_rod", "BLOOM FISHINGROD", false, false, "➟ 2.5x more Legendary Fish")));
	}

	@Test void theConsumablesShareTheirOwnGroup() {
		assertEquals(CONSUMABLES, DEFAULTS.groupOf(item("minecraft:golden_apple", "CANDY APPLE")));
		assertEquals(CONSUMABLES, DEFAULTS.groupOf(item("minecraft:honey_bottle", "BOTTLE OF NECTAR")));
		assertEquals(CONSUMABLES, DEFAULTS.groupOf(item("minecraft:honey_bottle", "Champion Potion")));
	}

	// "other stuff shouldn't rotate through the main bar"
	@Test void everythingElseIsInNoGroup() {
		assertEquals(-1, DEFAULTS.groupOf(item("minecraft:cobblestone", "Cobblestone")));
		assertEquals(-1, DEFAULTS.groupOf(item("minecraft:golden_apple", "Golden Apple")));
		assertEquals(-1, DEFAULTS.groupOf(item("minecraft:paper", "ONE WISH WILLOW")));
		assertEquals(-1, DEFAULTS.groupOf(item("minecraft:golden_apple", "PUMPKIN CANDY APPLE"))); // anchored
		assertEquals(-1, DEFAULTS.groupOf(item("minecraft:book", "Book")));
		assertEquals(-1, DEFAULTS.groupOf(item("minecraft:blaze_rod", "Blaze Rod")));
		assertEquals(-1, DEFAULTS.groupOf(null));
	}

	@Test void matcherSyntax() {
		ItemGroups g = ItemGroups.parse(List.of("torch | name:([", "  ", "Minecraft:Bow", "lore:a|b | id:_shovel$"));
		assertEquals(3, g.size());
		assertEquals(0, g.groupOf(item("minecraft:torch", "Torch")));
		assertEquals(1, g.groupOf(item("minecraft:bow", "Bow")));
		assertEquals(2, g.groupOf(gear("minecraft:stick", "Stick", false, false, "xx b"))); // a bare | is regex alternation
		assertEquals(2, g.groupOf(item("minecraft:iron_shovel", "Shovel")));
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
