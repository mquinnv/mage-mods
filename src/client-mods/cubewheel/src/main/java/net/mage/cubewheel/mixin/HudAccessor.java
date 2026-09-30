package net.mage.cubewheel.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the action-bar text; the SetActionBarText packet writes it here without passing ChatListener. */
@Mixin(Hud.class)
public interface HudAccessor {
	@Accessor("overlayMessageString")
	Component cubewheel$getOverlayMessage();
}
