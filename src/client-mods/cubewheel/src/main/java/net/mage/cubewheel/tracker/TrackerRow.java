package net.mage.cubewheel.tracker;

/**
 * One tracked entry as displayed: the stored {@link Trackable} plus the value to show, which includes a
 * local estimate when {@code estimated} ({@code counted} objective units since the last read). An
 * estimated row is never complete and its fraction stops at 0.99 until a menu read confirms it.
 * Pure: no Minecraft/Fabric imports.
 */
public record TrackerRow(Trackable item, double shownCurrent, double shownMax, boolean estimated, long counted) {
	public static TrackerRow plain(Trackable t) {
		return new TrackerRow(t, t.current(), t.max(), false, 0);
	}

	public double fraction() {
		if (!estimated) return item.fraction();
		if (shownMax <= 0) return 0;
		return Math.max(0, Math.min(0.99, shownCurrent / shownMax));
	}

	public boolean complete() {
		return !estimated && item.complete();
	}

	/** An estimate that reached the target: shown as "✓?" until a menu read confirms it. */
	public boolean atCap() {
		return estimated && shownMax > 0 && shownCurrent >= shownMax;
	}
}
