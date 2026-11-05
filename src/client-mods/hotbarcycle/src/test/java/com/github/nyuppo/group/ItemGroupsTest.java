package com.github.nyuppo.group;

import com.github.nyuppo.config.HotbarCycleConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The default rules against real ManaCube items: names, base items and lore from the SVAS catalog (2026-11-04). The
 * first tests are Michael's own items; the rest are other players' items the rules were tuned on.
 */
class ItemGroupsTest {
	private static final ItemGroups DEFAULTS = ItemGroups.parse(HotbarCycleConfig.DEFAULT_ITEM_GROUPS);

	private static final String SWORD = "minecraft:netherite_sword", AXE = "minecraft:netherite_axe",
			HOE = "minecraft:netherite_hoe", PICK = "minecraft:netherite_pickaxe";

	private static ItemGroups.Item item(String id, String name) {
		return new ItemGroups.Item(id, name, false, false);
	}

	/** A ManaCube item; the tool/weapon components follow the base item as in vanilla. */
	private static ItemGroups.Item gear(String id, String name, String... lore) {
		boolean tool = id.matches(".*_(pickaxe|axe|shovel|hoe|sword)$") || id.equals("minecraft:shears");
		boolean weapon = id.matches(".*_(sword|axe|spear)$") || id.equals("minecraft:mace") || id.equals("minecraft:trident");
		return new ItemGroups.Item(id, name, List.of(lore), tool, weapon);
	}

	private static String group(ItemGroups.Item item) {
		return DEFAULTS.label(DEFAULTS.groupOf(item));
	}

	@Test void michaelsMeleeWeapons() {
		assertEquals("melee", group(gear(SWORD, "GOD KILLER", "Unbreakable", "God Killer", "➟ Take Damage to Prime", "➟ Next hit Deals 3x Damage")));
		assertEquals("melee", group(gear(SWORD, "SAMURAI KATANA", "➟ Every 5th hit deals 2x Damage")));
		assertEquals("melee", group(gear(SWORD, "VAMPIRE SWORD", "➟ +20% Sword McMMO XP")));
		assertEquals("melee", group(gear(AXE, "TROLL GLAIVE", "Unbreakable", "Troll Glaive", "ITEM EFFECTS: (When Held)",
				"➟ 15% Extra damage to Bosses", "➟ 10% Chance to Deal 1.33x Damage")));
		assertEquals("melee", group(gear("minecraft:paper", "ZOMBIE HAND", "Unbreakable", "Zombie Hand", "Unarmed Weapon")));
		assertEquals("melee", group(item("minecraft:trident", "Trident"))); // "primarily melee"
	}

	@Test void michaelsRangedWeapons() {
		assertEquals("ranged", group(gear("minecraft:bow", "SPIRIT BOW", "➟ Shoots 9 Arrows at once")));
		assertEquals("ranged", group(gear("minecraft:bow", "EVENTIDE BOW", "➟ Arrows Pierce Multiple Targets")));
		assertEquals("ranged", group(gear("minecraft:crossbow", "SOUL CROSSBOW", "➟ On impact shards scatter in all directions")));
		assertEquals("ranged", group(gear(HOE, "PHOENIX STAFF", "Unbreakable", "Phoenix Staff", "➟ Shoot a Flame (Left-Click)", "PVE Damage: 20")));
		assertEquals("ranged", group(gear(HOE, "STORMCALLER STAFF", "➟ Inflict Levitation that", "damages your Opponent", "PVE Damage: 10dps")));
		assertEquals("ranged", group(gear(HOE, "CYBORG'S SONIC CANNON", "➟ High Damage Attack (Right-Click)", "PVE Damage: 65")));
		assertEquals("ranged", group(gear(HOE, "☠ CURSED BOOK ☠", "Cursed Book", "➟ Summon Magic Circle", "Damage: 14dps")));
		assertEquals("ranged", group(gear("minecraft:book", "▲ WIZARDS BOOK ▼", "Wizards Book", "➟ Summon Magic Circle", "Damage: 10dps")));
		assertEquals("ranged", group(gear("minecraft:blaze_rod", "MAGICAL BOOK", "Hellhound Book", "Shoots mysterious magic", "that damages all mobs!")));
		assertEquals("ranged", group(gear("minecraft:blaze_rod", "ENHANCED MAGICAL BOOK", "Shoots mysterious magic", "that damages all mobs!")));
	}

