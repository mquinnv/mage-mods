package net.mage.cubewheel.hud;

import net.minecraft.client.KeyMapping;

/**
 * On-screen hints for CubeWheel's keys, so the way into a screen is in sight where it matters: the Quests panel's
 * picker, moving the panels, the SVA catalog (Michael 2026-10-08). The key is named as Minecraft names it ("X",
 * "Left Shift"); an unbound key gives no hint.
 */
public final class KeyHint {
	private KeyHint() {}

	/** The key's name ("X"), or null when {@code key} is null or unbound. */
	public static String name(KeyMapping key) {
		if (key == null || key.isUnbound()) return null;
		String n = key.getTranslatedKeyMessage().getString();
		return n == null || n.isBlank() ? null : n.trim();
	}

	/** "[X]", or "" when unbound. */
	public static String bracket(KeyMapping key) {
		String n = name(key);
		return n == null ? "" : "[" + n + "]";
	}

	/** "Status · move [,]": {@code title} with a short action and its key; just {@code title} when unbound. */
	public static String titled(String title, String action, KeyMapping key) {
		String n = name(key);
		return n == null ? title : title + " · " + action + " [" + n + "]";
	}
}
