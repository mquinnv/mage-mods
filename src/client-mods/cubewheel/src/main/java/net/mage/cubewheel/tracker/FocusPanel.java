package net.mage.cubewheel.tracker;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.hud.HudLayout;
import net.mage.cubewheel.hud.Panel;
import net.mage.cubewheel.tracker.local.Estimate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Minecraft adapter for {@link FocusPanelModel}: the "Focus" HUD panel (see {@link net.mage.cubewheel.hud.PanelsHud}),
 * shown while {@code focus.enabled} in ManaCube. It follows the entry the local counter last credited
 * ({@link #onProgress}, fed from the store's progress listener); before any credit this session it starts on the
 * entry whose estimate is newest.
 */
public final class FocusPanel {
	private static final int GREY = 0xFFAAAAAA;
	private static final int DIM = 0xFF808080;

	/** The counter key in focus (client thread), or null until something is credited. */
	private static volatile String focusKey;
	private static boolean seeded;

	private FocusPanel() {}

	/** The store credited ({@code units} &gt; 0) or took back progress for {@code key}: a credit takes the focus. */
	public static void onProgress(String key, long units) {
		if (key != null && units > 0) focusKey = key;
	}

	public static Optional<Panel> panel(long now) {
		TrackerStore store = CubeWheelClient.tracker();
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		if (store == null || cfg.focus == null || !cfg.focus.enabled || !ServerGate.active(cfg)) return Optional.empty();
		String key = focusKey;
		if (key == null && !seeded) {
			seeded = true;
			key = focusKey = newest(store);
		}
		if (key == null) return Optional.empty();
		List<String> worlds = cfg.tracker.local.worlds;
		Optional<FocusPanelModel.Focus> focus = FocusPanelModel.of(store, key, worlds, now);
		if (focus.isEmpty()) return Optional.empty();
		FocusPanelModel.Focus f = focus.get();
		List<Panel.Line> lines = new ArrayList<>(4);
		lines.add(new Panel.Line(f.tag(), JobsPanel.worldTagColor(f.tag()), f.objective(), Panel.WHITE, ""));
		String bar = f.done() ? "hand in ✓" : f.fraction() >= 0 ? f.count() + " · " + f.percent() + "%" : f.count();
		lines.add(new Panel.Line("", 0, bar, f.done() ? Panel.GREEN : Panel.WHITE, "")
				.withProgress(f.fraction() >= 0 ? f.fraction() : 0).withTall());
		if (!f.pace().isEmpty()) lines.add(new Panel.Line(f.pace(), GREY).withLoose());
		if (!f.rewards().isEmpty()) lines.add(new Panel.Line("Reward: " + String.join(" · ", f.rewards()), DIM).withLoose());
		CubeWheelConfig.Position p = cfg.focus.position;
		return Optional.of(new Panel(f.heading(), lines, HudLayout.Corner.parse(p.corner), p.x, p.y));
	}

	/** The incomplete entry whose local estimate was counted most recently; null when there is none. */
	static String newest(TrackerStore store) {
		String best = null;
		long bestAt = Long.MIN_VALUE;
		for (Trackable t : store.all()) {
			if (t.complete()) continue;
			Optional<Estimate> e = store.estimate(t.id());
			if (e.isPresent() && e.get().lastAt() > bestAt) {
				bestAt = e.get().lastAt();
				best = t.id();
			}
		}
		return best;
	}
}
