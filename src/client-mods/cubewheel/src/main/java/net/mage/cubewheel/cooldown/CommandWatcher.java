package net.mage.cubewheel.cooldown;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.SentCommands;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.hud.Durations;
import net.mage.cubewheel.hud.Panel;
import net.mage.cubewheel.wheel.Icons;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Minecraft adapter for command cooldowns (/heal). Purely passive: it hears the commands the player sends
 * ({@link SentCommands}: typed or from the wheel) and the chat that follows (ALLOW_GAME, never hides anything),
 * feeds {@link CommandCooldowns}, and adds lines to the Cooldowns panel. Active only in ManaCube Survival with
 * {@code cooldowns.enabled}. Every hook is guarded; after {@link #MAX_FAILURES} failures the feature switches itself
 * off for the session.
 */
public final class CommandWatcher {
	private static final int MAX_FAILURES = 10;
	/** Under this many ms left a countdown is shown yellow. */
	private static final long ENDING_SOON_MS = 10_000;

	private static CommandCooldowns cooldowns;
	private static int failures;

	private CommandWatcher() {}

	public static void init(Path configDir) {
		cooldowns = new CommandCooldowns(configDir.resolve("cubewheel-cooldowns.json"));
		cooldowns.load();
		SentCommands.listen(CommandWatcher::onCommand);
	}

	private static boolean active() {
		if (cooldowns == null || failures >= MAX_FAILURES) return false;
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		return cfg.cooldowns.enabled && ServerGate.survival(cfg);
	}

	/** SentCommands listener: a command the player sent, typed or from the wheel. */
	static void onCommand(String commandWithSlash, long now) {
		try {
			if (!active()) return;
			cooldowns.configure(CubeWheelClient.config().current().cooldowns.commands); // the config may have been reloaded
			cooldowns.sent(commandWithSlash, now);
		} catch (RuntimeException | LinkageError e) {
			fail(e);
		}
	}

	/** ClientReceiveMessageEvents.ALLOW_GAME: always returns true (never hides the message). */
	public static boolean onGameMessage(Component message, boolean overlay) {
		try {
			if (message == null || overlay || !active()) return true;
			if (cooldowns.chat(message.getString(), System.currentTimeMillis())) {
				cooldowns.save();
				CubeWheelClient.LOG.info("[cubewheel] command cooldowns learned: {}", cooldowns.learned());
			}
		} catch (RuntimeException | LinkageError e) {
			fail(e);
		}
		return true;
	}

	/** Cooldowns-panel lines in the item-ability shape: "/" in the tag column, "Heal", the time on the right, an icon. */
	public static List<Panel.Line> lines(long now) {
		List<Panel.Line> out = new ArrayList<>();
		try {
			if (!active()) return out;
			for (CommandCooldowns.Row r : cooldowns.rows(now)) {
				long left = r.endsAt() - now;
				out.add(new Panel.Line("/", Panel.GRAY, r.label(), left < ENDING_SOON_MS ? Panel.YELLOW : Panel.WHITE,
						Durations.shortCountdown(left)).withIcon(Icons.stack(r.icon())));
			}
		} catch (RuntimeException | LinkageError e) {
			fail(e);
		}
		return out;
	}

	private static void fail(Throwable e) {
		if (failures == 0) CubeWheelClient.LOG.error("[cubewheel] command cooldown hook failed", e);
		if (++failures == MAX_FAILURES) CubeWheelClient.LOG.warn("[cubewheel] command cooldowns switched off for this session after repeated failures");
	}
}
