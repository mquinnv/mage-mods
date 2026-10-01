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
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
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
	/**
	 * The wheel's hold key while it is still physically down from opening this list, else null. Until it is
	 * released its presses, repeats and characters are swallowed (so it never types into the filter), the
	 * filter gets no focus, and its release does nothing.
	 */
	private KeyMapping heldKey;
	private final WheelNode source; // the ring/dynamic node whose children this list shows
	private List<WheelNode> all;
	private List<WheelNode> shown;
	private EditBox filter;
	private int scroll;

	public ListScreen(Screen parent, WheelNode source, List<WheelNode> entries) {
		this(parent, source, entries, null);
	}

	public ListScreen(Screen parent, WheelNode source, List<WheelNode> entries, KeyMapping heldKey) {
		super(Component.literal(source.label == null ? "" : source.label));
		this.heldKey = heldKey;
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
		if (!holding()) setInitialFocus(filter);
		applyFilter();
	}

	/** True while the hold key that opened this list is still down; clears itself on release. */
	private boolean holding() {
		if (heldKey == null) return false;
		if (minecraft != null && RadialScreen.typingKeyDown(minecraft, heldKey)) return true;
		heldKey = null;
		return false;
	}

	private boolean isHeldKey(KeyEvent event) {
		if (heldKey == null) return false;
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(heldKey);
		return bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() == event.key();
	}

	@Override
	public void tick() {
		super.tick();
		try {
			if (heldKey != null && !holding() && filter != null && getFocused() == null) setInitialFocus(filter);
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] list tick failed", e);
		}
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (holding()) return true; // the held wheel key's characters (and repeats) never reach the filter
		return super.charTyped(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (isHeldKey(event)) {
			heldKey = null; // releasing the wheel key here neither activates nor closes anything
			if (filter != null && getFocused() == null) setInitialFocus(filter);
			return true;
		}
		return super.keyReleased(event);
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
			SliceViews.View view = SliceViews.view(node, System.currentTimeMillis());
			String command = SliceViews.command(node, view);
			String hint = command != null ? command : SliceViews.opens(node) ? "▶" : "";
			int hintWidth = font.width(hint);
			String label = font.plainSubstrByWidth(SliceViews.label(node, view), w - 30 - hintWidth);
			g.text(font, label, left + 22, y + 6, view != null && view.colour() != null ? view.colour() : WHITE);
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
		if (isHeldKey(event) && holding()) return true; // key repeat of the held wheel key
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
			String command = SliceViews.command(node, SliceViews.view(node, System.currentTimeMillis()));
			if (command != null) {
				minecraft.gui.setScreen(null);
				CommandSender.send(command);
				return;
			}
			if (!SliceViews.opens(node)) return; // placeholder: not actionable
			if (HomesFetcher.isRefreshEntry(node)) {
				RadialScreen.resolve(node, true); // user click: may force one /homes (rate-limited)
				all = List.copyOf(RadialScreen.resolve(source, false)); // re-resolve in place, no send
				applyFilter();
				return;
			}
			// Sub-rings open as lists too, so Esc always walks back up the same stack of screens.
			minecraft.gui.setScreen(new ListScreen(this, node, RadialScreen.resolve(node, true), holding() ? heldKey : null));
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
