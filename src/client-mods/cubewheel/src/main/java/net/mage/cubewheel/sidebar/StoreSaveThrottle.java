package net.mage.cubewheel.sidebar;

import net.mage.cubewheel.tracker.TrackerStore;

/**
 * {@link SidebarLinker.SaveThrottle} that knows which store its dirty flag belongs to. The store can change
 * between Survival and Skyblock before a throttled save fires; a change made to the old store must still reach
 * its own file, never the new store's. Pure: no Minecraft imports.
 */
public final class StoreSaveThrottle {
	private final SidebarLinker.SaveThrottle throttle;
	private TrackerStore owner;

	public StoreSaveThrottle(long intervalMs) {
		this.throttle = new SidebarLinker.SaveThrottle(intervalMs);
	}

	/** {@code store} has unsaved changes. If another store was still dirty, that one is saved first. */
	public void markDirty(TrackerStore store) {
		if (owner != null && owner != store && throttle.consumeDirty()) owner.save();
		owner = store;
		throttle.markDirty();
	}

	/** Writes the dirty store in the background once the interval has passed. */
	public void tick(long now) {
		if (owner != null && throttle.shouldSave(now)) owner.saveInBackground();
	}

	/** Writes the dirty store now (disconnect, quit). */
	public void flush() {
		if (owner != null && throttle.consumeDirty()) owner.save();
	}
}
