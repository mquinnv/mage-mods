package net.mage.cubewheel.cooldown;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.hud.Durations;
import net.mage.cubewheel.hud.Panel;
import net.mage.cubewheel.mixin.HudAccessor;
import net.mage.cubewheel.tracker.local.ActionBarFeed;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Minecraft adapter for mcMMO ability cooldowns. Purely passive: reads action-bar messages (the same packet
 * and Hud hooks as local counting, via {@code LocalSignals}, plus its own per-tick poll of the Hud overlay,
 * deduped by its own {@link ActionBarFeed}) and chat (ALLOW_GAME, never hides anything), feeds
 * {@link McmmoParser} / {@link McmmoCooldowns}, and adds lines to the Cooldowns panel. Active only in
 * ManaCube Survival with {@code cooldowns.enabled} and {@code cooldowns.mcmmo}. Every hook is guarded; after
 * {@link #MAX_FAILURES} failures the feature switches itself off for the session.
 */
public final class McmmoWatcher {
	private static final int MAX_FAILURES = 10;
	/** Under this many ms left a countdown is shown yellow. */
	private static final long ENDING_SOON_MS = 10_000;

	private static final ActionBarFeed actionBars = new ActionBarFeed();
	private static McmmoCooldowns cooldowns;
	private static int failures;

	private McmmoWatcher() {}

	public static void init(Path configDir) {
		cooldowns = new McmmoCooldowns(configDir.resolve("cubewheel-mcmmo.json"));
		cooldowns.load();
		ClientTickEvents.END_CLIENT_TICK.register(McmmoWatcher::poll);
	}

	private static boolean active() {
		if (cooldowns == null || failures >= MAX_FAILURES) return false;
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		return cfg.cooldowns.enabled && cfg.cooldowns.mcmmo && ServerGate.survival(cfg);
	}

	/** From LocalSignals' action-bar hooks: {@code source} is PACKET or HUD. */
	public static void onActionBarEvent(ActionBarFeed.Source source, Component message) {
		try {
			if (message == null || !active()) return;
			String text = message.getString();
			long now = System.currentTimeMillis();
			if (actionBars.event(source, text, now)) handle(text, now);
		} catch (RuntimeException | LinkageError e) {
			fail(e);
		}
	}

	/** END_CLIENT_TICK: the Hud's action-bar text, for when neither hook fires. */
	private static void poll(Minecraft mc) {
		try {
			if (mc.player == null || !active()) return;
			HudAccessor hud = (HudAccessor) mc.gui.hud;
			Component message = hud.cubewheel$getOverlayMessage();
			String text = message == null ? null : message.getString();
			long now = System.currentTimeMillis();
			if (actionBars.poll(text, hud.cubewheel$getOverlayMessageTime(), now)) handle(text, now);
		} catch (RuntimeException | LinkageError e) {
			fail(e);
		}
	}

	/** ClientReceiveMessageEvents.ALLOW_GAME: always returns true (never hides the message). */
	public static boolean onGameMessage(Component message, boolean overlay) {
		try {
			// Overlay (action-bar) messages also reach the Hud hook / poll; reading them here too would double them.
			if (message == null || overlay || !active()) return true;
			handle(message.getString(), System.currentTimeMillis());
		} catch (RuntimeException | LinkageError e) {
			fail(e);
		}
		return true;
	}

	private static void handle(String text, long now) {
		Optional<McmmoParser.Message> m = McmmoParser.parse(text);
		if (m.isEmpty()) return;
		if (cooldowns.apply(m.get(), now)) {
			cooldowns.save();
			CubeWheelClient.LOG.info("[cubewheel] mcMMO cooldowns learned: {}", cooldowns.learned());
		}
		CubeWheelClient.LOG.debug("[cubewheel] mcMMO: {}", m.get());
	}

	/** Cooldowns-panel lines: "Super Breaker · 3:12", then "Super Breaker · ready" (green). */
	public static List<Panel.Line> lines(long now) {
		List<Panel.Line> out = new ArrayList<>();
		try {
			if (!active()) return out;
			for (McmmoCooldowns.Row r : cooldowns.rows(now)) {
				if (r.ready()) {
					out.add(Panel.Line.split(r.ability().label, "ready", Panel.GREEN));
				} else {
					long left = r.endsAt() - now;
					out.add(Panel.Line.split(r.ability().label, Durations.shortCountdown(left),
							left < ENDING_SOON_MS ? Panel.YELLOW : Panel.WHITE));
				}
			}
		} catch (RuntimeException | LinkageError e) {
			fail(e);
		}
		return out;
	}

	private static void fail(Throwable e) {
		if (failures == 0) CubeWheelClient.LOG.error("[cubewheel] mcMMO cooldown hook failed", e);
		if (++failures == MAX_FAILURES) CubeWheelClient.LOG.warn("[cubewheel] mcMMO cooldowns switched off for this session after repeated failures");
	}
}
