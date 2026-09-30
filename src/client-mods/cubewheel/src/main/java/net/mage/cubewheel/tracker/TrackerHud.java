package net.mage.cubewheel.tracker;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
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
	private static final int TAG_GAP = 3;
	private static final int EFFECTS_HEIGHT = 52; // two rows of vanilla mob-effect icons
	private static final int BACKDROP = 0x80000000;
	/** Behind a divider's label, so the hairline doesn't run through the text. */
	private static final int BACKDROP_SOLID = 0xFF1A1A1A;
	private static final int DIVIDER = 0x60FFFFFF;
	private static final int DIVIDER_TEXT = 0xFF8C8C8C;
	private static final String DETAIL_INDENT = "  ↳ ";
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
		List<TrackerStore.HudSection> sections = store.hudSections(cfg.tracker.hudMaxLines, cfg.tracker.local.enabled,
				net.mage.cubewheel.tracker.local.mc.LocalSignals.currentWorld(), cfg.tracker.local.worlds,
				net.mage.cubewheel.tracker.local.WorldScope.Mode.parse(cfg.tracker.worldFilter));
		if (sections.isEmpty()) return;
		// A labelled divider before each group, but only when there is more than one group to tell apart.
		boolean dividers = sections.size() > 1;

		Font font = mc.font;
		long now = System.currentTimeMillis();
		List<TrackerRow> entries = new ArrayList<>();
		List<String> lines = new ArrayList<>();
		List<Boolean> header = new ArrayList<>();
		for (TrackerStore.HudSection s : sections) {
			if (dividers) {
				entries.add(null);
				lines.add(sectionLabel(s.kind()));
				header.add(true);
			}
			for (TrackerRow r : s.rows()) {
				// Say what to do: "Jungle Pursuit · Mine 15,000 Tangleroots Resources", or one detail line per objective.
				EntryLabel label = EntryLabel.of(r.item().name(), store.objective(r.item().id()).orElse(null));
				entries.add(r);
				lines.add(TrackerFormat.line(r, label.title(), now));
				header.add(false);
				for (String detail : label.details()) {
					entries.add(null);
					lines.add(DETAIL_INDENT + detail);
					header.add(false);
				}
			}
		}
		// Every entry starts with its source's marker (⚒ jobs, ✦ prestige, ⚑ party quests …) in a fixed-width column.
		int tagW = 0;
		for (TrackerRow r : entries) {
			if (r != null) tagW = Math.max(tagW, font.width(SourceTag.of(r.item().source()).glyph()));
		}
		int textX = tagW + TAG_GAP;
		int w = font.width("Tracker");
		for (int i = 0; i < lines.size(); i++) {
			w = Math.max(w, (header.get(i) ? 0 : textX) + font.width(lines.get(i)));
		}
		int lh = font.lineHeight + 1;
		int x = g.guiWidth() - MARGIN - w;
		int y = MARGIN + (mc.player.getActiveEffects().isEmpty() ? 0 : EFFECTS_HEIGHT);
		g.fill(x - 2, y - 2, x + w + 2, y + lh * (lines.size() + 1), BACKDROP);
		lastBottom = y + lh * (lines.size() + 1);
		g.text(font, "Tracker", x, y, GOLD);
		for (int i = 0; i < lines.size(); i++) {
			int ly = y + lh * (i + 1);
			if (header.get(i)) {
				// Divider: a hairline across the panel with the group's name on it.
				g.fill(x, ly + lh / 2, x + w, ly + lh / 2 + 1, DIVIDER);
				int lw = font.width(lines.get(i));
				g.fill(x + 6, ly, x + 10 + lw, ly + lh - 1, BACKDROP_SOLID);
				g.text(font, lines.get(i), x + 8, ly, DIVIDER_TEXT);
				continue;
			}
			TrackerRow row = entries.get(i);
			if (row == null) { // an objective detail line under its quest
				g.text(font, lines.get(i), x + textX, ly, DIVIDER_TEXT);
				continue;
			}
			SourceTag tag = SourceTag.of(row.item().source());
			g.text(font, tag.glyph(), x, ly, tag.argb());
			g.text(font, lines.get(i), x + textX, ly, color(row, cfg.tracker.nearThreshold));
		}
	}

	private static String sectionLabel(TrackerStore.HudSection.Kind kind) {
		return switch (kind) {
			case PINNED -> "Pinned";
			case THIS_WORLD -> "This world";
			case ANYWHERE -> "Anywhere";
			case OTHER_WORLDS -> "Other worlds";
		};
	}

	/** Green only when a menu read says complete; an estimate at the target ("✓?") stays yellow. */
	private static int color(TrackerRow r, double near) {
		if (r.complete()) return GREEN;
		return r.atCap() || r.fraction() >= near ? YELLOW : WHITE;
	}
}
