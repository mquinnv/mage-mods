package net.mage.cubewheel.mixin;

import net.mage.cubewheel.tracker.local.mc.ModelCaptureProbe;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Capture mode only: reads every sound as it is played (never changes it), with its subtitle key and nearby
 * entities, to learn which entity a mana mob's sounds come from. Optional: if it does not apply, no sounds are captured.
 */
@Mixin(SoundManager.class)
public abstract class SoundCaptureMixin {
	@Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;", at = @At("HEAD"))
	private void cubewheel$sound(SoundInstance sound, CallbackInfoReturnable<?> cir) {
		ModelCaptureProbe.onSound((SoundManager) (Object) this, sound);
	}
}
