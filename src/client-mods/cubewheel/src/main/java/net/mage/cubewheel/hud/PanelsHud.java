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
 * a corner can stack (see {@link HudLayout}). A panel with a {@link Panel.Side} gets it as a second column right of
 * its lines (see {@link TwoColumn}), under the title, which spans both. Attached right after {@link TrackerHud}, which
 * reports the vanilla effect icons' height; that is reserved in the top-right corner so nothing overlaps them. Each source is asked every frame; it must be cheap and return
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
	/** A row of pieces draws its item icons at full size (16 px) plus a gap. */
	private static final int BIG_ICON = 16;
	private static final int BIG_ICON_W = 18;
	/** A capacity gauge: a bar this tall under its row, on a dark track. */
	private static final int GAUGE_H = 3;
	private static final int GAUGE_TRACK = 0x60000000;
	/** Widest a panel's content may grow; longer names are cut to fit, with "…". */
	private static final int MAX_CONTENT_W = 170;
	/** The empty part of a row's progress bar. */
	private static final int METER_TRACK = 0x18FFFFFF;
	/** The thin line between a panel's two columns. */
	private static final int DIVIDER = 0x40FFFFFF;
	/** An empty armor slot in a side column: a faint square where the item would be. */
	private static final int EMPTY_SLOT = 0x20FFFFFF;
	/** A side column's item bar: this tall, centred on its item. */
	private static final int SLOT_BAR_H = 4;

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

	/** A row's height: text rows {@code lh}, rows of pieces a full-size icon, a row with a light disc the disc. */
	private static int rowHeight(Panel.Line l, int lh) {
		if (!l.pieces().isEmpty()) return BIG_ICON + 1;
		if (l.light() >= 0) return Math.max(lh, LightDisc.SIZE + 1);
		return l.gauge() >= 0 ? lh + GAUGE_H + 1 : lh;
	}

	/** A line's text width, its icon included. */
	private static int textWidth(Font font, Panel.Line l) {
		if (!l.pieces().isEmpty()) {
			// As drawn: an icon, then its text and a gap; icon-only pieces sit tight together.
			int w = 0;
			for (Panel.Piece piece : l.pieces()) {
				w += (piece.icon() instanceof ItemStack s && !s.isEmpty() ? BIG_ICON_W : 0)
						+ (piece.text().isEmpty() ? 0 : font.width(piece.text()) + PIECE_GAP);
			}
			return w;
		}
		return font.width(l.text()) + (l.icon() instanceof ItemStack s && !s.isEmpty() ? ICON_W : 0);
	}

	/** True for a line without a tag or right part: it may run across the whole width. */
	private static boolean heading(Panel.Line l) {
		return l.tag().isEmpty() && l.right().isEmpty() && l.light() < 0;
	}

	/** The width of a line's right part: its text, or its light disc. */
	private static int rightWidth(Font font, Panel.Line l) {
		return l.light() >= 0 ? LightDisc.SIZE : font.width(l.right());
	}

	/** Content width of {@code p}'s lines (the left column when it has a side). */
	private static int linesWidth(Font font, Panel p) {
		int tagW = 0, textW = 0, rightW = 0;
		for (Panel.Line l : p.lines()) {
			if (!l.tag().isEmpty()) tagW = Math.max(tagW, font.width(l.tag()) + COLUMN_GAP);
			if (!heading(l) && rightWidth(font, l) > 0) rightW = Math.max(rightW, rightWidth(font, l) + COLUMN_GAP);
		}
		for (Panel.Line l : p.lines()) {
			// Headings (no tag, no right part) may run across the whole width.
			textW = Math.max(textW, textWidth(font, l) - (heading(l) ? tagW + rightW : 0));
		}
		return Math.min(MAX_CONTENT_W, tagW + textW + rightW);
	}

	/** Content width of {@code p}: its lines and side column, at least its title (see {@link #draw}). */
	private static int width(Font font, Panel p) {
		int side = p.side() == null ? 0 : p.side().width();
		return Math.max(font.width(p.title()), TwoColumn.width(linesWidth(font, p), side));
	}

	/** {@code s} cut with "…" to at most {@code max} pixels. */
	private static String fit(Font font, String s, int max) {
		if (font.width(s) <= max) return s;
		if (max <= font.width("…")) return "";
		return font.plainSubstrByWidth(s, max - font.width("…")).stripTrailing() + "…";
	}

	private static HudLayout.Box draw(GuiGraphicsExtractor g, Font font, HudLayout layout, Panel p, int width) {
		// Columns: tag (aligned), text, right-aligned part.
		int tagW = 0;
		for (Panel.Line l : p.lines()) if (!l.tag().isEmpty()) tagW = Math.max(tagW, font.width(l.tag()) + COLUMN_GAP);
		Panel.Side side = p.side();
		int sideW = side == null ? 0 : side.width();
		int total = Math.max(width, width(font, p));
		// The lines get what the side column leaves (all of it without one); stretched panels widen the lines.
		int w = TwoColumn.leftWidth(total, sideW);
		int lh = font.lineHeight + 1;
		// A panel with an empty title has no title row (the compact Charms panel).
		int titleRows = p.title().isEmpty() ? 0 : 1;
		// Rows of pieces show their icons at full size, so they are taller than text rows.
		int linesH = 0;
		for (Panel.Line l : p.lines()) linesH += rowHeight(l, lh);
		int h = lh * titleRows + Math.max(linesH, sideHeight(side, lh));
		HudLayout.Box box = layout.place(p.corner(), p.x(), p.y(), total + 2 * PAD, h + 2 * PAD);
		int x = box.x() + PAD, y = box.y() + PAD;
		g.fill(box.x(), box.y(), box.x() + box.w(), box.y() + box.h(), BACKDROP);
		if (titleRows > 0) g.text(font, p.title(), x, y, GOLD);
		if (side != null) drawSide(g, font, side, x + w, y + lh * titleRows, box.y() + box.h() - PAD, lh);
		int ly = y + lh * titleRows - lh;
		int lastH = lh;
		for (int i = 0; i < p.lines().size(); i++) {
			Panel.Line l = p.lines().get(i);
			ly += lastH;
			lastH = rowHeight(l, lh);
			boolean heading = heading(l);
			// An accent bar in the left padding (e.g. an entry you made progress on just now).
			if (l.accent() != 0) g.fill(box.x(), ly - 1, box.x() + PAD, ly + lh - 1, l.accent());
			if (!l.tag().isEmpty()) g.text(font, l.tag(), x, ly, l.tagColor());
			int tx = heading ? x : x + tagW;
			if (l.progress() >= 0) {
				// The row itself is the bar: filled behind the text and count in one steady colour, green once done (see Panel.meterColor).
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
				int textY = ly + (BIG_ICON - font.lineHeight) / 2;
				for (Panel.Piece piece : l.pieces()) {
					if (piece.icon() instanceof ItemStack stack && !stack.isEmpty()) {
						g.item(stack, tx, ly);
						tx += BIG_ICON_W;
					}
					if (piece.text().isEmpty()) continue; // icon-only pieces sit tight together
					g.text(font, piece.text(), tx, textY, piece.color());
					tx += font.width(piece.text()) + PIECE_GAP;
				}
				continue;
			}
			// Names use all the room the panel has; only what really does not fit is cut.
			int rightW = rightWidth(font, l);
			int room = x + w - tx - (rightW == 0 ? 0 : rightW + COLUMN_GAP);
			// The light disc's row is taller than its text: the text is centred in it.
			int ty = l.light() >= 0 ? ly + (lastH - lh) / 2 : ly;
			g.text(font, fit(font, l.text(), room), tx, ty, l.color());
			if (l.light() >= 0) drawLight(g, font, l.light(), x + w - LightDisc.SIZE, ly - 1 + (lastH - LightDisc.SIZE) / 2);
			else if (!l.right().isEmpty()) g.text(font, l.right(), x + w - font.width(l.right()), ly, l.rightColor());
			if (l.gauge() >= 0) {
				// A capacity gauge: a thin bar under the row, across the panel, on its own dark track.
				int top = ly + font.lineHeight, end = x + w;
				int fill = x + (int) Math.round((end - x) * Math.min(1, l.gauge()));
				g.fill(x, top, end, top + GAUGE_H, GAUGE_TRACK);
				if (fill > x) g.fill(x, top, fill, top + GAUGE_H, Panel.gaugeColor(l.gauge()));
			}
		}
		return box;
	}

	/** A side column's height: a full-size item row per slot, then its text lines. */
	private static int sideHeight(Panel.Side side, int lh) {
		return side == null ? 0 : side.slots().size() * BIG_ICON + side.lines().size() * lh;
	}

	/**
	 * {@code side} right of lines that end at {@code linesEnd}, from {@code top}; the divider runs down to
	 * {@code bottom}. Each slot: its item (or a faint square for an empty one) and its bar across the rest of the
	 * column; then the text lines, their right parts at the column's right edge.
	 */
	private static void drawSide(GuiGraphicsExtractor g, Font font, Panel.Side side, int linesEnd, int top, int bottom, int lh) {
		int divider = linesEnd + TwoColumn.GAP / 2;
		g.fill(divider, top, divider + 1, bottom, DIVIDER);
		int sx = linesEnd + TwoColumn.GAP, end = sx + side.width();
		int sy = top;
		for (Panel.Slot slot : side.slots()) {
			if (slot.icon() instanceof ItemStack stack && !stack.isEmpty()) g.item(stack, sx, sy);
			else g.fill(sx + 2, sy + 2, sx + BIG_ICON - 2, sy + BIG_ICON - 2, EMPTY_SLOT);
			if (slot.fraction() >= 0) {
				int bx = sx + BIG_ICON_W, by = sy + (BIG_ICON - SLOT_BAR_H) / 2;
				int fill = bx + (int) Math.round((end - bx) * Math.max(0, Math.min(1, slot.fraction())));
				g.fill(bx, by, end, by + SLOT_BAR_H, GAUGE_TRACK);
				if (fill > bx) g.fill(bx, by, fill, by + SLOT_BAR_H, slot.color());
			}
			sy += BIG_ICON;
		}
		for (Panel.Line l : side.lines()) {
			int rightW = l.right().isEmpty() ? 0 : font.width(l.right());
			g.text(font, fit(font, l.text(), side.width() - (rightW == 0 ? 0 : rightW + COLUMN_GAP)), sx, sy, l.color());
			if (rightW > 0) g.text(font, l.right(), end - rightW, sy, l.rightColor());
			sy += lh;
		}
	}

	/** A light-level disc with its number inside, its top left at {@code dx}, {@code dy} (see {@link LightDisc}). */
	private static void drawLight(GuiGraphicsExtractor g, Font font, int level, int dx, int dy) {
		int[] spans = LightDisc.spans(LightDisc.SIZE);
		int fill = LightDisc.fill(level);
		for (int row = 0; row < spans.length; row++) {
			int inset = (LightDisc.SIZE - spans[row]) / 2;
			g.fill(dx + inset, dy + row, dx + inset + spans[row], dy + row + 1, fill);
		}
		String n = String.valueOf(level);
		// The font's width counts a pixel of spacing after the last glyph; digits are 7 px tall.
		int nx = dx + (LightDisc.SIZE - (font.width(n) - 1) + 1) / 2;
		g.text(font, n, nx, dy + (LightDisc.SIZE - 7) / 2, LightDisc.text(level), false);
	}
}
