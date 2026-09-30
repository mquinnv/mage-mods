package com.mage.cubewheel.mixin;

import com.mage.cubewheel.tracker.local.mc.LocalSignals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Local counting: when the server settles a predicted block change, a counted break whose position gets
 * its pre-break state back was rejected and is taken back. Read-only. Optional: if it does not apply,
 * rejected breaks stay counted until the next menu read.
 */
@Mixin(ClientLevel.class)
public abstract class LocalCountingLevelMixin {
	@Inject(method = "syncBlockState", at = @At("HEAD"))
	private void cubewheel$sync(BlockPos pos, BlockState state, Vec3 playerPos, CallbackInfo ci) {
		try {
			LocalSignals.onSyncBlockState(pos, state);
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			try {
				LocalSignals.fail(LocalSignals.Hook.SYNC, t);
			} catch (Throwable ignored) {
				// LocalSignals itself is unusable: stay silent rather than break packet handling
			}
		}
	}
}
