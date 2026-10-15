package net.mage.cubewheel.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.Keybinds;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.hud.HudLayout;
import net.mage.cubewheel.hud.KeyHint;
import net.mage.cubewheel.hud.Panel;
import net.mage.cubewheel.tracker.Activity;
import net.mage.cubewheel.tracker.JobsPanel;
import net.mage.cubewheel.wheel.Icons;

/**
 * Minecraft adapter for {@link SkillsPanelModel}: the "mcMMO" HUD panel (see {@link net.mage.cubewheel.hud.PanelsHud}),
 * one row-as-bar per skill. Shown in ManaCube Survival while {@code skills.enabled} and there is something to list.
 */
public final class SkillsPanel {
	private SkillsPanel() {}

	public static Optional<Panel> panel(long now) {
		try {
			CubeWheelConfig cfg = CubeWheelClient.config().current();
			if (!cfg.skills.enabled || !ServerGate.survival(cfg)) return Optional.empty();
			SkillStore store = SkillsWatcher.store();
			String name = SkillsWatcher.account();
			if (store == null || name == null) return Optional.empty();
			CubeWheelConfig.Skills s = cfg.skills;
			long recentMs = s.recentMinutes * 60_000L;
			SkillsPanelModel.Mode mode = "all".equals(s.show) ? SkillsPanelModel.Mode.ALL : SkillsPanelModel.Mode.RECENT;
			Map<Skill, Long> gains = store.recentGains(name, now, recentMs);
			SkillsPanelModel.View view = SkillsPanelModel.build(store.account(name), gains,
					new SkillsPanelModel.Options(mode, recentMs, s.max, s.showChild), now);
			if (view.rows().isEmpty()) return Optional.empty();
			String powerText = view.header().powerText();
			String title = KeyHint.titled(powerText.isEmpty() ? "mcMMO" : "mcMMO · " + powerText, "/mcmmo", Keybinds.mcmmoSkills);
			List<Panel.Line> lines = new ArrayList<>(view.rows().size());
			for (SkillsPanelModel.Row r : view.rows()) {
				boolean bright = r.tone() == SkillsPanelModel.Tone.FRESH && !r.unknown();
				Panel.Line line = new Panel.Line("", 0, r.label(), bright ? Panel.WHITE : Panel.GRAY,
						r.right(), r.capped() ? Panel.GREEN : bright ? Panel.WHITE : Panel.GRAY)
						.withIcon(Icons.stack(r.skill().iconId))
						.withProgress(r.unknown() ? 0 : r.fraction());
				lines.add(r.accent() ? line.withAccent(JobsPanel.accent(Activity.ACTIVE)) : line);
			}
			CubeWheelConfig.Position p = s.position;
			return Optional.of(new Panel(title, lines, HudLayout.Corner.parse(p.corner), p.x, p.y));
		} catch (RuntimeException | LinkageError e) {
			CubeWheelClient.LOG.debug("[cubewheel] mcMMO panel failed", e);
			return Optional.empty();
		}
	}
}
