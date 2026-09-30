package net.mage.cubewheel.mixin;

import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The server-set progress; getProgress() is the animated value and would change every frame while lerping. */
@Mixin(LerpingBossEvent.class)
public interface LerpingBossEventAccessor {
	@Accessor("targetPercent")
	float cubewheel$getTargetPercent();
}
