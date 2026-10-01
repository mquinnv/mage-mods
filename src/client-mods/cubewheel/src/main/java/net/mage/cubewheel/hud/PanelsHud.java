package net.mage.cubewheel.hud;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.tracker.TrackerHud;
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
import net.minecraft.world.item.ItemStack;

/**
 * Draws CubeWheel's small panels (events, boosters, cooldowns, jobs, tracker) in one HUD element so panels sharing
 * a corner can stack (see {@link HudLayout}). Attached right after {@link TrackerHud}, which reports the vanilla
 * effect icons' height; that is reserved in the top-right corner so nothing overlaps them. Each source is asked every frame; it must be cheap and return
 * empty when it has nothing to show or is gated off. A failing source is logged once and skipped.
 */
public final class PanelsHud implements HudElement {
	private static final int BACKDROP = 0x80000000;
	private static final int GOLD = 0xFFFFAA00;
	private static final int PAD = 2;
	/** Space between a panel's columns. */
	private static final int COLUMN_GAP = 4;
	/** An item icon before a line's text: drawn at half size (8 px) plus a gap. */
	private static final int ICON_W = 10;
	/** Progress meter under a row: empty track, filling, full. */
	private static final int METER_TRACK = 0x40FFFFFF;
	private static final int METER_FILL = 0xFF4FA3FF;
	private static final int METER_DONE = 0xFF55FF55;

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
		List<Panel> panels = new ArrayList<>();
		for (int i = 0; i < SOURCES.size(); i++) {
			try {
				Optional<Panel> p = SOURCES.get(i).apply(now);
				if (p.isPresent() && !p.get().lines().isEmpty()) panels.add(p.get());
			} catch (RuntimeException e) {
				if (!FAILED.get(i)) CubeWheelClient.LOG.error("[cubewheel] HUD panel failed", e);
				FAILED.set(i, true);
			}
		}
		// Panels stacked in one corner share the widest one's width, so they line up as one column.
		java.util.Map<HudLayout.Corner, Integer> cornerWidth = new java.util.EnumMap<>(HudLayout.Corner.class);
		for (Panel p : panels) cornerWidth.merge(p.corner(), width(mc.font, p), Math::max);
		for (Panel p : panels) {
			try {
				draw(g, mc.font, layout, p, cornerWidth.get(p.corner()));
			} catch (RuntimeException e) {
				CubeWheelClient.LOG.error("[cubewheel] HUD panel draw failed", e);
			}
		}
	}

	/** A line's text width, its icon included. */
	private static int textWidth(Font font, Panel.Line l) {
		return font.width(l.text()) + (l.icon() instanceof ItemStack s && !s.isEmpty() ? ICON_W : 0);
	}

	/** Content width of {@code p}'s columns (see {@link #draw}). */
	private static int width(Font font, Panel p) {
		int tagW = 0, textW = 0, rightW = 0;
		for (Panel.Line l : p.lines()) {
			if (!l.tag().isEmpty()) tagW = Math.max(tagW, font.width(l.tag()) + COLUMN_GAP);
			if (!l.right().isEmpty()) rightW = Math.max(rightW, font.width(l.right()) + COLUMN_GAP);
		}
		for (Panel.Line l : p.lines()) {
			boolean heading = l.tag().isEmpty() && l.right().isEmpty();
			textW = Math.max(textW, textWidth(font, l) - (heading ? tagW + rightW : 0));
		}
		return Math.max(font.width(p.title()), tagW + textW + rightW);
	}

	private static void draw(GuiGraphicsExtractor g, Font font, HudLayout layout, Panel p, int width) {
		// Columns: tag (aligned), text, right-aligned part.
		int tagW = 0, textW = 0, rightW = 0;
		for (Panel.Line l : p.lines()) {
			if (!l.tag().isEmpty()) tagW = Math.max(tagW, font.width(l.tag()) + COLUMN_GAP);
			if (!l.right().isEmpty()) rightW = Math.max(rightW, font.width(l.right()) + COLUMN_GAP);
		}
		for (Panel.Line l : p.lines()) {
			// Headings (no tag, no right part) may run across the whole width.
			boolean heading = l.tag().isEmpty() && l.right().isEmpty();
			textW = Math.max(textW, textWidth(font, l) - (heading ? tagW + rightW : 0));
		}
		int w = Math.max(width, Math.max(font.width(p.title()), tagW + textW + rightW));
		int lh = font.lineHeight + 1;
		int h = lh * (p.lines().size() + 1);
		HudLayout.Box box = layout.place(p.corner(), p.x(), p.y(), w + 2 * PAD, h + 2 * PAD);
		int x = box.x() + PAD, y = box.y() + PAD;
		g.fill(box.x(), box.y(), box.x() + box.w(), box.y() + box.h(), BACKDROP);
		g.text(font, p.title(), x, y, GOLD);
		for (int i = 0; i < p.lines().size(); i++) {
			Panel.Line l = p.lines().get(i);
			int ly = y + lh * (i + 1);
			boolean heading = l.tag().isEmpty() && l.right().isEmpty();
			// An accent bar in the left padding (e.g. an entry you made progress on just now).
			if (l.accent() != 0) g.fill(box.x(), ly - 1, box.x() + PAD, ly + lh - 1, l.accent());
			if (!l.tag().isEmpty()) g.text(font, l.tag(), x, ly, l.tagColor());
			int tx = heading ? x : x + tagW;
			if (l.progress() >= 0) {
				// A 1 px meter in the gap under the row, across the text and count columns.
				int my = ly + font.lineHeight;
				int end = x + w;
				int fill = tx + (int) Math.round((end - tx) * Math.min(1, l.progress()));
				g.fill(tx, my, end, my + 1, METER_TRACK);
				if (fill > tx) g.fill(tx, my, fill, my + 1, l.progress() >= 1 ? METER_DONE : METER_FILL);
			}
			if (l.icon() instanceof ItemStack stack && !stack.isEmpty()) {
				g.pose().pushMatrix();
				g.pose().translate(tx, ly - 0.5f);
				g.pose().scale(0.5f, 0.5f);
				g.item(stack, 0, 0);
				g.pose().popMatrix();
				tx += ICON_W;
			}
			g.text(font, l.text(), tx, ly, l.color());
			if (!l.right().isEmpty()) g.text(font, l.right(), x + w - font.width(l.right()), ly, l.rightColor());
		}
	}
}
