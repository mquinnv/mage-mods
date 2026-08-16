package com.mage.hammerharvest;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-side harvest logic for Nova "Vanilla Hammers" items.
 *
 * <p>The server (Paper + Nova) renders every custom item as a plain
 * {@code minecraft:shulker_shell} and attaches a {@code minecraft:tool} component that carries
 * <em>no</em> mining rules at all. As a result the client believes a diamond hammer cannot
 * correctly harvest anything, which in turn makes mods such as AutoSwitch refuse to select it.
 *
 * <p>The only thing that identifies a Nova item client-side is its {@code minecraft:item_model}
 * component, which Nova sets to {@code vanilla_hammers:<material>_hammer}.
 */
public final class HammerHarvest {

	private static final Logger LOGGER = LoggerFactory.getLogger("hammerharvest");

	/** Model ids already reported by {@link #warnUnrecognised}, so the warning fires once each. */
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	/** Namespace Nova's Vanilla Hammers addon uses for its item models. */
	private static final String HAMMER_NAMESPACE = "vanilla_hammers";

	/** Suffix every hammer's item-model path carries, e.g. {@code diamond_hammer}. */
	private static final String HAMMER_SUFFIX = "_hammer";

	/**
	 * Mining levels keyed by the material prefix of the item-model path. Values mirror vanilla tool
	 * tiers plus the tiers the addon defines in its {@code tool_levels.yml}.
	 */
	private static final Map<String, Integer> HAMMER_LEVELS = Map.ofEntries(
			Map.entry("wooden", 0),
			Map.entry("golden", 0),
			Map.entry("stone", 1),
			Map.entry("lapis", 1),
			Map.entry("iron", 2),
			Map.entry("slime", 2),
			Map.entry("quartz", 2),
			Map.entry("obsidian", 2),
			Map.entry("diamond", 3),
			Map.entry("fiery", 3),
			Map.entry("prismarine", 3),
			Map.entry("emerald", 3),
			Map.entry("ender", 3),
			Map.entry("netherite", 4));

	private HammerHarvest() {
	}

	/**
	 * Decides whether a stack should be force-reported as the correct tool for a block.
	 *
	 * <p>This only ever answers {@code true} for Nova hammers on pickaxe-mineable blocks whose tier
	 * requirement the hammer actually meets. Every other case answers {@code false}, which callers
	 * must treat as "no opinion" and fall through to vanilla behaviour — this mixin is strictly
	 * additive and never downgrades a stack vanilla already considers correct.
	 */
	public static boolean forcesCorrectToolForDrops(ItemStack stack, BlockState state) {
		int level = hammerLevel(stack);
		if (level < 0) {
			return false;
		}

		// Every hammer in the addon is tool_category: minecraft:pickaxe.
		if (!state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return false;
		}

		if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
			return level >= 3;
		}
		if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
			return level >= 2;
		}
		if (state.is(BlockTags.NEEDS_STONE_TOOL)) {
			return level >= 1;
		}
		return true;
	}

	/**
	 * Returns the mining level of a Nova hammer, or {@code -1} if the stack is not a hammer this mod
	 * recognises (including every ordinary vanilla item).
	 */
	private static int hammerLevel(ItemStack stack) {
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		if (model == null || !HAMMER_NAMESPACE.equals(model.getNamespace())) {
			return -1;
		}

		// Nova is only observed to emit a bare "<material>_hammer" path, but tolerate a nested one
		// ("item/diamond_hammer") so a prefix change upstream degrades loudly below rather than
		// silently disabling the whole mod.
		String path = model.getPath();
		int lastSlash = path.lastIndexOf('/');
		String name = lastSlash < 0 ? path : path.substring(lastSlash + 1);

		int level = name.endsWith(HAMMER_SUFFIX)
				? HAMMER_LEVELS.getOrDefault(name.substring(0, name.length() - HAMMER_SUFFIX.length()), -1)
				: -1;

		if (level < 0) {
			warnUnrecognised(model);
		}
		return level;
	}

	/**
	 * Reports a {@code vanilla_hammers} item whose model path this mod could not map to a tier, once
	 * per distinct model.
	 *
	 * <p>Detection hinges on Nova's item-model naming, which is not a documented contract. Without
	 * this, an upstream rename would present as "auto-switch silently still doesn't work" with
	 * nothing to go on — the log line names the model we actually saw.
	 */
	private static void warnUnrecognised(Identifier model) {
		if (WARNED.add(model.toString())) {
			LOGGER.warn("[hammerharvest] unrecognised Vanilla Hammers item model '{}' - "
					+ "cannot determine its mining tier, so it will not auto-switch. "
					+ "The tier table in HammerHarvest.java likely needs updating.", model);
		}
	}
}
