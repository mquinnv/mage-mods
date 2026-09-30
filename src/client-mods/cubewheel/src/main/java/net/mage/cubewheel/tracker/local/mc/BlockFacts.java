package net.mage.cubewheel.tracker.local.mc;

import net.mage.cubewheel.tracker.local.Signal;
import net.mage.cubewheel.tracker.local.WorldInfo;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/** Minecraft adapter: a broken block's id, name, groups and crop maturity. */
final class BlockFacts {
	private BlockFacts() {}

	static Signal.BlockBroken of(BlockState state, BlockGetter level, BlockPos pos, WorldInfo world) {
		Block block = state.getBlock();
		String id = state.typeHolder().getRegisteredName();
		String path = id.substring(id.indexOf(':') + 1);
		boolean melonLike = path.equals("melon") || path.equals("pumpkin");
		boolean stem = block instanceof StemBlock || block instanceof AttachedStemBlock; // stems are not the harvest
		boolean crop = !stem && (melonLike || block instanceof CropBlock || block instanceof NetherWartBlock
				|| block instanceof CocoaBlock || block instanceof SweetBerryBushBlock || state.is(BlockTags.CROPS));
		boolean mature = crop && (melonLike || (block instanceof CropBlock c ? c.isMaxAge(state) : ageAtMax(state)));
		Set<String> groups = new HashSet<>();
		if (crop) groups.add("crop");
		if (path.endsWith("_ore") || path.equals("ancient_debris")) groups.add("ore");
		if (state.is(BlockTags.LOGS)) groups.add("logs");
		boolean trivial = state.canBeReplaced() || state.getDestroySpeed(level, pos) == 0.0f;
		return new Signal.BlockBroken(id, block.getName().getString(), groups, crop, mature, trivial, world);
	}

	/** Blocks that break by themselves when the block under (or over) them goes: not counted as area breaks. */
	private static final Set<String> CASCADES = Set.of("sugar_cane", "cactus", "bamboo", "kelp", "kelp_plant",
			"twisting_vines", "twisting_vines_plant", "weeping_vines", "weeping_vines_plant", "cave_vines",
			"cave_vines_plant", "chorus_plant", "chorus_flower", "pointed_dripstone", "scaffolding");

	/** Replaceable, instant-break or a cascading plant (sugar cane, cactus, vines): pops off without its support. */
	static boolean popsOff(BlockState state, BlockGetter level, BlockPos pos) {
		String id = state.typeHolder().getRegisteredName();
		return CASCADES.contains(id.substring(id.indexOf(':') + 1)) || state.canBeReplaced()
				|| state.getDestroySpeed(level, pos) == 0.0f;
	}

	/** True if the state's "age" property is at its highest value (or it has none). */
	private static boolean ageAtMax(BlockState state) {
		for (Property<?> p : state.getProperties()) {
			if (p instanceof IntegerProperty age && p.getName().equals("age")) {
				List<Integer> values = age.getPossibleValues();
				int max = values.stream().mapToInt(Integer::intValue).max().orElse(0);
				return state.getValue(age) == max;
			}
		}
		return true;
	}
}
