package com.mage.hammerharvest.mixin;

import com.mage.hammerharvest.HammerHarvest;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

	/**
	 * Reports Nova "Vanilla Hammers" as the correct tool for pickaxe-mineable blocks of a tier they
	 * can actually harvest.
	 *
	 * <p>Only ever overrides the result to {@code true}; a negative verdict falls through to vanilla
	 * so that nothing vanilla considers correct is ever downgraded.
	 */
	@Inject(
			method = "isCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z",
			at = @At("HEAD"),
			cancellable = true)
	private void hammerharvest$novaHammersMineAsPickaxes(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		ItemStack stack = (ItemStack) (Object) this;

		if (HammerHarvest.forcesCorrectToolForDrops(stack, state)) {
			cir.setReturnValue(true);
		}
	}
}
