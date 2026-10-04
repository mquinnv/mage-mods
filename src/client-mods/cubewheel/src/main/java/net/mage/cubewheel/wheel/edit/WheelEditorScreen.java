package net.mage.cubewheel.wheel.edit;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.config.ConfigStore;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.config.DefaultConfig;
import net.mage.cubewheel.config.WheelNode;
import net.mage.cubewheel.wheel.Icons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The wheel editor: the whole wheel as an indented tree, edited on a draft copy and written only on Save. Every change
 * goes through {@link WheelEdits}, which also decides which buttons the selected row allows. It only ever creates plain
 * command entries and plain rings; live entries already in the wheel can be renamed, re-iconed, moved or deleted.
 */
public final class WheelEditorScreen extends Screen {
	private static final int ROW = 20;
	private static final int ROWS_TOP = 30;
	private static final int BOTTOM = 66; // the error line and two rows of buttons
	private static final int INDENT = 12;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFAAAAAA;
	private static final int RED = 0xFFFF5555;
	private static final String LEGEND = "Click to select. Adding with a ring selected puts the new entry inside it.";

	private final Screen parent;
	private final ConfigStore store;
	private List<WheelNode> wheel;
	private List<Row> rows = List.of();
	private Path selected;
	private boolean dirty;
	private String error;
	private int scroll;
	private Button addCommand, addRing, addToArc, edit, delete, up, down, out;

	public WheelEditorScreen(Screen parent) {
		super(Component.literal("Edit wheel"));
		this.parent = parent;
		this.store = CubeWheelClient.config();
		List<WheelNode> live = ConfigStore.copyOf(store.current()).wheel;
		this.wheel = live == null ? new ArrayList<>() : live;
		this.rows = WheelEdits.rows(wheel);
	}

	@Override
	protected void init() {
		int total = Math.min(460, width - 20);
		int x = (width - total) / 2;
		int y1 = height - 48;
		int y2 = height - 24;
		Button[] first = buttons(x, y1, total,
				button("Add command", this::startAddCommand),
				button("Add ring", this::startAddRing),
				button("Add to arc", this::startAddToArc),
				button("Edit", this::startEdit),
				button("Delete", () -> apply(() -> WheelEdits.delete(wheel, selected))));
		addCommand = first[0];
		addRing = first[1];
		addToArc = first[2];
		edit = first[3];
		delete = first[4];
		Button[] second = buttons(x, y2, total,
				button("Up", () -> apply(() -> WheelEdits.moveUp(wheel, selected))),
				button("Down", () -> apply(() -> WheelEdits.moveDown(wheel, selected))),
				button("Out", () -> apply(() -> WheelEdits.moveOut(wheel, selected))),
				button("Reset to default", this::resetToDefault),
				button("Save", this::save),
				button("Cancel", this::onClose));
		up = second[0];
		down = second[1];
		out = second[2];
		refreshButtons();
	}

	private Button.Builder button(String text, Runnable action) {
		return Button.builder(Component.literal(text), b -> {
			try {
				action.run();
			} catch (RuntimeException e) {
				CubeWheelClient.LOG.error("[cubewheel] wheel editor \"{}\" failed", text, e);
				error = text + " failed: " + e.getMessage();
			}
		});
	}

	/** Lays {@code builders} out side by side across {@code total} pixels at {@code y}. */
	private Button[] buttons(int x, int y, int total, Button.Builder... builders) {
		int gap = 4;
		int w = (total - gap * (builders.length - 1)) / builders.length;
		Button[] made = new Button[builders.length];
		for (int i = 0; i < builders.length; i++) {
			made[i] = addRenderableWidget(builders[i].bounds(x + i * (w + gap), y, w, 20).build());
		}
		return made;
	}

	/** Enables each button only when {@link WheelEdits} accepts that edit on the selected row (a trial on a copy). */
	private void refreshButtons() {
		if (addCommand == null) return;
		Row row = selectedRow();
		WheelNode probe = WheelNode.leaf("x", null, "/x");
		addCommand.active = add(probe).ok();
		addRing.active = add(WheelNode.ring("x", null)).ok();
		addToArc.active = row != null && WheelEdits.addToArc(wheel, selected, probe).ok();
		edit.active = row != null;
		delete.active = row != null && WheelEdits.delete(wheel, selected).ok();
		up.active = row != null && WheelEdits.moveUp(wheel, selected).ok();
		down.active = row != null && WheelEdits.moveDown(wheel, selected).ok();
		out.active = row != null && WheelEdits.moveOut(wheel, selected).ok();
	}

