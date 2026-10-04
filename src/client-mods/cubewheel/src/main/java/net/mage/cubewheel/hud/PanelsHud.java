package net.mage.cubewheel.hud;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.tracker.TrackerHud;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
 * a corner can stack (see {@link HudLayout}). A panel's {@link Panel.Grid} goes under its lines, past a thin rule, in
 * columns that only grow until the world changes (see {@link GridLayout}). Attached right after {@link TrackerHud}, which
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
	/** The thin rule over a grid and the dividers between its columns. */
	private static final int DIVIDER = 0x40FFFFFF;
	/** The rule's row: a pixel of space, the rule, a pixel of space. */
	private static final int RULE_H = 3;
	/** A grid cell is at most this wide; longer text is cut with "…". */
	private static final int MAX_CELL_W = 110;
	/** A piece's wear bar, as Minecraft draws a durability bar: 13 px wide, 2 px from the item's left, 13 px down. */
	private static final int WEAR_W = 13;
	/** The light disc's rows (see {@link LightDisc#spans}); the same every frame. */
	private static final int[] DISC_SPANS = LightDisc.spans(LightDisc.SIZE);

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
	/** Per source with a grid: its column widths, which only grow until the world changes. */
	private static final java.util.Map<Integer, GridLayout> GRIDS = new java.util.HashMap<>();
	private static Object gridLevel;
	/** Client ticks since start, counted at the start of each tick; {@link #perTick} sources rebuild when it moves. */
	private static long ticks;

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

	/**
	 * {@code s} built at most once per client tick: the first frame after a tick builds it, the frames until the next
	 * tick reuse that panel. For sources whose content changes only with game state, which moves once a tick (the
	 * world, the inventory, the tracker store, chat), and whose clocks show seconds or minutes. The panel is still
	 * placed every frame at its live config {@code position}, so dragging it on the arrange screen stays smooth.
	 */
	public static Source perTick(Source s, Function<net.mage.cubewheel.config.CubeWheelConfig, net.mage.cubewheel.config.CubeWheelConfig.Position> position) {
		TickCache<Optional<Panel>> cache = new TickCache<>();
		return now -> {
			Optional<Panel> built = cache.get(ticks, () -> s.apply(now));
			if (built.isEmpty()) return built;
			net.mage.cubewheel.config.CubeWheelConfig.Position p = position.apply(CubeWheelClient.config().current());
			return Optional.of(built.get().at(HudLayout.Corner.parse(p.corner), p.x, p.y));
		};
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
		// At the start of a tick: the first frame after it sees everything that tick changed.
		ClientTickEvents.START_CLIENT_TICK.register(mc -> ticks++);
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
				if (p.isPresent() && (!p.get().lines().isEmpty() || p.get().grid() != null)) {
					panels.add(p.get());
					sources.add(i);
				}
			} catch (RuntimeException e) {
				if (!FAILED.get(i)) CubeWheelClient.LOG.error("[cubewheel] HUD panel failed", e);
				FAILED.set(i, true);
			}
		}
		if (mc.level != gridLevel) {
			gridLevel = mc.level;
			GRIDS.values().forEach(GridLayout::reset);
		}
		int[] widths = new int[panels.size()];
		List<int[]> columns = new ArrayList<>(panels.size());
		for (int k = 0; k < panels.size(); k++) {
			Panel p = panels.get(k);
			int[] cols = p.grid() == null ? null
					: GRIDS.computeIfAbsent(sources.get(k), i -> new GridLayout()).columns(cellWidths(mc.font, p.grid()));
			columns.add(cols);
			widths[k] = cols == null ? width(mc.font, p, null) : GRIDS.get(sources.get(k)).box(width(mc.font, p, cols));
		}
		// Panels stacked in one corner share the widest one's width, so they line up as one column.
		java.util.Map<HudLayout.Corner, Integer> cornerWidth = new java.util.EnumMap<>(HudLayout.Corner.class);
		for (int k = 0; k < panels.size(); k++) {
			Panel p = panels.get(k);
			if (p.corner() != HudLayout.Corner.CUSTOM) cornerWidth.merge(p.corner(), widths[k], Math::max);
		}
		List<Placed> placed = new ArrayList<>();
		for (int k = 0; k < panels.size(); k++) {
			Panel p = panels.get(k);
			try {
				int w = p.corner() == HudLayout.Corner.CUSTOM ? widths[k] : cornerWidth.get(p.corner());
				placed.add(new Placed(sources.get(k), p.title(), draw(g, mc.font, layout, p, w, columns.get(k))));
			} catch (RuntimeException e) {
				CubeWheelClient.LOG.error("[cubewheel] HUD panel draw failed", e);
			}
		}
		lastPlaced = List.copyOf(placed);
	}

	/** A row's height: text rows {@code lh}, rows of pieces a full-size icon. */
	private static int rowHeight(Panel.Line l, int lh) {
		if (!l.pieces().isEmpty()) return BIG_ICON + 1;
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
		return l.tag().isEmpty() && l.right().isEmpty();
	}

	/**
	 * Content width of {@code p}'s lines; loose lines take what the rest makes. Text lines are capped at
	 * {@link #MAX_CONTENT_W} (they are cut to fit); rows of pieces are not cut, so they are measured whole.
	 */
	private static int linesWidth(Font font, Panel p) {
		int tagW = 0, textW = 0, rightW = 0, piecesW = 0;
		for (Panel.Line l : p.lines()) {
			if (!l.tag().isEmpty()) tagW = Math.max(tagW, font.width(l.tag()) + COLUMN_GAP);
			if (!l.right().isEmpty()) rightW = Math.max(rightW, font.width(l.right()) + COLUMN_GAP);
		}
		for (Panel.Line l : p.lines()) {
			if (l.loose()) continue;
			if (!l.pieces().isEmpty()) {
				piecesW = Math.max(piecesW, textWidth(font, l));
				continue;
			}
			// Headings (no tag, no right part) may run across the whole width.
			textW = Math.max(textW, textWidth(font, l) - (heading(l) ? tagW + rightW : 0));
		}
		return Math.max(piecesW, Math.min(MAX_CONTENT_W, tagW + textW + rightW));
	}

	/** Content width of {@code p}: its lines, its grid's {@code columns} (null = none), at least its title. */
	private static int width(Font font, Panel p, int[] columns) {
		int w = Math.max(font.width(p.title()), linesWidth(font, p));
		return columns == null ? w : Math.max(w, GridLayout.width(columns));
	}

	/** Each grid cell's natural width (at most {@link #MAX_CELL_W}), by row and column. */
	private static int[][] cellWidths(Font font, Panel.Grid grid) {
		int[][] out = new int[grid.rows().size()][];
		for (int r = 0; r < out.length; r++) {
			List<Panel.Cell> row = grid.rows().get(r);
			out[r] = new int[row.size()];
			for (int c = 0; c < row.size(); c++) {
				Panel.Cell cell = row.get(c);
				int right = cellRightWidth(font, cell);
				int w = smallIconW(cell.icon()) + font.width(cell.text()) + (right == 0 ? 0 : COLUMN_GAP + right);
				out[r][c] = Math.min(MAX_CELL_W, w);
			}
		}
		return out;
	}

	/** The width of a cell's right part: its light disc, or its small item and text (0 = none). */
	private static int cellRightWidth(Font font, Panel.Cell cell) {
		if (cell.light() >= 0) return LightDisc.SIZE;
		return smallIconW(cell.rightIcon()) + (cell.right().isEmpty() ? 0 : font.width(cell.right()));
	}

	private static int smallIconW(Object icon) {
		return icon instanceof ItemStack s && !s.isEmpty() ? ICON_W : 0;
	}

	/** A half-size (8 px) item with its top left at {@code x}, {@code y}. */
	private static void smallItem(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
		g.pose().pushMatrix();
		g.pose().translate(x, y - 0.5f);
		g.pose().scale(0.5f, 0.5f);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
	}

	/** A grid row's height: a text row, or the light disc's when a cell has one. */
	private static int gridRowHeight(List<Panel.Cell> row, int lh) {
		for (Panel.Cell c : row) if (c.light() >= 0) return Math.max(lh, LightDisc.SIZE + 1);
		return lh;
	}

	/** {@code s} cut with "…" to at most {@code max} pixels. */
	private static String fit(Font font, String s, int max) {
		if (font.width(s) <= max) return s;
		if (max <= font.width("…")) return "";
		return font.plainSubstrByWidth(s, max - font.width("…")).stripTrailing() + "…";
	}

	/** {@code columns}: the grid's column widths (null without a grid), as measured for this frame. */
	private static HudLayout.Box draw(GuiGraphicsExtractor g, Font font, HudLayout layout, Panel p, int width, int[] columns) {
		// Columns: tag (aligned), text, right-aligned part.
		int tagW = 0;
		for (Panel.Line l : p.lines()) if (!l.tag().isEmpty()) tagW = Math.max(tagW, font.width(l.tag()) + COLUMN_GAP);
		int w = Math.max(width, width(font, p, columns));
		int lh = font.lineHeight + 1;
		// A panel with an empty title has no title row (the compact Charms panel).
		int titleRows = p.title().isEmpty() ? 0 : 1;
		// Rows of pieces show their icons at full size, so they are taller than text rows.
		int linesH = 0;
		for (Panel.Line l : p.lines()) linesH += rowHeight(l, lh);
		int gridH = 0;
		if (p.grid() != null) {
			if (!p.lines().isEmpty()) gridH += RULE_H;
			for (List<Panel.Cell> row : p.grid().rows()) gridH += gridRowHeight(row, lh);
		}
		int h = lh * titleRows + linesH + gridH;
		HudLayout.Box box = layout.place(p.corner(), p.x(), p.y(), w + 2 * PAD, h + 2 * PAD);
		int x = box.x() + PAD, y = box.y() + PAD;
		g.fill(box.x(), box.y(), box.x() + box.w(), box.y() + box.h(), BACKDROP);
		if (titleRows > 0) g.text(font, p.title(), x, y, GOLD);
		if (p.grid() != null) {
			drawGrid(g, font, p.grid(), GridLayout.stretch(columns, w), x, y + lh * titleRows + linesH, !p.lines().isEmpty(), lh);
		}
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
				smallItem(g, stack, tx, ly);
				tx += ICON_W;
			}
			if (!l.pieces().isEmpty()) {
				int textY = ly + (BIG_ICON - font.lineHeight) / 2;
				for (Panel.Piece piece : l.pieces()) {
					if (piece.icon() instanceof ItemStack stack && !stack.isEmpty()) {
						g.item(stack, tx, ly);
						if (piece.wear() >= 0) {
							// Minecraft's durability bar: a black track with the remaining part in colour on top.
							g.fill(tx + 2, ly + 13, tx + 2 + WEAR_W, ly + 15, 0xFF000000);
							int fill = (int) Math.round(WEAR_W * Math.min(1, piece.wear()));
							if (fill > 0) g.fill(tx + 2, ly + 13, tx + 2 + fill, ly + 14, piece.wearColor());
						}
						tx += BIG_ICON_W;
					}
					if (piece.text().isEmpty()) continue; // icon-only pieces sit tight together
					g.text(font, piece.text(), tx, textY, piece.color());
					tx += font.width(piece.text()) + PIECE_GAP;
				}
				continue;
			}
			// Names use all the room the panel has; only what really does not fit is cut.
			int room = x + w - tx - (l.right().isEmpty() ? 0 : font.width(l.right()) + COLUMN_GAP);
			g.text(font, fit(font, l.text(), room), tx, ly, l.color());
			if (!l.right().isEmpty()) g.text(font, l.right(), x + w - font.width(l.right()), ly, l.rightColor());
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

	/**
	 * {@code grid} from {@code top}, in {@code cols} starting at {@code x}: a rule over it when lines come before
	 * ({@code ruled}), a divider between the columns, and each cell's text with its right part at the column's edge.
	 */
	private static void drawGrid(GuiGraphicsExtractor g, Font font, Panel.Grid grid, int[] cols, int x, int top, boolean ruled, int lh) {
		int end = x + GridLayout.width(cols);
		if (ruled) {
			g.fill(x, top + 1, end, top + 2, DIVIDER);
			top += RULE_H;
		}
		int gridH = 0;
		for (List<Panel.Cell> row : grid.rows()) gridH += gridRowHeight(row, lh);
		for (int c = 0, cx = x; c < cols.length - 1; cx += cols[c] + GridLayout.GAP, c++) {
			int d = cx + cols[c] + GridLayout.GAP / 2;
			g.fill(d, top, d + 1, top + gridH - 1, DIVIDER);
		}
		int cy = top;
		for (List<Panel.Cell> row : grid.rows()) {
			int rh = gridRowHeight(row, lh);
			int cx = x;
			for (int c = 0; c < row.size() && c < cols.length; c++) {
				drawCell(g, font, row.get(c), cx, cy, cols[c], rh, lh);
				cx += cols[c] + GridLayout.GAP;
			}
			cy += rh;
		}
	}

	/** One grid cell in a column {@code w} wide and a row {@code rh} tall from {@code cy}; text centred in the row. */
	private static void drawCell(GuiGraphicsExtractor g, Font font, Panel.Cell cell, int cx, int cy, int w, int rh, int lh) {
		int ty = cy + (rh - lh) / 2;
		int tx = cx;
		if (cell.icon() instanceof ItemStack stack && !stack.isEmpty()) {
			smallItem(g, stack, tx, ty);
			tx += ICON_W;
		}
		int right = cellRightWidth(font, cell);
		int room = cx + w - tx - (right == 0 ? 0 : right + COLUMN_GAP);
		g.text(font, fit(font, cell.text(), room), tx, ty, cell.color());
		if (cell.light() >= 0) {
			drawLight(g, font, cell.light(), cx + w - LightDisc.SIZE, cy - 1 + (rh - LightDisc.SIZE) / 2);
		} else if (right > 0) {
			int rx = cx + w - right;
			if (cell.rightIcon() instanceof ItemStack stack && !stack.isEmpty()) {
				smallItem(g, stack, rx, ty);
				rx += ICON_W;
			}
			g.text(font, cell.right(), rx, ty, cell.rightColor());
		}
	}

	/** A light-level disc with its number inside, its top left at {@code dx}, {@code dy} (see {@link LightDisc}). */
	private static void drawLight(GuiGraphicsExtractor g, Font font, int level, int dx, int dy) {
		int[] spans = DISC_SPANS;
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
