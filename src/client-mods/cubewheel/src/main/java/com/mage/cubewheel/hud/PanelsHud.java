package com.mage.cubewheel.hud;

import com.mage.cubewheel.CubeWheelClient;
import com.mage.cubewheel.tracker.TrackerHud;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Draws CubeWheel's small panels (events, boosters, cooldowns) in one HUD element so panels sharing a corner
 * can stack (see {@link HudLayout}). Attached right after the tracker HUD, whose drawn height it reserves in
 * the top-right corner so nothing overlaps it. Each source is asked every frame; it must be cheap and return
 * empty when it has nothing to show or is gated off. A failing source is logged once and skipped.
 */
public final class PanelsHud implements HudElement {
	private static final int BACKDROP = 0x80000000;
	private static final int GOLD = 0xFFFFAA00;
	private static final int PAD = 2;

	/** A panel source, given the current time in epoch ms. */
	public interface Source extends Function<Long, Optional<Panel>> {}

	private static final List<Source> SOURCES = new ArrayList<>();
	private static final List<Boolean> FAILED = new ArrayList<>();

	/** Adds a source; panels in one corner are stacked in registration order. */
	public static void add(Source s) {
		SOURCES.add(s);
		FAILED.add(false);
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(TrackerHud.ID,
				Identifier.fromNamespaceAndPath(CubeWheelClient.MOD_ID, "panels"), new PanelsHud());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || SOURCES.isEmpty()) return;
		long now = System.currentTimeMillis();
		HudLayout layout = new HudLayout(g.guiWidth(), g.guiHeight());
		layout.reserve(HudLayout.Corner.TOP_RIGHT, TrackerHud.lastBottom());
		for (int i = 0; i < SOURCES.size(); i++) {
			try {
				Optional<Panel> p = SOURCES.get(i).apply(now);
				if (p.isPresent() && !p.get().lines().isEmpty()) draw(g, mc.font, layout, p.get());
			} catch (RuntimeException e) {
				if (!FAILED.get(i)) CubeWheelClient.LOG.error("[cubewheel] HUD panel failed", e);
				FAILED.set(i, true);
			}
		}
	}

	private static void draw(GuiGraphicsExtractor g, Font font, HudLayout layout, Panel p) {
		int w = font.width(p.title());
		for (Panel.Line l : p.lines()) w = Math.max(w, font.width(l.text()));
		int lh = font.lineHeight + 1;
		int h = lh * (p.lines().size() + 1);
		HudLayout.Box box = layout.place(p.corner(), p.x(), p.y(), w + 2 * PAD, h + 2 * PAD);
		int x = box.x() + PAD, y = box.y() + PAD;
		g.fill(box.x(), box.y(), box.x() + box.w(), box.y() + box.h(), BACKDROP);
		g.text(font, p.title(), x, y, GOLD);
		for (int i = 0; i < p.lines().size(); i++) {
			Panel.Line l = p.lines().get(i);
			g.text(font, l.text(), x, y + lh * (i + 1), l.color());
		}
	}
}
