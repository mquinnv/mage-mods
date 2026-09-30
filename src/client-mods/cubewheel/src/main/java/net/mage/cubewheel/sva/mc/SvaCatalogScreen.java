package net.mage.cubewheel.sva.mc;

import net.mage.cubewheel.CommandSender;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.sva.Sva;
import net.mage.cubewheel.sva.SvaCatalog;
import net.mage.cubewheel.sva.SvaFilter;
import net.mage.cubewheel.sva.SvaFormat;
import net.mage.cubewheel.sva.SvaService;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Grid of every Survival SVA with search (name + lore), owned / not-owned filter, sort, and "compare with
 * player". Hover shows the SVA's name, lore and circulation; left-click runs {@code /ah search <name>} (one
 * command, a direct click). Everything else here is read-only and talks only to ManaCube's web API.
 */
public final class SvaCatalogScreen extends Screen {
	private static final int CELL = 20;
	/** Below this grid width the search row's buttons move to a row of their own. */
	private static final int NARROW = 270;
	private static final String LEGEND = "■ you  ■ them  ■ both";
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFAAAAAA;
	private static final int GOLD = 0xFFFFAA00;
	private static final int RED = 0xFFFF5555;
	private static final int MINE = 0xFF55FF55;
	private static final int THEIRS = 0xFFFF55FF;
	private static final int BOTH = 0xFF55FFFF;
	private static final int CELL_BG = 0x60000000;
	private static final int CELL_HOT = 0x60FFFFFF;

	private EditBox search;
	private EditBox compareName;
	private Button showButton;
	private Button sortButton;
	private SvaFilter.Show show = SvaFilter.Show.ALL;
	private SvaFilter.Sort sort = SvaFilter.Sort.NAME;

	private List<Sva> shown = List.of();
	private SvaCatalog filteredCatalog;
	private Map<String, Integer> filteredOwned;
	private String filteredQuery;
	private SvaFilter.Show filteredShow;
	private SvaFilter.Sort filteredSort;
	private int scroll; // in rows
	private int gridTop = 76;
	private int statusY = 66;
	/** Whether the compare box had focus in the last frame, so a resize (which re-runs init) keeps it. */
	private boolean compareHadFocus;
	private boolean errorLogged;
	private String error;

	public SvaCatalogScreen() {
		super(Component.literal("SVA Catalog"));
	}

	private static SvaService service() {
		return SvaClient.service();
	}

	@Override
	protected void init() {
		try {
			layout();
		} catch (RuntimeException e) {
			fail("init", e);
		}
	}

