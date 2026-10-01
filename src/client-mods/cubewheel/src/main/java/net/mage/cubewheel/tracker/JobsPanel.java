package net.mage.cubewheel.tracker;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.hud.HudLayout;
import net.mage.cubewheel.hud.Panel;
import net.mage.cubewheel.tracker.local.WorldInfo;
import net.mage.cubewheel.tracker.local.WorldScope;
import net.mage.cubewheel.tracker.local.mc.LocalSignals;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Minecraft adapter for {@link JobsPanelModel}: the "Jobs" HUD panel (see {@link net.mage.cubewheel.hud.PanelsHud}),
 * shown while {@code tracker.jobsPanel.enabled} in ManaCube (same gate as the tracker HUD).
 */
public final class JobsPanel {
	private static final int INDUSTRY_COLOR = 0xFFFFAA00;
	/** No world named (or the world is unknown): a shade below the current world's white. */
	private static final int NEUTRAL_COLOR = 0xFFCCCCCC;
	private static final int OTHER_WORLD_COLOR = 0xFF808080;

	private JobsPanel() {}

	public static Optional<Panel> panel(long now) {
		TrackerStore store = CubeWheelClient.tracker();
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		if (store == null || !cfg.tracker.jobsPanel.enabled || !ServerGate.active(cfg)) return Optional.empty();
		WorldScope.Mode mode = WorldScope.Mode.parse(cfg.tracker.worldFilter);
		WorldInfo at = mode == WorldScope.Mode.OFF ? null : LocalSignals.currentWorld();
		List<String> worlds = cfg.tracker.local.worlds;
		JobsPanelModel.Model m = JobsPanelModel.build(store.rows(cfg.tracker.local.enabled), store::isHidden,
				id -> store.objective(id).orElse(null),
				t -> WorldScope.relevance(WorldScope.of(t.name(), store.objective(t.id()).orElse(null), worlds), at), now);
		if (m.lines().isEmpty()) return Optional.empty();
		List<Panel.Line> lines = new ArrayList<>(m.lines().size());
		for (JobsPanelModel.Line l : m.lines()) lines.add(new Panel.Line(l.text(), color(l.tone())));
		CubeWheelConfig.Position p = cfg.tracker.jobsPanel.position;
		return Optional.of(new Panel(m.title(), lines, HudLayout.Corner.parse(p.corner), p.x, p.y));
	}

	private static int color(JobsPanelModel.Tone tone) {
		return switch (tone) {
			case INDUSTRY -> INDUSTRY_COLOR;
			case CURRENT -> Panel.WHITE;
			case NEUTRAL -> NEUTRAL_COLOR;
			case OTHER_WORLD -> OTHER_WORLD_COLOR;
			case AT_CAP -> Panel.YELLOW;
			case DONE -> Panel.GREEN;
		};
	}
}
