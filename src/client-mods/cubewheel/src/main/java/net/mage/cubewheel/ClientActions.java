package net.mage.cubewheel;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Client actions: wheel commands starting with {@code cubewheel:} that run code in CubeWheel itself (open a screen)
 * instead of being sent to the server. Pure: no Minecraft/Fabric imports; actions are registered at client start-up.
 */
public final class ClientActions {
	/** What marks a command as a client action. */
	public static final String PREFIX = "cubewheel:";

	private static final Map<String, Runnable> ACTIONS = new HashMap<>();

	private ClientActions() {}

	/** True when the command (trimmed, one leading "/" removed) starts with {@link #PREFIX}, ignoring case. */
	public static boolean is(String command) {
		return name(command) != null;
	}

	/** The action name after the prefix, lower-cased and trimmed; null if the command is not an action. */
	public static String name(String command) {
		if (command == null) return null;
		String c = command.trim();
		if (c.startsWith("/")) c = c.substring(1);
		if (!c.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) return null;
		return c.substring(PREFIX.length()).trim().toLowerCase(Locale.ROOT);
	}

	/** Registers (or replaces) the action for {@code name}, matched case-insensitively. */
	public static void register(String name, Runnable action) {
		ACTIONS.put(name.trim().toLowerCase(Locale.ROOT), action);
	}

	/** Runs the registered action and returns true; false, doing nothing, for an unknown name or a non-action. */
	public static boolean run(String command) {
		String name = name(command);
		Runnable action = name == null ? null : ACTIONS.get(name);
		if (action == null) return false;
		action.run();
		return true;
	}

	/** Forgets every registration (tests). */
	static void clear() {
		ACTIONS.clear();
	}
}
