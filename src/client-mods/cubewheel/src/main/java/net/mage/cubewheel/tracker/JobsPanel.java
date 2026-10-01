package net.mage.cubewheel.tracker;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.hud.HudLayout;
import net.mage.cubewheel.hud.Panel;
import net.mage.cubewheel.tracker.local.WorldInfo;
import net.mage.cubewheel.tracker.local.WorldScope;
import net.mage.cubewheel.tracker.local.mc.LocalSignals;
import net.mage.cubewheel.wheel.Icons;
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
				t -> WorldScope.relevance(WorldScope.of(t.name(), store.objective(t.id()).orElse(null), worlds), at), worlds, now,
				id -> store.activity(id, now));
		if (m.lines().isEmpty()) return Optional.empty();
		List<Panel.Line> lines = new ArrayList<>(m.lines().size());
		for (JobsPanelModel.Line l : m.lines()) {
			// A fish target takes its rarity colour (as the server draws it); the count keeps the progress colour.
			int progress = color(l.tone());
			int text = l.rarity() != null && l.tone() != JobsPanelModel.Tone.DONE ? l.rarity().argb : progress;
			if (l.tone() == JobsPanelModel.Tone.INDUSTRY) {
				// An industry heading: its tool as the icon ("Farming" with a hoe), else the generic ⚒ mark.
				String name = JobsPanelModel.industryName(l.text());
				String icon = JobsPanelModel.industryIcon(name);
				if (icon != null) {
					lines.add(new Panel.Line(l.tag(), 0, name, text, l.right(), progress).withIcon(Icons.stack(icon)));
					continue;
				}
			}
			lines.add(new Panel.Line(l.tag(), worldTagColor(l.tag()), l.text(), text, l.right(), progress)
					.withAccent(accent(l.activity())).withProgress(l.progress()));
		}
		CubeWheelConfig.Position p = cfg.tracker.jobsPanel.position;
		return Optional.of(new Panel(m.title(), lines, HudLayout.Corner.parse(p.corner), p.x, p.y));
	}

	/** The bar marking an entry you made progress on: bright for the last couple of minutes, dim for a while after. */
	static int accent(Activity a) {
		return switch (a) {
			case ACTIVE -> 0xFF55FFFF;
			case RECENT -> 0x9055FFFF;
			case NONE -> 0;
		};
	}

	/** World tags in each mana world's colour. */
	private static int worldTagColor(String tag) {
		return switch (tag) {
			case "WH" -> 0xFFB8C7D9; // Wolfhaven: steel
			case "TR" -> 0xFF5FD35F; // Tangleroots: jungle green
			case "SA" -> 0xFFE8C98A; // Sandara: sand
			case "IH" -> 0xFF9EE7FF; // Icehaven: ice
			case "MO" -> 0xFFB98AE8; // Morend: end purple
			case "BL" -> 0xFFFF7A45; // Burninglands: fire
			case "\u2726" -> 0xFFFFD75E; // any special world
			default -> Panel.GRAY;
		};
	}

	private static int color(JobsPanelModel.Tone tone) {
		return switch (tone) {
			case INDUSTRY -> INDUSTRY_COLOR;
			case CURRENT -> Panel.WHITE;
			case NEUTRAL -> NEUTRAL_COLOR;
			case OTHER_WORLD -> OTHER_WORLD_COLOR;
			case AT_CAP -> Panel.GREEN; // "✓?": probably ready to claim
			case DONE -> Panel.GREEN;
		};
	}
}
