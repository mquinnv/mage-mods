package net.mage.cubewheel.tracker;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.hud.HudLayout;
import net.mage.cubewheel.hud.Panel;
import net.mage.cubewheel.tracker.local.WorldScope;
import net.mage.cubewheel.tracker.local.mc.LocalSignals;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Minecraft adapter for {@link TrackerPanelModel}: the "Tracker" HUD panel (see {@link net.mage.cubewheel.hud.PanelsHud}),
 * registered after the Jobs panel so in the same corner it stacks under it. Shown while {@code tracker.hudVisible}
 * in ManaCube; job entries are left out while the Jobs panel shows them.
 */
public final class TrackerPanel {
	private static final int OTHER_WORLD_COLOR = 0xFF808080;

	private TrackerPanel() {}

	public static Optional<Panel> panel(long now) {
		TrackerStore store = CubeWheelClient.tracker();
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		if (store == null || !cfg.tracker.hudVisible || !ServerGate.active(cfg)) return Optional.empty();
		WorldScope.Mode mode = WorldScope.Mode.parse(cfg.tracker.worldFilter);
		List<TrackerStore.HudSection> sections = store.hudSections(cfg.tracker.hudMaxLines, cfg.tracker.local.enabled,
				mode == WorldScope.Mode.OFF ? null : LocalSignals.currentWorld(), cfg.tracker.local.worlds, mode,
				cfg.tracker.jobsPanel.enabled ? JobsPanelModel::isJob : null); // the Jobs panel shows those
		List<TrackerPanelModel.Line> model = TrackerPanelModel.build(sections, id -> store.objective(id).orElse(null),
				cfg.tracker.nearThreshold, cfg.tracker.local.worlds, now, id -> store.activity(id, now));
		if (model.isEmpty()) return Optional.empty();
		List<Panel.Line> lines = new ArrayList<>(model.size());
		for (TrackerPanelModel.Line l : model) {
			lines.add(new Panel.Line(l.tag(), l.tagColor(), l.text(), color(l.tone()), l.right())
					.withAccent(JobsPanel.accent(l.activity())).withProgress(l.progress()));
		}
		CubeWheelConfig.Position p = cfg.tracker.position;
		return Optional.of(new Panel(TrackerPanelModel.TITLE, lines, HudLayout.Corner.parse(p.corner), p.x, p.y));
	}

	private static int color(TrackerPanelModel.Tone tone) {
		return switch (tone) {
			case HEADING, DETAIL -> Panel.GRAY;
			case DONE -> Panel.GREEN;
			case NEAR -> Panel.YELLOW;
			case NORMAL -> Panel.WHITE;
			case OTHER_WORLD -> OTHER_WORLD_COLOR;
		};
	}
}