	// "you have to separate weapon axes from farming axes i guess. scythes too"
	@Test void michaelsTools() {
		assertEquals("tools", group(gear(AXE, "IRIDIUM SCYTHE", "Unbreakable", "Iridium Scythe", "➟ Plants a 3x3 patch of crops",
				"from your inventory (Right-Click)", "➟ Vein Mine Herbalism Blocks (Break Block)")));
		assertEquals("tools", group(gear(PICK, "ELDEN PICKAXE", "➟ Mining Fatigue II", "➟ 2x Ore Drops")));
		assertEquals("tools", group(gear(PICK, "CHRONOBREAKER", "➟ Bonus Mining McMMO XP")));
		assertEquals("tools", group(gear("minecraft:fishing_rod", "BLOOM FISHINGROD", "➟ 2.5x more Legendary Fish")));
		assertEquals("tools", group(gear("minecraft:diamond_axe", "Diamond Axe"))); // vanilla, no lore
		assertEquals("tools", group(gear("minecraft:diamond_hoe", "Diamond Hoe")));
	}

	// "hermes caduceus pulls mobs towards you. photo barrier places an energy shield ... kinda weirdos"
	@Test void michaelsUtilityItems() {
		assertEquals("utility", group(gear(HOE, "HERMES CADUCEUS", "Unbreakable", "Hermes Caduceus", "ITEM EFFECTS: (Either Hand)",
				"➟ Pull all monsters towards you (Right-Click)", "Radius: 20")));
		assertEquals("utility", group(gear(HOE, "PHOTON BARRIER", "Photon Barrier", "ITEM EFFECTS: (Right Click)", "➟ Spawns a protective Barrier")));
	}

	@Test void michaelsConsumablesAndNothingElse() {
		assertEquals("consumables", group(gear("minecraft:golden_apple", "CANDY APPLE", "Infinite Uses", "Candy Apple", "ITEM EFFECTS: (Consume)")));
		assertEquals("consumables", group(gear("minecraft:honey_bottle", "BOTTLE OF NECTAR", "Unbreakable", "Bottle of Nectar", "ITEM EFFECTS: (Consume)")));
		assertEquals("consumables", group(item("minecraft:honey_bottle", "Champion Potion"))); // not in the catalog: by name
		// "those three are the important ones": the event candy stays out
		assertEquals("none", group(gear("minecraft:golden_apple", "RIP LOLLIPOP", "Sugar Rush IV", "Sticky Fingers VI",
				"A ghostly voice urges you to take a bite.")));
		assertEquals("none", group(gear("minecraft:paper", "ONE WISH WILLOW", "Unbreakable", "➟ Snap to gain a Random Effect")));
		assertEquals("none", group(gear("minecraft:carved_pumpkin", "CHUCKY MASK", "➟ +20% Extra Damage")));
	}

	// Other players' items (catalog), where the obvious rule gets it wrong.
	@Test void catalogAxesAreMeleeUnlessTheirLoreNamesToolWorkOrShooting() {
		assertEquals("melee", group(gear(AXE, "GIDEON", "ITEM EFFECTS: (Attack)", "➟ Inflict Gradual Bleed Damage", "Damage: 2❤")));
		assertEquals("melee", group(gear(AXE, "FROSTBITE AXE", "ITEM EFFECTS: (Attack)", "➟ Stuns Enemy")));
		assertEquals("melee", group(gear(AXE, "HARVEST RAKE", "Harvest Rake", "➟ 20% Chance of receiving 4x mob heads")));
		assertEquals("melee", group(gear(AXE, "STORM AXE", "ITEM EFFECTS: (Attack)", "➟ Create a Lightning Chain", "PVE Damage: 50")));
		assertEquals("tools", group(gear(AXE, "LASER AXE", "ITEM EFFECTS: (Break Block)", "➟ Vein Mine Logs")));
		assertEquals("tools", group(gear(AXE, "SPIRAL AXE", "➟ Haste III", "➟ Bonus Woodcutting XP")));
		assertEquals("tools", group(gear(AXE, "IRIDIUM AXE", "➟ Break a 5x5x10 Trench Tunnel")));
		assertEquals("ranged", group(gear(AXE, "BO STAFF", "ITEM EFFECTS: (Left Click)", "➟ Shoots Pure Kinetic Energy")));
	}

