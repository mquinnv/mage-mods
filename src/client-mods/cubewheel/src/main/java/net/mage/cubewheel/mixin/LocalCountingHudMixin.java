package net.mage.cubewheel.mixin;

import net.mage.cubewheel.tracker.local.mc.LocalSignals;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Local counting: reads each action-bar message as it is set (never changes it), so the loot line after
 * a custom-model mob's removal ("+2  Tiger Hide") can name the kill. One of three action-bar sources
 * (with the setActionBarText packet hook and the per-tick Hud poll), deduped by ActionBarFeed. Optional:
 * if it does not apply, the other two still see the loot lines.
 */
@Mixin(Hud.class)
public abstract class LocalCountingHudMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"))
	private void cubewheel$overlay(Component message, boolean animate, CallbackInfo ci) {
		try {
			LocalSignals.onOverlayMessage(message);
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			try {
				LocalSignals.fail(LocalSignals.Hook.LOOT, t);
			} catch (Throwable ignored) {
				// LocalSignals itself is unusable: stay silent rather than break the HUD
			}
		}
	}
}
