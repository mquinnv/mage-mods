package net.mage.cubewheel.tracker.local.mc;

import net.mage.cubewheel.mixin.TextDisplayAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;

/** Minecraft adapter: reading a text display's text, shared by the nearby probe and the model-mob registry. */
public final class TextDisplays {
	private static boolean textInvokerBroken;

	private TextDisplays() {}

	/** A text display's text: through the optional invoker, else its render state (may be null before rendering). */
	public static String text(Display.TextDisplay td) {
		Component c = null;
		if (!textInvokerBroken) {
			try {
				c = ((TextDisplayAccessor) td).cubewheel$getText();
			} catch (VirtualMachineError e) {
				throw e;
			} catch (Throwable t) {
				textInvokerBroken = true; // the accessor did not apply: use the render state from now on
			}
		}
		if (c == null) {
			Display.TextDisplay.TextRenderState state = td.textRenderState();
			if (state != null) c = state.text();
		}
		String s = c == null ? null : c.getString();
		return s == null || s.isBlank() ? null : s;
	}
}
