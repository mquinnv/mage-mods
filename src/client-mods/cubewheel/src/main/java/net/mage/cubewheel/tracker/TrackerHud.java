package net.mage.cubewheel.tracker;

import net.mage.cubewheel.CubeWheelClient;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Formerly the top-right tracker overlay; the tracker is now a panel ({@link TrackerPanel}) in the left-hand column.
 * This element draws nothing: it stays as the anchor {@link net.mage.cubewheel.hud.PanelsHud} attaches after, and
 * reports how far down the top-right corner the vanilla effect icons reach so a top-right panel goes below them.
 */
public final class TrackerHud implements HudElement {
	private static final int MARGIN = 4;
	private static final int EFFECTS_HEIGHT = 52; // two rows of vanilla mob-effect icons

	public static final Identifier ID = Identifier.fromNamespaceAndPath(CubeWheelClient.MOD_ID, "tracker");

	/** Bottom edge (GUI y) of the effect icons this frame; 0 = none. The tracker itself takes no height here. */
	private static int lastBottom;

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MOB_EFFECTS, ID, new TrackerHud());
	}

	/** How far down the top-right corner is taken by effect icons (last frame drawn). */
	public static int lastBottom() {
		return lastBottom;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		lastBottom = mc.player != null && !mc.player.getActiveEffects().isEmpty() ? MARGIN + EFFECTS_HEIGHT : 0;
	}
}
