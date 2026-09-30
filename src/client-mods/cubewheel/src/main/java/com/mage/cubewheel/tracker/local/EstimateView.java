package com.mage.cubewheel.tracker.local;

import com.mage.cubewheel.tracker.Trackable;
import com.mage.cubewheel.tracker.TrackerRow;

/** Display math for estimates. Pure: no Minecraft/Fabric imports. */
public final class EstimateView {
	private EstimateView() {}

	/**
	 * The row to show. Entries whose max equals the objective's target ("[4,377/1,000]") add the count to
	 * the current value; percentage entries ("Progress: 64%" of "10,000 Resources") are shown in objective
	 * units: floor(64% of 10,000) + count over 10,000. The shown value never exceeds the shown max.
	 */
	public static TrackerRow row(Trackable t, Estimate est, CounterRule rule) {
		if (est == null || est.count() <= 0 || t.complete()) return TrackerRow.plain(t);
		double shownMax = t.max();
		double base = t.current();
		if (rule != null && rule.target() > 0 && t.max() > 0 && Math.abs(t.max() - rule.target()) > 1e-9) {
			shownMax = rule.target();
			base = Math.floor(t.current() / t.max() * rule.target());
		}
		double shown = Math.min(shownMax, base + est.count());
		return new TrackerRow(t, shown, shownMax, true, est.count());
	}

	/** Change of the entry's current value per counted objective unit (0.01 for "%" of 10,000). */
	public static double unitsPerCount(Trackable t, CounterRule rule) {
		if (t == null || rule == null || rule.target() <= 0 || t.max() <= 0) return 1;
		return t.max() / rule.target();
	}
}
