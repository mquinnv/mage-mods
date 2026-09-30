package com.mage.cubewheel.tracker;

import com.mage.cubewheel.CubeWheelClient;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Lists every trackable grouped by source; clicking a row toggles its HUD pin. Local data only. */
public final class TrackerScreen extends Screen {
	private static final long FORGET_AGE_MS = 7L * 24 * 60 * 60 * 1000;
	private static final int ROW = 12;
	private static final int ROWS_TOP = 22;
	private static final int BOTTOM = 30; // space reserved for the forget button
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFAAAAAA;
	private static final int GOLD = 0xFFFFAA00;

	/** A header row has a source and no trackable. */
	private record Row(String header, TrackerRow item) {}

	private List<Row> rows = List.of();
	private int scroll;

	public TrackerScreen() {
		super(Component.literal("Tracker"));
	}

	@Override
	protected void init() {
		int refreshW = 70;
		int w = Math.min(220, width - 24 - refreshW);
		int x = (width - w - refreshW - 4) / 2;
		addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> refresh())
				.bounds(x, height - 24, refreshW, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Forget entries older than 7 days"), b -> forgetStale())
				.bounds(x + refreshW + 4, height - 24, w, 20).build());
		rebuild();
	}

	private void rebuild() {
		TrackerStore store = CubeWheelClient.tracker();
		Map<String, List<TrackerRow>> bySource = new TreeMap<>();
		if (store != null) {
			boolean estimates = CubeWheelClient.config().current().tracker.local.enabled;
			for (TrackerRow r : store.rows(estimates)) { // already sorted by (estimated) fraction desc
				String source = r.item().source();
				bySource.computeIfAbsent(source == null ? "?" : source, k -> new ArrayList<>()).add(r);
			}
		}
		List<Row> out = new ArrayList<>();
		for (Map.Entry<String, List<TrackerRow>> e : bySource.entrySet()) {
			out.add(new Row(e.getKey(), null));
			for (TrackerRow r : e.getValue()) out.add(new Row(null, r));
		}
		rows = out;
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		super.extractRenderState(g, mouseX, mouseY, partial);
		g.centeredText(font, title, width / 2, 6, WHITE);
		if (rows.isEmpty()) {
			g.centeredText(font, "Open /jobs, /pquests, /prestige or /challenges to start tracking.",
					width / 2, ROWS_TOP + 6, GREY);
			return;
		}
		TrackerStore store = CubeWheelClient.tracker();
		long now = System.currentTimeMillis();
		int left = left();
		int w = listWidth();
		int hot = rowAt(mouseX, mouseY);
		int visible = Math.min(visibleRows(), rows.size() - scroll);
		for (int i = 0; i < visible; i++) {
			Row row = rows.get(scroll + i);
			int y = ROWS_TOP + i * ROW;
			if (row.item() == null) {
				g.text(font, row.header(), left, y + 2, GOLD);
				continue;
			}
			if (scroll + i == hot) g.fill(left, y, left + w, y + ROW, 0x40FFFFFF);
			String id = row.item().item().id();
			boolean pinned = store != null && store.isPinned(id);
			String text = (pinned ? "★ " : "☆ ") + TrackerFormat.pickerLine(row.item(), now);
			g.text(font, font.plainSubstrByWidth(text, w - 8), left + 6, y + 2, pinned ? WHITE : GREY);
			if (scroll + i == hot && row.item().estimated()) {
				List<Component> tip = new ArrayList<>();
				for (String line : TrackerFormat.estimateTooltip(row.item(), store == null ? null : store.lastAccuracy(id).orElse(null), now)) {
					tip.add(Component.literal(line));
				}
				g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		try {
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				int index = rowAt(event.x(), event.y());
				TrackerStore store = CubeWheelClient.tracker();
				if (index >= 0 && store != null && rows.get(index).item() != null) {
					store.togglePin(rows.get(index).item().item().id());
					store.save();
					return true;
				}
			}
			return super.mouseClicked(event, doubleClick);
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] tracker screen click failed", e);
			return true;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Starts one refresh run (a direct click); the picker closes during it and reopens afterwards. */
	private void refresh() {
		try {
			RefreshController.request(Minecraft.getInstance(), true);
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] tracker refresh button failed", e);
		}
	}

	private void forgetStale() {
		try {
			TrackerStore store = CubeWheelClient.tracker();
			if (store == null) return;
			int removed = store.forgetOlderThan(System.currentTimeMillis(), FORGET_AGE_MS);
			if (removed > 0) store.save();
			rebuild();
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] forgetting stale tracker entries failed", e);
		}
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
		return Math.min(360, width - 20);
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
}
