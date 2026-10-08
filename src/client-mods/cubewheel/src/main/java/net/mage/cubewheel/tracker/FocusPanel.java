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
 * the entries whose estimates are newest. Each entry is three rows: the objective with its world tag and the
 * industry and tier on the right, a tall bar with the count and percentage, then pace and reward in one dim line.
 */
public final class FocusPanel {
	/** An entry stays in focus this long after its last credit. */
	public static final long WINDOW_MS = 10 * 60_000;
	public static final int MAX = 3;
	private static final int GREY = 0xFFAAAAAA;
	private static final int DIM = 0xFF808080;

	/** Counter keys credited, with when (client thread; the listener runs there too). */
	private static final Map<String, Long> credited = new LinkedHashMap<>();
	private static boolean seeded;

	private FocusPanel() {}

	/** The store credited ({@code units} &gt; 0) or took back progress for {@code key}: a credit brings it into focus. */
	public static void onProgress(String key, long units) {
		if (key == null || units <= 0) return;
		synchronized (credited) {
			credited.remove(key);
			credited.put(key, System.currentTimeMillis());
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
			Optional<FocusPanelModel.Focus> focus = FocusPanelModel.of(store, key, worlds, now);
			if (focus.isEmpty()) continue;
			FocusPanelModel.Focus f = focus.get();
			lines.add(new Panel.Line(f.tag(), JobsPanel.worldTagColor(f.tag()), f.objective(), Panel.WHITE, f.heading(), GREY));
			String bar = f.done() ? "hand in ✓" : f.fraction() >= 0 ? f.count() + " · " + f.percent() + "%" : f.count();
			lines.add(new Panel.Line("", 0, bar, f.done() ? Panel.GREEN : Panel.WHITE, "")
					.withProgress(f.fraction() >= 0 ? f.fraction() : 0).withTall());
			List<String> extra = new ArrayList<>(2);
			if (!f.pace().isEmpty()) extra.add(f.pace());
			if (!f.rewards().isEmpty()) extra.add(String.join(" · ", f.rewards()));
			if (!extra.isEmpty()) lines.add(new Panel.Line(String.join(" · ", extra), DIM).withLoose());
		}
		if (lines.isEmpty()) return Optional.empty();
		CubeWheelConfig.Position p = cfg.focus.position;
		return Optional.of(new Panel("Focus", lines, HudLayout.Corner.parse(p.corner), p.x, p.y));
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
