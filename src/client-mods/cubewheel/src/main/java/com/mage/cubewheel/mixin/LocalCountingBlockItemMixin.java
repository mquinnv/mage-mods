package com.mage.cubewheel.mixin;

import com.mage.cubewheel.tracker.local.mc.LocalSignals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Local counting: remembers blocks the player places client-side, so breaking them again is not counted
 * (job and quest plugins ignore placed blocks). Read-only. Optional: if it does not apply, placed blocks
 * count and the next menu read corrects the estimate.
 */
@Mixin(BlockItem.class)
public abstract class LocalCountingBlockItemMixin {
	@Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
			at = @At("RETURN"))
	private void cubewheel$placed(BlockPlaceContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
		try {
			if (ctx.getLevel() instanceof ClientLevel level && cir.getReturnValue() != null && cir.getReturnValue().consumesAction()) {
				LocalSignals.onPlaced(level, ctx.getClickedPos());
			}
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			try {
				LocalSignals.fail(LocalSignals.Hook.PLACE, t);
			} catch (Throwable ignored) {
				// LocalSignals itself is unusable: stay silent rather than break packet handling
			}
		}
	}
}
