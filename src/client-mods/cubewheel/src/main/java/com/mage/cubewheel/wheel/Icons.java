package com.mage.cubewheel.wheel;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Item icons for wheel nodes, looked up by item id ("minecraft:ender_chest"). */
public final class Icons {
	private static final Map<String, ItemStack> CACHE = new HashMap<>();

	private Icons() {}

	/** Returns an icon stack for the item id; unknown, invalid or null ids give ItemStack.EMPTY. */
	public static ItemStack stack(String itemId) {
		if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
		return CACHE.computeIfAbsent(itemId, Icons::lookup);
	}

	private static ItemStack lookup(String itemId) {
		Identifier id = Identifier.tryParse(itemId.trim());
		if (id == null) return ItemStack.EMPTY;
		Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}
}