	@Test void catalogWandsHoesAndMounts() {
		assertEquals("tools", group(gear("minecraft:stick", "EASTER SELL WAND", "This wand sells all of the contents of")));
		assertEquals("tools", group(gear(HOE, "HARVEST HOE", "This hoe automatically harvests and", "replants simultaneously in a 5x5 area")));
		assertEquals("tools", group(gear("minecraft:netherite_shovel", "Pharaoh's Shovel", "➟ Pharoh's Curse", "PVE Damage: 10dps")));
		assertEquals("utility", group(gear("minecraft:stick", "MOREND WAND", "Teleports you to wherever you", "are looking using Ender Magic")));
		assertEquals("utility", group(gear(HOE, "TIDE STAFF", "➟ Create a Water Shield that absorbs")));
		assertEquals("utility", group(gear("minecraft:carrot_on_a_stick", "SPEEDER BIKE", "➟ Zoom around on your own Speeder")));
		assertEquals("ranged", group(gear("minecraft:stick", "☠ WITCHDOCTOR'S STAFF ☠", "Fires mystical skulls that", "damage mobs and players")));
		assertEquals("ranged", group(gear("minecraft:bow", "Arcane Bow", "➟ Chance to Deal 3x Damage"))); // a bow, despite melee-ish lore
	}

	@Test void catalogItemsThatAreNeverCycled() {
		assertEquals("none", group(gear("minecraft:netherite_chestplate", "GINGERBREAD CHESTPLATE", "➟ +10% Extra Damage")));
		assertEquals("none", group(gear("minecraft:shield", "AEGIS SHIELD", "➟ Deal +10% Extra Damage to Monsters")));
		assertEquals("none", group(gear("minecraft:paper", "GARNET RING", "ITEM EFFECTS: (Pendant Slot)", "➟ +20% extra damage to Mana Monsters")));
		assertEquals("none", group(gear("minecraft:honey_bottle", "Power Potion", "THIS ITEM IS A ONE TIME USE", "➟ Deal 10% Extra Damage")));
		assertEquals("none", group(gear(HOE, "KUNAI", "ITEM EFFECTS: (Off Hand)", "➟ Throw Knife", "PVP Damage: 10")));
		assertEquals("none", group(item("minecraft:cobblestone", "Cobblestone")));
		assertEquals("none", group(null));
		assertEquals("consumables", group(gear("minecraft:honey_bottle", "GRIMACE SHAKE", "Unbreakable", "ITEM EFFECTS: (Consume)")));
	}

	@Test void ruleSyntax() {
		ItemGroups g = ItemGroups.parse(List.of(
				"none: minecraft:dirt",
				"a: torch | name:([",
				"Minecraft:Bow",
				"a: lore:x|y & id:_shovel$",
				"b: @tool"));
		assertEquals(List.of("a", "group 2", "b"), List.of(g.label(0), g.label(1), g.label(2)));
		assertEquals(3, g.size());
		assertEquals(-1, g.groupOf(item("minecraft:dirt", "Dirt")));
		assertEquals(0, g.groupOf(item("minecraft:torch", "Torch")));
		assertEquals(1, g.groupOf(item("minecraft:bow", "Bow"))); // "Minecraft:Bow" is an id, not a label
		assertEquals(0, g.groupOf(gear("minecraft:iron_shovel", "Shovel", "yy"))); // a bare | is regex alternation; & is AND
		assertEquals(2, g.groupOf(gear("minecraft:iron_shovel", "Shovel", "zz"))); // lore misses: falls to the next rule
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
