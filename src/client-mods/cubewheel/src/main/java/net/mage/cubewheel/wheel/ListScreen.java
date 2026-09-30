package net.mage.cubewheel.wheel;

import net.mage.cubewheel.CommandSender;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.config.WheelNode;
import net.mage.cubewheel.homes.HomesFetcher;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Scrollable, filterable list used when a ring has too many entries for the wheel. */
public final class ListScreen extends Screen {
	private static final int ROW = 20;
	private static final int ROWS_TOP = 42;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFAAAAAA;

	private final Screen parent;
	private final WheelNode source; // the ring/dynamic node whose children this list shows
	private List<WheelNode> all;
	private List<WheelNode> shown;
	private EditBox filter;
	private int scroll;

	public ListScreen(Screen parent, WheelNode source, List<WheelNode> entries) {
		super(Component.literal(source.label == null ? "" : source.label));
		this.parent = parent;
		this.source = source;
		this.all = List.copyOf(entries);
		this.shown = this.all;
	}

	@Override
	protected void init() {
		String previous = filter == null ? "" : filter.getValue();
		filter = new EditBox(font, left(), 20, listWidth(), 16, Component.literal("Filter"));
		filter.setHint(Component.literal("Type to filter…").withStyle(ChatFormatting.DARK_GRAY));
		filter.setValue(previous);
		filter.setResponder(s -> applyFilter());
		addRenderableWidget(filter);
		setInitialFocus(filter);
		applyFilter();
	}

	private void applyFilter() {
		String q = filter.getValue().trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			shown = all;
		} else {
			List<WheelNode> out = new ArrayList<>();
			for (WheelNode n : all) {
				if (contains(n.label, q) || contains(n.command, q)) out.add(n);
			}
			shown = out;
		}
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	private static boolean contains(String s, String lowerQuery) {
		return s != null && s.toLowerCase(Locale.ROOT).contains(lowerQuery);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		super.extractRenderState(g, mouseX, mouseY, partial);
		g.centeredText(font, title, width / 2, 6, WHITE);
		int left = left();
		int w = listWidth();
		int hot = rowAt(mouseX, mouseY);
		int visible = Math.min(visibleRows(), shown.size() - scroll);
		for (int i = 0; i < visible; i++) {
			WheelNode node = shown.get(scroll + i);
			int y = ROWS_TOP + i * ROW;
			if (scroll + i == hot) g.fill(left, y, left + w, y + ROW, 0x40FFFFFF);
			ItemStack icon = Icons.stack(node.icon);
			if (!icon.isEmpty()) g.item(icon, left + 2, y + 2);
			String hint = node.command != null ? node.command : (node.isRing() || node.isDynamic()) ? "▶" : "";
			int hintWidth = font.width(hint);
			String label = font.plainSubstrByWidth(node.label == null ? "" : node.label, w - 30 - hintWidth);
			g.text(font, label, left + 22, y + 6, WHITE);
			g.text(font, hint, left + w - hintWidth - 4, y + 6, GREY);
		}
		if (shown.isEmpty()) {
			g.centeredText(font, all.isEmpty() ? "(empty)" : "(no matches)", width / 2, ROWS_TOP + 6, GREY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		try {
			if (filter.isMouseOver(event.x(), event.y())) return super.mouseClicked(event, doubleClick);
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				int row = rowAt(event.x(), event.y());
				if (row >= 0) activate(shown.get(row));
				return true;
			}
			if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
				onClose();
				return true;
			}
			return super.mouseClicked(event, doubleClick);
		} catch (RuntimeException e) {
			fail("mouseClicked", e);
			return true;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		boolean enter = event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER;
		if (enter && !shown.isEmpty()) {
			activate(shown.get(0));
			return true;
		}
		return super.keyPressed(event);
	}

	/** Esc / right-click: back to the screen that opened this list. */
	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private void activate(WheelNode node) {
		try {
			if (node.isLeaf()) {
				minecraft.gui.setScreen(null);
				CommandSender.send(node.command);
				return;
			}
			if (!node.isRing() && !node.isDynamic()) return; // placeholder: not actionable
			if (HomesFetcher.isRefreshEntry(node)) {
				RadialScreen.resolve(node, true); // user click: may force one /homes (rate-limited)
				all = List.copyOf(RadialScreen.resolve(source, false)); // re-resolve in place, no send
				applyFilter();
				return;
			}
			// Sub-rings open as lists too, so Esc always walks back up the same stack of screens.
			minecraft.gui.setScreen(new ListScreen(this, node, RadialScreen.resolve(node, true)));
		} catch (RuntimeException e) {
			fail("activate", e);
		}
	}

	private void fail(String where, RuntimeException e) {
		CubeWheelClient.LOG.error("[cubewheel] list {} failed; closing", where, e);
		minecraft.gui.setScreen(null);
	}

	private int rowAt(double x, double y) {
		int left = left();
		if (x < left || x >= left + listWidth() || y < ROWS_TOP) return -1;
		int i = (int) ((y - ROWS_TOP) / ROW);
		if (i >= visibleRows()) return -1;
		int index = scroll + i;
		return index < shown.size() ? index : -1;
	}

	private int listWidth() {
		return Math.min(320, width - 20);
	}

	private int left() {
		return (width - listWidth()) / 2;
	}

	private int visibleRows() {
		return Math.max(1, (height - ROWS_TOP - 8) / ROW);
	}

	private int maxScroll() {
		return Math.max(0, shown.size() - visibleRows());
	}
}
