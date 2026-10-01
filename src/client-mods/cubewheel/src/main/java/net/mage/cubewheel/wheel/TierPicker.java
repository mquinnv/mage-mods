package net.mage.cubewheel.wheel;

/**
 * Which tier of the hovered slice is selected. Distance from the centre picks a tier, the mouse wheel steps
 * through them, and Shift jumps to the outermost. A scroll holds until the pointer moves to another slice or
 * crosses into another tier by distance.
 */
public final class TierPicker {
	private Object slice;
	private int distanceTier;
	private int scrolled = -1;

	/**
	 * The selected tier for {@code slice}.
	 *
	 * @param slice the hovered slice (compared by identity), or null when nothing is hovered
	 * @param distanceTier the tier the pointer's distance picks
	 * @param tiers how many outer tiers the slice has
	 * @param shift whether Shift is held
	 */
	public int pick(Object slice, int distanceTier, int tiers, boolean shift) {
		if (slice != this.slice || distanceTier != this.distanceTier) {
			this.slice = slice;
			this.distanceTier = distanceTier;
			scrolled = -1;
		}
		if (slice == null || tiers <= 0) return 0;
		if (shift) return tiers;
		return scrolled >= 0 ? Math.min(scrolled, tiers) : Math.min(distanceTier, tiers);
	}

	/** One wheel notch on the slice last passed to {@link #pick}: up (positive) steps outward, down steps inward. */
	public void scroll(double amount, int tiers) {
		if (slice == null || tiers <= 0 || amount == 0) return;
		int base = scrolled >= 0 ? scrolled : Math.min(distanceTier, tiers);
		scrolled = Math.max(0, Math.min(tiers, base + (amount > 0 ? 1 : -1)));
	}
}
