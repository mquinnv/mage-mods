package net.mage.cubewheel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.mage.cubewheel.manamobs.SubtitleRenamer;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Names the mana mob a vanilla mob sound came from ("Polar Bear growls" becomes "Mana Wolf growls"). Rewrites the
 * subtitle component {@code onPlaySound} stores (its second {@code WeighedSoundEvents.getSubtitle()} call; the first is
 * only a null check), so duplicate folding and display use the new text. Optional: if it does not apply, subtitles
 * stay vanilla.
 */
@Mixin(SubtitleOverlay.class)
public abstract class SubtitleOverlayMixin {
	@ModifyExpressionValue(method = "onPlaySound", at = @At(value = "INVOKE", ordinal = 1,
			target = "Lnet/minecraft/client/sounds/WeighedSoundEvents;getSubtitle()Lnet/minecraft/network/chat/Component;"))
	private Component cubewheel$nameManaMob(Component subtitle, @Local(argsOnly = true) SoundInstance sound) {
		return SubtitleRenamer.apply(subtitle, sound);
	}
}
