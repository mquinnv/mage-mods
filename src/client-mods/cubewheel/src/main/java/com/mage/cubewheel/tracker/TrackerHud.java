package com.mage.cubewheel.tracker;

import com.mage.cubewheel.CubeWheelClient;
import com.mage.cubewheel.ServerGate;
import com.mage.cubewheel.config.CubeWheelConfig;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Top-right overlay: pinned trackables, then every other incomplete one closest to done first (hidden
 * ones never), capped at {@code tracker.hudMaxLines}. Entries at or above {@code nearThreshold} are yellow.
 */
public final class TrackerHud implements HudElement {
	private static final int MARGIN = 4;
	private static final int EFFECTS_HEIGHT = 52; // two rows of vanilla mob-effect icons
	private static final int BACKDROP = 0x80000000;
	private static final int GOLD = 0xFFFFAA00;
	private static final int GREEN = 0xFF55FF55;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int WHITE = 0xFFFFFFFF;

	public static final Identifier ID = Identifier.fromNamespaceAndPath(CubeWheelClient.MOD_ID, "tracker");

	private boolean failureLogged;
	/** Bottom edge (GUI y) of what this element and the effect icons took this frame; 0 = nothing. */
	private static int lastBottom;

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MOB_EFFECTS, ID, new TrackerHud());
	}

	/** How far down the top-right corner is taken by effect icons and the tracker HUD (last frame drawn). */
	public static int lastBottom() {
		return lastBottom;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		lastBottom = 0;
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null && !mc.player.getActiveEffects().isEmpty()) lastBottom = MARGIN + EFFECTS_HEIGHT;
			draw(g);
		} catch (RuntimeException e) {
			if (!failureLogged) CubeWheelClient.LOG.error("[cubewheel] tracker HUD failed", e);
			failureLogged = true;
		}
	}

	private void draw(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		TrackerStore store = CubeWheelClient.tracker();
		if (mc.player == null || store == null) return;
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		if (!cfg.tracker.hudVisible || !ServerGate.active(cfg)) return;
		List<TrackerRow> entries = store.hudRows(cfg.tracker.hudMaxLines, cfg.tracker.local.enabled);
		if (entries.isEmpty()) return;

		Font font = mc.font;
		long now = System.currentTimeMillis();
		List<String> lines = new ArrayList<>(entries.size());
		int w = font.width("Tracker");
		for (TrackerRow r : entries) {
			String line = TrackerFormat.line(r, now);
			lines.add(line);
			w = Math.max(w, font.width(line));
		}
		int lh = font.lineHeight + 1;
		int x = g.guiWidth() - MARGIN - w;
		int y = MARGIN + (mc.player.getActiveEffects().isEmpty() ? 0 : EFFECTS_HEIGHT);
		g.fill(x - 2, y - 2, x + w + 2, y + lh * (lines.size() + 1), BACKDROP);
		lastBottom = y + lh * (lines.size() + 1);
		g.text(font, "Tracker", x, y, GOLD);
		for (int i = 0; i < lines.size(); i++) {
			g.text(font, lines.get(i), x, y + lh * (i + 1), color(entries.get(i), cfg.tracker.nearThreshold));
		}
	}

	/** Green only when a menu read says complete; an estimate at the target ("✓?") stays yellow. */
	private static int color(TrackerRow r, double near) {
		if (r.complete()) return GREEN;
		return r.atCap() || r.fraction() >= near ? YELLOW : WHITE;
	}
}
