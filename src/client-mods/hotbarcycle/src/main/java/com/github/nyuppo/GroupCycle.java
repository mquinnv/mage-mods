package com.github.nyuppo;

import com.github.nyuppo.compat.Clicker;
import com.github.nyuppo.group.ItemGroups;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The mage fork's Cycle Slot: the held item is swapped for the next item of its group from the three inventory rows
 * (slots 9-35); an item in no group is left alone. The other hotbar slots never move. See {@link ItemGroups}.
 */
public final class GroupCycle {
	/** First and last main-inventory slot (container slot ids in the player's inventory menu are the same). */
	static final int FIRST = 9, LAST = 35;

	private static List<String> parsedFrom;
	private static ItemGroups groups = ItemGroups.parse(List.of());

	// The walk: the last slot swapped, for which hotbar slot and group, so repeated presses go A, B, C, A, ...
	private static int cursor = FIRST - 1, cursorSelected = -1, cursorGroup = -1;

	private GroupCycle() {}

	public static void cycle(Minecraft client, Clicker clicker, int direction) {
		if (client.gameMode == null || client.player == null) return;
		Inventory inv = client.player.getInventory();
		ItemGroups g = groups();
		int selected = inv.getSelectedSlot();
		int group = g.groupOf(describe(inv.getItem(selected)));
		if (group < 0) return; // not a grouped item: Q does nothing

		if (selected != cursorSelected || group != cursorGroup) cursor = direction < 0 ? LAST + 1 : FIRST - 1;
		boolean[] inGroup = new boolean[LAST - FIRST + 1];
		for (int i = FIRST; i <= LAST; i++) inGroup[i - FIRST] = g.groupOf(describe(inv.getItem(i))) == group;
		int slot = ItemGroups.nextSlot(inGroup, FIRST, cursor, direction);
		if (slot < 0) return; // nothing else of this group in the inventory

		clicker.swap(client, slot, selected);
		cursor = slot;
		cursorSelected = selected;
		cursorGroup = group;
		if (HotbarCycleClient.getConfig().getPlaySound()) {
			client.player.level().playPlayerSound(SoundEvents.BOOK_PAGE_TURN, SoundSource.MASTER, 0.5f, 1.5f);
		}
	}

	/** The parsed groups, re-parsed when the config's lines change (Mod Menu edits apply without a restart). */
	private static ItemGroups groups() {
		List<String> lines = HotbarCycleClient.getConfig().getItemGroups();
		if (!lines.equals(parsedFrom)) {
			parsedFrom = List.copyOf(lines);
			groups = ItemGroups.parse(parsedFrom);
			for (String p : groups.problems()) HotbarCycleClient.LOGGER.warn("item groups: {}", p);
			cursorGroup = -1;
		}
		return groups;
	}

	static ItemGroups.Item describe(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		String name = ChatFormatting.stripFormatting(stack.getHoverName().getString());
		List<String> lore = new java.util.ArrayList<>();
		net.minecraft.world.item.component.ItemLore l = stack.get(DataComponents.LORE);
		if (l != null) for (net.minecraft.network.chat.Component c : l.lines()) lore.add(ChatFormatting.stripFormatting(c.getString()));
		return new ItemGroups.Item(id, name, lore, stack.has(DataComponents.TOOL), stack.has(DataComponents.WEAPON));
	}
}
