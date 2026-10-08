package net.mage.cubewheel.tracker;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.hud.HudLayout;
import net.mage.cubewheel.hud.Panel;
import net.mage.cubewheel.tracker.local.Estimate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minecraft adapter for {@link FocusPanelModel}: the "Focus" HUD panel (see {@link net.mage.cubewheel.hud.PanelsHud}),
 * shown while {@code focus.enabled} in ManaCube. It shows every entry the local counter credited within
 * {@link #WINDOW_MS}, newest first, at most {@link #MAX}: one kill counting for "Slay Monsters in Wolfhaven" and
 * "Slay Magic Mooshrooms in Wolfhaven" shows both (Michael 2026-10-08). Before any credit this session it starts on
 * the entries whose estimates are newest. Each entry is three or four rows: the objective with its world tag, on a
 * line of its own; a tall bar with the count and percentage; the industry and tier (or the quest's category) with
 * the pace in one dim line; the rewards in another when the listing had any.
 */
public final class FocusPanel {
	/** An entry stays in focus this long after its last credit. */
	public static final long WINDOW_MS = 10 * 60_000;
	public static final int MAX = 3;
	/** The bar's text once the entry is complete: centred in a full green bar (Michael 2026-10-08). */
	public static final String DONE_LABEL = "Ready to hand in ✓";
	private static final int GREY = 0xFFAAAAAA;
	private static final int DIM = 0xFF808080;

	/** Counter keys credited, with when (client thread; the listener runs there too). */
	private static final Map<String, Long> credited = new LinkedHashMap<>();
	private static boolean seeded;

	private FocusPanel() {}

	/** The recent rate per key, from the credits ({@link Pace}): the pace and time left shown under the bar. */
	private static final Pace PACE = new Pace();

	/** The store credited ({@code units} &gt; 0) or took back progress for {@code key}: a credit brings it into focus. */
	public static void onProgress(String key, long units) {
		if (key == null || units <= 0) return;
		long now = System.currentTimeMillis();
		PACE.credit(key, units, now);
		synchronized (credited) {
			credited.remove(key);
			credited.put(key, now);
			while (credited.size() > 16) credited.remove(credited.keySet().iterator().next());
		}
	}

	public static Optional<Panel> panel(long now) {
		TrackerStore store = CubeWheelClient.tracker();
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		if (store == null || cfg.focus == null || !cfg.focus.enabled || !ServerGate.active(cfg)) return Optional.empty();
		List<String> keys = inFocus(store, now);
		if (keys.isEmpty()) return Optional.empty();
		List<String> worlds = cfg.tracker.local.worlds;
		List<Panel.Line> lines = new ArrayList<>(3 * keys.size());
		for (String key : keys) {
			Optional<FocusPanelModel.Focus> focus = FocusPanelModel.of(store, key, worlds, now, PACE::perMin);
			if (focus.isEmpty()) continue;
			FocusPanelModel.Focus f = focus.get();
			// The objective has the whole first line, and a second when it needs one: a panel's content is capped at
			// MAX_CONTENT_W, which cut "Catch Uncommon Lobster while fishing in Jungles Biomes" short (Michael
			// 2026-10-08). The heading went to the dim line under the bar for the same reason.
			List<String> rows = wrap(f.objective(), f.tag());
			lines.add(new Panel.Line(f.tag(), JobsPanel.worldTagColor(f.tag()), rows.get(0), Panel.WHITE, ""));
			for (int i = 1; i < rows.size(); i++) lines.add(new Panel.Line("", 0, rows.get(i), Panel.WHITE, ""));
			String bar = f.done() ? DONE_LABEL : f.fraction() >= 0 ? f.count() + " · " + f.percent() + "%" : f.count();
			lines.add(new Panel.Line("", 0, bar, f.done() ? Panel.GREEN : Panel.WHITE, "")
					.withProgress(f.fraction() >= 0 ? f.fraction() : 0).withTall());
			// Pace first, then the heading, on one dim line: the line is cut at the panel's width and the pace is
			// the part worth keeping ("the time estimate is getting cut off"). The rewards get their own line.
			List<String> extra = new ArrayList<>(2);
			if (!f.pace().isEmpty()) extra.add(f.pace());
			if (!f.heading().isEmpty()) extra.add(f.heading());
			if (!extra.isEmpty()) lines.add(new Panel.Line(String.join(" · ", extra), DIM).withLoose());
			if (!f.rewards().isEmpty()) lines.add(new Panel.Line(String.join(" · ", f.rewards()), DIM).withLoose());
		}
		if (lines.isEmpty()) return Optional.empty();
		CubeWheelConfig.Position p = cfg.focus.position;
		return Optional.of(new Panel("Focus", lines, HudLayout.Corner.parse(p.corner), p.x, p.y));
	}

	/** At most this many rows for an objective; the last is cut with "…" by the renderer if it still overflows. */
	private static final int MAX_OBJECTIVE_ROWS = 2;

	/**
	 * {@code text} split at word boundaries into rows that fit the panel's content width beside the {@code tag}
	 * column, with the game's font; at most {@link #MAX_OBJECTIVE_ROWS}, never empty.
	 */
	private static List<String> wrap(String text, String tag) {
		List<String> rows = new ArrayList<>(MAX_OBJECTIVE_ROWS);
		try {
			net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
			int room = net.mage.cubewheel.hud.PanelsHud.MAX_CONTENT_W - (tag.isEmpty() ? 0 : font.width(tag) + 4);
			for (net.minecraft.network.chat.FormattedText part
					: font.getSplitter().splitLines(text, Math.max(40, room), net.minecraft.network.chat.Style.EMPTY)) {
				// Past the last row, the rest joins it, so the renderer shows the cut ("…") rather than losing it.
				if (rows.size() == MAX_OBJECTIVE_ROWS) rows.set(MAX_OBJECTIVE_ROWS - 1, rows.get(MAX_OBJECTIVE_ROWS - 1) + " " + part.getString());
				else rows.add(part.getString());
			}
		} catch (RuntimeException e) {
			rows.clear();
		}
		if (rows.isEmpty()) rows.add(text);
		return rows;
	}

	/** The keys credited within {@link #WINDOW_MS}, newest first, at most {@link #MAX}; seeded from the store once. */
	static List<String> inFocus(TrackerStore store, long now) {
		synchronized (credited) {
			if (!seeded) {
				seeded = true;
				for (Map.Entry<String, Long> e : newest(store, now).entrySet()) credited.putIfAbsent(e.getKey(), e.getValue());
			}
			credited.values().removeIf(at -> now - at > WINDOW_MS || at > now);
			List<Map.Entry<String, Long>> entries = new ArrayList<>(credited.entrySet());
			entries.sort(Comparator.comparingLong((Map.Entry<String, Long> e) -> e.getValue()).reversed());
			List<String> out = new ArrayList<>(MAX);
			for (Map.Entry<String, Long> e : entries) {
				if (out.size() >= MAX) break;
				out.add(e.getKey());
			}
			return out;
		}
	}

	/** Incomplete entries with a local estimate counted within the window, with when. */
	static Map<String, Long> newest(TrackerStore store, long now) {
		Map<String, Long> out = new LinkedHashMap<>();
		for (Trackable t : store.all()) {
			if (t.complete()) continue;
			Optional<Estimate> e = store.estimate(t.id());
			if (e.isPresent() && now - e.get().lastAt() <= WINDOW_MS) out.put(t.id(), e.get().lastAt());
		}
		return out;
	}
}
