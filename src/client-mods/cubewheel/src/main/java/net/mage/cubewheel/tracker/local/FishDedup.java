package net.mage.cubewheel.tracker.local;

import java.util.List;

/**
 * One catch, two possible signals: a vanilla bobber reel-in ({@link FishDetector}) and ManaCube's catch
 * chat line ({@link FishCatchParser}). Within {@link #WINDOW_MS} of each other they are the same catch and
 * count once, preferring the chat line (it names the species): a reel-in after a chat catch is dropped, and
 * a chat catch after a counted reel-in takes back what the reel-in added before counting itself. Pure: no
 * Minecraft/Fabric imports.
 */
public final class FishDedup {
	public static final long WINDOW_MS = 2_000;

	private long lastChat = Long.MIN_VALUE;
	private long lastHook = Long.MIN_VALUE;
	private List<LocalCounter.Contribution> hookAdded = List.of();

	/** A reel-in at {@code now}: false if a chat catch was just counted (the same catch). */
	public boolean hookAllowed(long now) {
		return !within(lastChat, now);
	}

	/** Records what a counted reel-in added, for a following chat line to take back. */
	public void hookCounted(long now, List<LocalCounter.Contribution> added) {
		lastHook = now;
		hookAdded = added == null ? List.of() : List.copyOf(added);
	}

	/**
	 * A chat catch at {@code now}: returns what a reel-in within the window added (to reverse; each
	 * reel-in is taken back at most once), empty otherwise.
	 */
	public List<LocalCounter.Contribution> chatCatch(long now) {
		List<LocalCounter.Contribution> undo = within(lastHook, now) ? hookAdded : List.of();
		lastChat = now;
		lastHook = Long.MIN_VALUE;
		hookAdded = List.of();
		return undo;
	}

	private static boolean within(long then, long now) {
		return then != Long.MIN_VALUE && now - then >= 0 && now - then <= WINDOW_MS;
	}
}
