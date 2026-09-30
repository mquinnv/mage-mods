package com.mage.cubewheel.mixin;

import com.mage.cubewheel.tracker.RefreshController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips drawing the server menus a tracker refresh opens and closes by itself, so the player keeps
 * seeing the world instead of menus flashing by. Optional: if it does not apply, the menus just show.
 */
@Mixin(Screen.class)
public abstract class ScreenHideMixin {
	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), cancellable = true)
	private void cubewheel$hideRefreshMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		try {
			if (RefreshController.shouldHide((Screen) (Object) this)) ci.cancel();
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			RefreshController.disableHiding(t);
		}
	}
}