	/** New entries go inside a selected ring (or a live ring's extras); otherwise after the selection. */
	private static boolean addInside(Row row) {
		return row.kind() == Row.Kind.RING || row.kind() == Row.Kind.LIVE_RING;
	}

	private Row selectedRow() {
		if (selected == null) return null;
		for (Row r : rows) {
			if (r.path().equals(selected)) return r;
		}
		return null;
	}

	/** Adds a command entry, or a ring, inside the selected ring or after the selected entry (top level if none). */
	private WheelEdits.Result add(WheelNode node) {
		Row row = selectedRow();
		if (row != null && addInside(row)) return WheelEdits.addChild(wheel, selected, node);
		return WheelEdits.addLeaf(wheel, row == null ? null : selected, node);
	}

	private void startAddCommand() {
		open("New command entry", Row.Kind.LEAF, null,
				f -> apply(add(WheelNode.leaf(f.label(), blankToNull(f.icon()), f.command()))));
	}

	private void startAddRing() {
		open("New ring", Row.Kind.RING, null, f -> {
			WheelNode ring = WheelNode.ring(f.label(), blankToNull(f.icon()));
			ring.asArc = f.asArc();
			return apply(add(ring));
		});
	}

	private void startAddToArc() {
		Path slice = selected;
		open("New arc entry", Row.Kind.ARC_ENTRY, null,
				f -> apply(WheelEdits.addToArc(wheel, slice, WheelNode.leaf(f.label(), blankToNull(f.icon()), f.command()))));
	}

	private void startEdit() {
		Row row = selectedRow();
		if (row == null) return;
		Path at = row.path();
		WheelNode n = row.node();
		boolean command = row.kind() == Row.Kind.LEAF || row.kind() == Row.Kind.ARC_ENTRY;
		boolean fan = row.kind() == Row.Kind.RING || row.kind() == Row.Kind.LIVE_RING;
		open("Edit entry", row.kind(), n, f -> {
			// Null means "unchanged" to WheelEdits.edit: keep a missing icon missing rather than writing "".
			String icon = f.icon().isEmpty() && n.icon == null ? null : f.icon();
			return apply(WheelEdits.edit(wheel, at, f.label(), icon, command ? f.command() : null, fan ? f.asArc() : null));
		});
	}

	private void open(String title, Row.Kind kind, WheelNode initial, Function<NodeEditScreen.Fields, String> done) {
		error = null;
		minecraft.gui.setScreen(new NodeEditScreen(this, title, kind, initial, done));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}

	/** Runs one edit; keeps it and selects what it names, or shows its error. */
	private void apply(Supplier<WheelEdits.Result> edit) {
		error = apply(edit.get());
	}

	/** Takes {@code r} if it succeeded (returns null) or leaves everything as it was (returns its error). */
	private String apply(WheelEdits.Result r) {
		if (!r.ok()) return r.error();
		wheel = r.wheel();
		rows = WheelEdits.rows(wheel);
		selected = r.selected();
		dirty = true;
		error = null;
		scrollToSelected();
		refreshButtons();
		return null;
	}

	private void resetToDefault() {
		wheel = DefaultConfig.wheel();
		rows = WheelEdits.rows(wheel);
		selected = null;
		scroll = 0;
		dirty = true;
		error = null;
		refreshButtons();
	}

	/** Writes the edited wheel into the live config (as the settings screen saves), reports in chat, and closes. */
	private void save() {
		CubeWheelConfig draft = ConfigStore.copyOf(store.current());
		draft.wheel = wheel;
		List<String> warnings;
		try {
			warnings = store.apply(draft);
		} catch (IOException e) {
			CubeWheelClient.LOG.error("[cubewheel] could not save the wheel", e);
			error = "Could not save: " + e.getMessage();
			chat(Component.literal("[CubeWheel] could not save: " + e.getMessage()).withStyle(ChatFormatting.RED));
			return;
		}
		for (String w : warnings) chat(Component.literal("[CubeWheel] " + w).withStyle(ChatFormatting.YELLOW));
		chat(Component.literal("CubeWheel wheel saved").withStyle(ChatFormatting.GREEN));
		minecraft.gui.setScreen(parent);
	}

