package net.mage.cubewheel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells listeners about every command the player sends, whether typed in chat (Fabric's COMMAND event) or sent by
 * the wheel ({@link CommandSender}). Nothing here sends anything. A wheel-sent command reaches both of those paths,
 * so the same command noted again within {@link #DUPLICATE_MS} is passed on once. Pure: no Minecraft/Fabric imports.
 */
public final class SentCommands {
	/** {@code commandWithSlash} is trimmed and starts with "/"; {@code now} is when it was sent (ms). */
	@FunctionalInterface
	public interface Listener {
		void sent(String commandWithSlash, long now);
	}

	public static final long DUPLICATE_MS = 100;

	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
	private static String last;
	private static long lastAt;

	private SentCommands() {}

	public static void listen(Listener listener) {
		if (listener != null) LISTENERS.add(listener);
	}

	public static void forget(Listener listener) {
		LISTENERS.remove(listener);
	}

	/** A command was sent (leading "/" optional; blank ignored). Each listener's failure is logged, never thrown. */
	public static void note(String commandWithSlash, long now) {
		if (commandWithSlash == null || commandWithSlash.isBlank()) return;
		String c = commandWithSlash.trim();
		if (!c.startsWith("/")) c = "/" + c;
		synchronized (SentCommands.class) {
			if (c.equals(last) && now - lastAt >= 0 && now - lastAt < DUPLICATE_MS) return;
			last = c;
			lastAt = now;
		}
		for (Listener l : LISTENERS) {
			try {
				l.sent(c, now);
			} catch (RuntimeException e) {
				LOG.error("[cubewheel] command listener failed for {}", c, e);
			}
		}
	}
}
