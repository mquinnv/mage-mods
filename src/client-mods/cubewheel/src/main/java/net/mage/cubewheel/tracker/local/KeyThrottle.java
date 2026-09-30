package net.mage.cubewheel.tracker.local;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * At most one pass per key per {@code windowMs} (capture of unmatched block breaks: one line per block id
 * per 10 s explains a miss without flooding the file). Bounded: the oldest keys are dropped past
 * {@code maxKeys}. Pure: no Minecraft/Fabric imports.
 */
public final class KeyThrottle {
	private final long windowMs;
	private final int maxKeys;
	private final Map<String, Long> last = new LinkedHashMap<>();

	public KeyThrottle(long windowMs, int maxKeys) {
		this.windowMs = windowMs;
		this.maxKeys = maxKeys;
	}

	/** True (and remembered) if {@code key} did not pass within the window before {@code now}. */
	public boolean allow(String key, long now) {
		if (key == null) return false;
		Long prev = last.get(key);
		if (prev != null && now - prev < windowMs) return false;
		last.remove(key);
		last.put(key, now);
		Iterator<Long> it = last.values().iterator();
		while (last.size() > maxKeys && it.hasNext()) {
			it.next();
			it.remove();
		}
		return true;
	}
}
