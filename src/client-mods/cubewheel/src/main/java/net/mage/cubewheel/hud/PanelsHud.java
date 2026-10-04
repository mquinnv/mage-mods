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
	/** Space between the pieces of a row of pieces. */
	private static final int PIECE_GAP = 6;
	/** Widest a panel's content may grow; longer names are cut to fit, with "…". */
	private static final int MAX_CONTENT_W = 170;
	/** The empty part of a row's progress bar. */
	private static final int METER_TRACK = 0x18FFFFFF;

	/** A panel source, given the current time in epoch ms. */
	public interface Source extends Function<Long, Optional<Panel>> {}

	private static final List<Source> SOURCES = new ArrayList<>();
	private static final List<Boolean> FAILED = new ArrayList<>();
	/** Per source: its position in the live config, and its default (for the arrange screen). */
	private static final List<Function<net.mage.cubewheel.config.CubeWheelConfig, net.mage.cubewheel.config.CubeWheelConfig.Position>> POSITIONS = new ArrayList<>();
	private static final List<java.util.function.Supplier<net.mage.cubewheel.config.CubeWheelConfig.Position>> DEFAULTS = new ArrayList<>();

	/** Where a source's panel was drawn last frame (for dragging it). */
	public record Placed(int source, String title, HudLayout.Box box) {}

	private static volatile List<Placed> lastPlaced = List.of();

	/**
	 * Adds a source; panels in one corner are stacked in registration order. {@code position} reads its position in
	 * the live config (the arrange screen moves it), {@code def} gives its default.
	 */
	public static void add(Source s, Function<net.mage.cubewheel.config.CubeWheelConfig, net.mage.cubewheel.config.CubeWheelConfig.Position> position,
			java.util.function.Supplier<net.mage.cubewheel.config.CubeWheelConfig.Position> def) {
		SOURCES.add(s);
		FAILED.add(false);
		POSITIONS.add(position);
		DEFAULTS.add(def);
	}

	public static List<Placed> lastPlaced() {
		return lastPlaced;
	}

	/** The live config position of source {@code i}. */
	public static net.mage.cubewheel.config.CubeWheelConfig.Position position(int i) {
		return POSITIONS.get(i).apply(CubeWheelClient.config().current());
	}

	/** A fresh copy of source {@code i}'s default position. */
	public static net.mage.cubewheel.config.CubeWheelConfig.Position defaultPosition(int i) {
		return DEFAULTS.get(i).get();
	}

	public static int sourceCount() {
		return SOURCES.size();
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
		List<Integer> sources = new ArrayList<>();
		for (int i = 0; i < SOURCES.size(); i++) {
			try {
				Optional<Panel> p = SOURCES.get(i).apply(now);
				if (p.isPresent() && !p.get().lines().isEmpty()) {
					panels.add(p.get());
					sources.add(i);
				}
			} catch (RuntimeException e) {
				if (!FAILED.get(i)) CubeWheelClient.LOG.error("[cubewheel] HUD panel failed", e);
				FAILED.set(i, true);
			}
		}
		// Panels stacked in one corner share the widest one's width, so they line up as one column.
		java.util.Map<HudLayout.Corner, Integer> cornerWidth = new java.util.EnumMap<>(HudLayout.Corner.class);
		for (Panel p : panels) if (p.corner() != HudLayout.Corner.CUSTOM) cornerWidth.merge(p.corner(), width(mc.font, p), Math::max);
		List<Placed> placed = new ArrayList<>();
		for (int k = 0; k < panels.size(); k++) {
			Panel p = panels.get(k);
			try {
				int w = p.corner() == HudLayout.Corner.CUSTOM ? width(mc.font, p) : cornerWidth.get(p.corner());
				placed.add(new Placed(sources.get(k), p.title(), draw(g, mc.font, layout, p, w)));
			} catch (RuntimeException e) {
				CubeWheelClient.LOG.error("[cubewheel] HUD panel draw failed", e);
			}
		}
		lastPlaced = List.copyOf(placed);
	}

	/** A line's text width, its icon included. */
	private static int textWidth(Font font, Panel.Line l) {
		if (!l.pieces().isEmpty()) {
			int w = -PIECE_GAP;
			for (Panel.Piece piece : l.pieces()) {
				w += PIECE_GAP + font.width(piece.text()) + (piece.icon() instanceof ItemStack s && !s.isEmpty() ? ICON_W : 0);
			}
			return w;
		}
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
		return Math.max(font.width(p.title()), Math.min(MAX_CONTENT_W, tagW + textW + rightW));
	}

	/** {@code s} cut with "…" to at most {@code max} pixels. */
	private static String fit(Font font, String s, int max) {
		if (font.width(s) <= max) return s;
		if (max <= font.width("…")) return "";
		return font.plainSubstrByWidth(s, max - font.width("…")).stripTrailing() + "…";
	}

	private static HudLayout.Box draw(GuiGraphicsExtractor g, Font font, HudLayout layout, Panel p, int width) {
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
		int w = Math.max(width, Math.max(font.width(p.title()), Math.min(MAX_CONTENT_W, tagW + textW + rightW)));
		int lh = font.lineHeight + 1;
		// A panel with an empty title has no title row (the compact Charms panel).
		int titleRows = p.title().isEmpty() ? 0 : 1;
		int h = lh * (p.lines().size() + titleRows);
		HudLayout.Box box = layout.place(p.corner(), p.x(), p.y(), w + 2 * PAD, h + 2 * PAD);
		int x = box.x() + PAD, y = box.y() + PAD;
		g.fill(box.x(), box.y(), box.x() + box.w(), box.y() + box.h(), BACKDROP);
		if (titleRows > 0) g.text(font, p.title(), x, y, GOLD);
		for (int i = 0; i < p.lines().size(); i++) {
			Panel.Line l = p.lines().get(i);
			int ly = y + lh * (i + titleRows);
			boolean heading = l.tag().isEmpty() && l.right().isEmpty();
			// An accent bar in the left padding (e.g. an entry you made progress on just now).
			if (l.accent() != 0) g.fill(box.x(), ly - 1, box.x() + PAD, ly + lh - 1, l.accent());
			if (!l.tag().isEmpty()) g.text(font, l.tag(), x, ly, l.tagColor());
			int tx = heading ? x : x + tagW;
			if (l.progress() >= 0) {
				// The row itself is the bar: filled behind the text and count, heating up as it goes (see Panel.meterColor).
				int left = tx - 1;
				int end = x + w + 1;
				int fill = left + (int) Math.round((end - left) * Math.min(1, l.progress()));
				g.fill(left, ly - 1, end, ly + lh - 1, METER_TRACK);
				if (fill > left) g.fill(left, ly - 1, fill, ly + lh - 1, Panel.meterColor(l.progress()));
			}
			if (l.icon() instanceof ItemStack stack && !stack.isEmpty()) {
				g.pose().pushMatrix();
				g.pose().translate(tx, ly - 0.5f);
				g.pose().scale(0.5f, 0.5f);
				g.item(stack, 0, 0);
				g.pose().popMatrix();
				tx += ICON_W;
			}
			if (!l.pieces().isEmpty()) {
				for (Panel.Piece piece : l.pieces()) {
					if (piece.icon() instanceof ItemStack stack && !stack.isEmpty()) {
						g.pose().pushMatrix();
						g.pose().translate(tx, ly - 0.5f);
						g.pose().scale(0.5f, 0.5f);
						g.item(stack, 0, 0);
						g.pose().popMatrix();
						tx += ICON_W;
					}
					g.text(font, piece.text(), tx, ly, piece.color());
					tx += font.width(piece.text()) + PIECE_GAP;
				}
				continue;
			}
			// Names use all the room the panel has; only what really does not fit is cut.
			int room = x + w - tx - (l.right().isEmpty() ? 0 : font.width(l.right()) + COLUMN_GAP);
			g.text(font, fit(font, l.text(), room), tx, ly, l.color());
			if (!l.right().isEmpty()) g.text(font, l.right(), x + w - font.width(l.right()), ly, l.rightColor());
		}
		return box;
	}
}
