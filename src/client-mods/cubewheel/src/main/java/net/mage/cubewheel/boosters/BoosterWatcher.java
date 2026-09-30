package net.mage.cubewheel.boosters;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.hud.Durations;
import net.mage.cubewheel.hud.HudLayout;
import net.mage.cubewheel.hud.Panel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * Minecraft adapter for the booster tracker: an ALLOW_GAME listener that feeds chat text to
 * {@link BoosterParser} (never hides anything) and the "Boosters" HUD panel. Purely passive. Active only in
 * ManaCube Survival with {@code boosters.enabled}.
 */
public final class BoosterWatcher {
	/** Under this many ms left a booster is shown yellow. */
	private static final long ENDING_SOON_MS = 60_000;

	private static BoosterStore store;
	private static boolean failureLogged;

	private BoosterWatcher() {}

	public static void init(Path configDir) {
		store = new BoosterStore(configDir.resolve("cubewheel-boosters.json"));
		store.load(System.currentTimeMillis());
	}

	/** ClientReceiveMessageEvents.ALLOW_GAME: always returns true (never hides the message). */
	public static boolean onGameMessage(Component message, boolean overlay) {
		try {
			if (message == null || store == null) return true;
			CubeWheelConfig cfg = CubeWheelClient.config().current();
			if (!cfg.boosters.enabled || !ServerGate.survival(cfg)) return true;
			Optional<BoosterParser.Message> m = BoosterParser.parse(message.getString());
			if (m.isEmpty()) return true;
			long now = System.currentTimeMillis();
			if (store.apply(m.get(), now)) {
				store.save();
				CubeWheelClient.LOG.info("[cubewheel] booster {}: {}x {} ({})", m.get().kind(), m.get().multiplier(),
						m.get().type(), Durations.countdown(m.get().durationMs()));
			}
		} catch (RuntimeException e) {
			if (!failureLogged) CubeWheelClient.LOG.error("[cubewheel] booster parse failed", e);
			failureLogged = true;
		}
		return true;
	}

	/** The "Boosters" panel: "2x Sell · 12:34", soonest-ending first. */
	public static Optional<Panel> panel(long now) {
		if (store == null) return Optional.empty();
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		if (!cfg.boosters.enabled || !ServerGate.survival(cfg)) return Optional.empty();
		List<BoosterStore.Booster> active = store.active(now);
		if (active.isEmpty()) return Optional.empty();
		List<Panel.Line> lines = new ArrayList<>(active.size());
		for (BoosterStore.Booster b : active) {
			long left = b.endsAt() - now;
			lines.add(new Panel.Line(b.label() + " · " + Durations.countdown(left),
					left < ENDING_SOON_MS ? Panel.YELLOW : Panel.WHITE));
		}
		CubeWheelConfig.Position p = cfg.boosters.position;
		return Optional.of(new Panel("Boosters", lines, HudLayout.Corner.parse(p.corner), p.x, p.y));
	}
}
