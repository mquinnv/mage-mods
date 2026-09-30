package com.mage.cubewheel.sidebar;

import com.mage.cubewheel.tracker.TrackerStore;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Feeds live sidebar values into tracked entries: config {@code tracker.sidebarLinks} maps a sidebar
 * key ("Skills") to a regex on trackable names ("(?i)skill level"). Pure: no Minecraft/Fabric imports.
 */
public final class SidebarLinker {
	private SidebarLinker() {}

	/**
	 * For each link whose key (case-insensitive) is in {@code values}, raises the current value of every
	 * matching incomplete trackable to that value (never lowers it) and marks it seen at {@code now}.
	 * Invalid regexes are skipped. Returns how many trackables were touched.
	 */
	public static int apply(Map<String, Double> values, Map<String, String> links, TrackerStore store, long now) {
		if (values == null || links == null || store == null || values.isEmpty()) return 0;
		Map<String, Double> byKey = new LinkedHashMap<>();
		values.forEach((k, v) -> {
			if (k != null && v != null) byKey.putIfAbsent(k.toLowerCase(Locale.ROOT), v);
		});
		int touched = 0;
		for (Map.Entry<String, String> link : links.entrySet()) {
			if (link.getKey() == null || link.getValue() == null) continue;
			Double value = byKey.get(link.getKey().toLowerCase(Locale.ROOT));
			if (value == null || !Double.isFinite(value)) continue;
			Pattern names;
			try {
				names = Pattern.compile(link.getValue());
			} catch (PatternSyntaxException e) {
				continue;
			}
			touched += store.applyLiveValue(names, value, now);
		}
		return touched;
	}

	/** Entries of {@code after} that are new or differ from {@code before}. */
	public static Map<String, Double> changed(Map<String, Double> before, Map<String, Double> after) {
		Map<String, Double> out = new LinkedHashMap<>();
		if (after == null) return out;
		for (Map.Entry<String, Double> e : after.entrySet()) {
			if (before == null || !Objects.equals(before.get(e.getKey()), e.getValue())) out.put(e.getKey(), e.getValue());
		}
		return out;
	}

	/** "Save at most every {@code intervalMs}" for a store that changes often. */
	public static final class SaveThrottle {
		private final long intervalMs;
		private long lastSave = Long.MIN_VALUE / 2;
		private boolean dirty;

		public SaveThrottle(long intervalMs) {
			this.intervalMs = intervalMs;
		}

		public void markDirty() {
			dirty = true;
		}

		/** True (and clean again) if there are unsaved changes, regardless of the interval (disconnect, shutdown). */
		public boolean consumeDirty() {
			boolean was = dirty;
			dirty = false;
			return was;
		}

		/** True (and clean again) when there are unsaved changes and the last save is old enough. */
		public boolean shouldSave(long now) {
			if (!dirty || now - lastSave < intervalMs) return false;
			dirty = false;
			lastSave = now;
			return true;
		}
	}
}