	/** Esc / Cancel: drop the draft and go back. */
	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		super.extractRenderState(g, mouseX, mouseY, partial);
		g.centeredText(font, dirty ? "Edit wheel (unsaved changes)" : "Edit wheel", width / 2, 6, WHITE);
		if (!store.lastLoadOk()) {
			g.centeredText(font, "cubewheel.json has an error and was not loaded. Saving here replaces it.", width / 2, 17, RED);
		} else {
			g.centeredText(font, LEGEND, width / 2, 17, GREY);
		}
		int left = left();
		int w = listWidth();
		int hot = rowAt(mouseX, mouseY);
		int visible = Math.min(visibleRows(), rows.size() - scroll);
		for (int i = 0; i < visible; i++) {
			Row row = rows.get(scroll + i);
			int y = ROWS_TOP + i * ROW;
			boolean isSelected = row.path().equals(selected);
			if (isSelected) g.fill(left, y, left + w, y + ROW, 0x60FFFFFF);
			else if (scroll + i == hot) g.fill(left, y, left + w, y + ROW, 0x30FFFFFF);
			int x = left + 2 + row.depth() * INDENT;
			ItemStack icon = Icons.stack(row.node().icon);
			if (!icon.isEmpty()) g.item(icon, x, y + 2);
			String problem = WheelNodeCheck.problem(row.node(), row.kind() == Row.Kind.ARC_ENTRY);
			String hint = RowHint.of(row);
			int labelX = x + 20;
			int hintWidth = Math.min(font.width(hint), w / 2);
			String label = row.node().label == null || row.node().label.isBlank() ? "(no label)" : row.node().label;
			if (problem != null) label = "⚠ " + label;
			g.text(font, font.plainSubstrByWidth(label, Math.max(0, left + w - hintWidth - 8 - labelX)), labelX, y + 6,
					problem != null ? RED : WHITE);
			g.text(font, font.plainSubstrByWidth(hint, hintWidth), left + w - hintWidth - 4, y + 6, GREY);
			if (problem != null && scroll + i == hot) {
				g.setTooltipForNextFrame(font, font.split(Component.literal(problem), 240), mouseX, mouseY);
			}
		}
		if (rows.isEmpty()) g.centeredText(font, "(the wheel is empty: Add command or Add ring)", width / 2, ROWS_TOP + 6, GREY);
		if (error != null) g.centeredText(font, font.plainSubstrByWidth(error, width - 20), width / 2, height - 62, RED);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		try {
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				int index = rowAt(event.x(), event.y());
				if (index >= 0) {
					Path clicked = rows.get(index).path();
					boolean again = clicked.equals(selected);
					selected = clicked;
					error = null;
					refreshButtons();
					if (doubleClick && again) startEdit();
					return true;
				}
			}
			return super.mouseClicked(event, doubleClick);
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] wheel editor click failed", e);
			return true;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
		return true;
	}

	/** Scrolls just enough to show the selected row. */
	private void scrollToSelected() {
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).path().equals(selected)) {
				if (i < scroll) scroll = i;
				else if (i >= scroll + visibleRows()) scroll = i - visibleRows() + 1;
				break;
			}
		}
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	private int rowAt(double x, double y) {
		int left = left();
		if (x < left || x >= left + listWidth() || y < ROWS_TOP) return -1;
		int i = (int) ((y - ROWS_TOP) / ROW);
		if (i >= visibleRows()) return -1;
		int index = scroll + i;
		return index < rows.size() ? index : -1;
	}

	private int listWidth() {
		return Math.min(400, width - 20);
	}

	private int left() {
		return (width - listWidth()) / 2;
	}

	private int visibleRows() {
		return Math.max(1, (height - ROWS_TOP - BOTTOM) / ROW);
	}

	private int maxScroll() {
		return Math.max(0, rows.size() - visibleRows());
	}

	/** A client chat line; logged instead when there is no player (opened from the title screen's Mod Menu). */
	private static void chat(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendSystemMessage(message);
		else CubeWheelClient.LOG.info("[cubewheel] {}", message.getString());
	}
}