	private void layout() {
		SvaItems.clearIcons();
		int left = left();
		int w = gridWidth();
		String prevSearch = search == null ? "" : search.getValue();
		String prevCompare = compareName == null ? "" : compareName.getValue();
		boolean narrow = w < NARROW;

		// row 1: search, then Show / Sort / refresh beside it -- or, when narrow, on a row of their own
		int searchW = narrow ? w : w - (92 + 80 + 20 + 3 * 4 + 4);
		search = new EditBox(font, left, 20, Math.max(40, searchW), 18, Component.literal("Search"));
		search.setHint(Component.literal("Search name or lore…").withStyle(ChatFormatting.DARK_GRAY));
		search.setMaxLength(100);
		search.setValue(prevSearch);
		addRenderableWidget(search);
		int by = narrow ? 42 : 19;
		int bx = narrow ? left : left + search.getWidth() + 4;
		int showW = 92;
		int sortW = 80;
		if (narrow) { // share the row: refresh keeps 20 px, Show and Sort split the rest
			int rest = Math.max(40, w - 20 - 8);
			showW = rest * 53 / 100;
			sortW = rest - showW;
		}
		showButton = addRenderableWidget(Button.builder(Component.literal(show.label), b -> {
			show = show.next();
			b.setMessage(Component.literal(show.label));
		}).bounds(bx, by, showW, 20).build());
		sortButton = addRenderableWidget(Button.builder(Component.literal("Sort: " + sort.label), b -> {
			sort = sort.next();
			b.setMessage(Component.literal("Sort: " + sort.label));
		}).bounds(bx + showW + 4, by, sortW, 20).build());
		addRenderableWidget(Button.builder(Component.literal("↻"), b -> manualRefresh())
				.bounds(bx + showW + 4 + sortW + 4, by, 20, 20).build());

		// row 2: compare with player
		int cy = narrow ? 66 : 44;
		int compareW = narrow ? 56 : 80;
		compareName = new EditBox(font, left, cy + 1, Math.max(40, w - compareW - 20 - 8), 18,
				Component.literal("Compare with player"));
		compareName.setHint(Component.literal("Compare with player…").withStyle(ChatFormatting.DARK_GRAY));
		compareName.setMaxLength(16);
		compareName.setValue(prevCompare);
		addRenderableWidget(compareName);
		int cx = left + compareName.getWidth() + 4;
		addRenderableWidget(Button.builder(Component.literal("Compare"), b -> startCompare())
				.bounds(cx, cy, compareW, 20).build());
		addRenderableWidget(Button.builder(Component.literal("×"), b -> clearCompare())
				.bounds(cx + compareW + 4, cy, 20, 20).build());

		statusY = cy + 22;
		gridTop = statusY + 10;
		setInitialFocus(compareHadFocus ? compareName : search);
		try {
			if (service() != null) service().refresh(false); // stale-only
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] SVA refresh failed", e);
		}
		refilter(true);
	}

	/** Logs the first failure of this screen and keeps a line to draw instead of the grid. */
	private void fail(String where, RuntimeException e) {
		if (!errorLogged) {
			errorLogged = true;
			CubeWheelClient.LOG.error("[cubewheel] SVA catalog screen failed in {}", where, e);
		}
		error = "SVA catalog error (" + where + "), see the log";
	}

	private void manualRefresh() {
		try {
			SvaClient.setSelf(minecraft);
			service().refresh(true);
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] SVA refresh failed", e);
		}
	}

	private void startCompare() {
		try {
			String name = compareName.getValue().trim();
			if (name.isEmpty()) clearCompare();
			else if (service() != null) service().compare(name);
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] SVA compare failed", e);
		}
	}

	private void clearCompare() {
		try {
			compareName.setValue("");
			if (service() != null) service().clearComparison();
		} catch (RuntimeException e) {
			fail("compare", e);
		}
	}

	/** Recomputes the shown list when the query, filter, sort, catalog or owned set changed. */
	private void refilter(boolean force) {
		SvaService svc = service();
		SvaCatalog catalog = svc == null ? null : svc.catalog();
		Map<String, Integer> owned = svc == null ? Map.of() : svc.owned();
		String q = search.getValue();
		if (!force && catalog == filteredCatalog && owned == filteredOwned && q.equals(filteredQuery)
				&& show == filteredShow && sort == filteredSort) return;
		boolean queryChanged = !q.equals(filteredQuery) || show != filteredShow || sort != filteredSort;
		filteredCatalog = catalog;
		filteredOwned = owned;
		filteredQuery = q;
		filteredShow = show;
		filteredSort = sort;
		shown = catalog == null ? List.of() : SvaFilter.apply(catalog.all(), q, show, owned.keySet(), sort);
		if (queryChanged) scroll = 0;
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		try {
			if (compareName != null) compareHadFocus = compareName.isFocused();
			if (error == null) {
				renderCatalog(g, mouseX, mouseY, partial);
				return;
			}
		} catch (RuntimeException e) {
			fail("render", e);
		}
		try {
			super.extractRenderState(g, mouseX, mouseY, partial);
		} catch (RuntimeException ignored) {
			// already logged once; the error line below still shows
		}
		try {
			g.centeredText(font, error, width / 2, gridTop + 20, RED);
		} catch (RuntimeException ignored) {
			// nothing left to draw with
		}
	}

	private void renderCatalog(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		refilter(false);
		super.extractRenderState(g, mouseX, mouseY, partial);
		SvaService svc = service();
		SvaCatalog catalog = svc == null ? null : svc.catalog();
		int total = catalog == null ? 0 : catalog.all().size();
		Map<String, Integer> owned = svc == null ? Map.of() : svc.owned();
		String head = catalog == null ? "SVA Catalog"
				: "SVA Catalog · " + shown.size() + " of " + total + (svc.ownedKnown() ? " · you own " + owned.size() : "");
		SvaService.Comparison cmp = svc == null ? null : svc.comparison();
		Set<String> theirs = cmp == null ? null : cmp.owned();
		boolean legend = cmp != null && cmp.owned() != null;
		// the legend sits at the header's right end; the header gives way rather than run under it
		int headRoom = gridWidth() - (legend ? font.width(LEGEND) + 6 : 0);
		g.text(font, font.plainSubstrByWidth(head, Math.max(0, headRoom)), left(), 6, WHITE);
		if (legend) legend(g);
		statusLine(g, svc, catalog, cmp);

		int left = left();
		if (catalog == null) {
			String msg = svc == null ? "Not initialised" : switch (svc.catalogStatus().phase()) {
				case LOADING -> "Loading SVA catalog…";
				case OFFLINE -> "Offline: join ManaCube to download the SVA catalog";
				case LIMITED, FAILED -> svc.catalogStatus().message();
				default -> "No SVA catalog yet";
			};
			g.centeredText(font, msg, width / 2, gridTop + 20, svc != null && svc.catalogStatus().phase() == SvaService.Phase.FAILED ? RED : GREY);
			return;
		}
		if (shown.isEmpty()) {
			g.centeredText(font, "(no matches)", width / 2, gridTop + 20, GREY);
			return;
		}
		int cols = cols();
		int rows = visibleRows();
		int hot = indexAt(mouseX, mouseY);
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < cols; c++) {
				int i = (scroll + r) * cols + c;
				if (i >= shown.size()) break;
				Sva s = shown.get(i);
				int x = left + c * CELL;
				int y = gridTop + r * CELL;
				g.fill(x, y, x + CELL - 1, y + CELL - 1, i == hot ? CELL_HOT : CELL_BG);
				int border = borderColor(SvaFilter.mark(s.itemType(), owned.keySet(), theirs));
				if (border != 0) g.outline(x, y, CELL - 1, CELL - 1, border);
				g.item(SvaItems.icon(s), x + 1, y + 1);
			}
		}
		if (maxScroll() > 0) {
			int trackH = rows * CELL;
			int barH = Math.max(8, trackH * rows / (maxScroll() + rows));
			int barY = gridTop + (trackH - barH) * scroll / maxScroll();
			int bx = left + cols * CELL + 2;
			g.fill(bx, gridTop, bx + 2, gridTop + trackH, 0x40FFFFFF);
			g.fill(bx, barY, bx + 2, barY + barH, 0xC0FFFFFF);
		}
		if (hot >= 0) g.setComponentTooltipForNextFrame(font, tooltip(shown.get(hot), owned, cmp), mouseX, mouseY);
	}

	private void statusLine(GuiGraphicsExtractor g, SvaService svc, SvaCatalog catalog, SvaService.Comparison cmp) {
		if (svc == null) return;
		long now = System.currentTimeMillis();
		List<String> parts = new ArrayList<>();
		int color = GREY;
		SvaService.Status cs = svc.catalogStatus();
		if (catalog != null) {
			parts.add("Catalog " + SvaFormat.age(now - svc.catalogFetchedAt()));
			if (cs.phase() == SvaService.Phase.LOADING) parts.add("updating…");
			else if (cs.phase() != SvaService.Phase.IDLE) {
				parts.add(cs.message());
				color = GOLD;
			}
		}
		SvaService.Status os = svc.ownedStatus();
		if (os.phase() == SvaService.Phase.LOADING) parts.add("owned: loading…");
		else if (os.phase() != SvaService.Phase.IDLE) {
			parts.add("owned: " + os.message());
			color = GOLD;
		} else if (svc.ownedKnown()) parts.add("owned " + SvaFormat.age(now - svc.ownedFetchedAt()));
		String line = String.join(" · ", parts);
		if (cmp != null && cmp.name() != null) {
			String c = switch (cmp.phase()) {
				case IDLE -> "Comparing with " + cmp.name() + " (owns " + cmp.owned().size() + ")";
				case LOADING -> cmp.message();
				default -> cmp.name() + ": " + cmp.message();
			};
			if (cmp.phase() != SvaService.Phase.IDLE && cmp.phase() != SvaService.Phase.LOADING) color = GOLD;
			line = line.isEmpty() ? c : line + " · " + c;
		}
		g.centeredText(font, font.plainSubstrByWidth(line, width - 8), width / 2, statusY, color);
	}

	private void legend(GuiGraphicsExtractor g) {
		int lx = Math.max(left(), left() + gridWidth() - font.width(LEGEND));
		g.text(font, "■ you", lx, 6, MINE);
		g.text(font, "■ them", lx + font.width("■ you  "), 6, THEIRS);
		g.text(font, "■ both", lx + font.width("■ you  ■ them  "), 6, BOTH);
	}

	private static int borderColor(SvaFilter.Mark mark) {
		return switch (mark) {
			case MINE -> MINE;
			case THEIRS -> THEIRS;
			case BOTH -> BOTH;
			default -> 0;
		};
	}

	private List<Component> tooltip(Sva s, Map<String, Integer> owned, SvaService.Comparison cmp) {
		List<Component> tip = new ArrayList<>();
		tip.add(SvaItems.text(s.displayName()));
		for (String l : s.lore()) tip.add(SvaItems.text(l));
		tip.add(Component.empty());
		tip.add(Component.literal("Circulation: " + s.circulation()).withStyle(ChatFormatting.GRAY));
		int n = owned.getOrDefault(s.itemType(), 0);
		if (n > 0) tip.add(Component.literal("✔ You own " + (n > 1 ? n : "this")).withStyle(ChatFormatting.GREEN));
		else if (service() != null && service().ownedKnown()) tip.add(Component.literal("✘ Not owned").withStyle(ChatFormatting.DARK_GRAY));
		if (cmp != null && cmp.owned() != null) {
			boolean t = cmp.owned().contains(s.itemType());
			tip.add(Component.literal((t ? "✔ " : "✘ ") + cmp.name() + (t ? " owns this" : " does not own this"))
					.withStyle(t ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY));
		}
		String q = s.ahQuery();
		if (!q.isEmpty()) tip.add(Component.literal("Click: /ah search " + q).withStyle(ChatFormatting.YELLOW));
		return tip;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		try {
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				int i = indexAt(event.x(), event.y());
				if (i >= 0) {
					searchAuctionHouse(shown.get(i));
					return true;
				}
			}
			return super.mouseClicked(event, doubleClick);
		} catch (RuntimeException e) {
			fail("click", e);
			return true;
		}
	}

	/**
	 * A direct user click: close the screen and send exactly one "/ah search <name>" -- only in ManaCube
	 * Survival, the only place these SVAs are on the auction house.
	 */
	private void searchAuctionHouse(Sva s) {
		String q = s.ahQuery();
		if (q.isEmpty()) return;
		if (!ServerGate.survival(CubeWheelClient.config().current())) {
			if (minecraft.player != null) {
				minecraft.player.sendOverlayMessage(Component.literal("CubeWheel: /ah search only works in ManaCube Survival"));
			}
			return;
		}
		minecraft.gui.setScreen(null);
		if (!CommandSender.send("/ah search " + q) && minecraft.player != null) {
			minecraft.player.sendOverlayMessage(Component.literal("CubeWheel: /ah search only works on ManaCube"));
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		try {
			if (scrollY != 0) scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY) * 2));
		} catch (RuntimeException e) {
			fail("scroll", e);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		try {
			boolean enter = event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER;
			if (enter && compareName != null && compareName.isFocused()) {
				startCompare();
				return true;
			}
			if (event.key() == InputConstants.KEY_PAGEDOWN) {
				scroll = Math.min(maxScroll(), scroll + visibleRows());
				return true;
			}
			if (event.key() == InputConstants.KEY_PAGEUP) {
				scroll = Math.max(0, scroll - visibleRows());
				return true;
			}
			return super.keyPressed(event);
		} catch (RuntimeException e) {
			fail("key", e);
			if (event.key() == InputConstants.KEY_ESCAPE) onClose(); // never trap the player in a broken screen
			return true;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int indexAt(double x, double y) {
		int left = left();
		if (x < left || y < gridTop) return -1;
		int c = (int) ((x - left) / CELL);
		int r = (int) ((y - gridTop) / CELL);
		if (c >= cols() || r >= visibleRows()) return -1;
		int i = (scroll + r) * cols() + c;
		return i < shown.size() ? i : -1;
	}

	private int gridWidth() {
		return cols() * CELL;
	}

	private int cols() {
		return Math.max(1, Math.min(24, (width - 24) / CELL));
	}

	private int left() {
		return (width - cols() * CELL) / 2;
	}

	private int visibleRows() {
		return Math.max(1, (height - gridTop - 6) / CELL);
	}

	private int maxScroll() {
		int rows = (shown.size() + cols() - 1) / cols();
		return Math.max(0, rows - visibleRows());
	}
}
