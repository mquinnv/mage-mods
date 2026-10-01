package net.mage.cubewheel.tracker;

/**
 * How recently an entry made progress (a local count or a higher server value), so the panels can mark what you
 * are working on. Pure: no Minecraft/Fabric imports.
 */
public enum Activity {
	NONE,
	/** Progress within the last {@link #RECENT_MS}. */
	RECENT,
	/** Progress within the last {@link #ACTIVE_MS}. */
	ACTIVE;

	public static final long ACTIVE_MS = 2 * 60_000;
	public static final long RECENT_MS = 10 * 60_000;

	/** The activity of an entry last advanced at {@code lastAt} (epoch ms, 0 = never) as of {@code now}. */
	public static Activity of(long lastAt, long now) {
		if (lastAt <= 0) return NONE;
		long age = now - lastAt;
		return age < ACTIVE_MS ? ACTIVE : age < RECENT_MS ? RECENT : NONE;
	}
}
