package net.mage.cubewheel.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only: a text display's text (a custom-model mob's name tag). Optional; NearbyProbe falls back to the render state. */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
	@Invoker("getText")
	Component cubewheel$getText();
}
