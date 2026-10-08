package net.mage.cubewheel.tracker;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * The recent rate of progress per counter key, from the credits of the last few minutes only. The stored estimate
 * spans everything since the entry was last read, pauses included, so a fishing session at a fish every few
 * seconds read as "0.3/min · ~5 h left" (Michael 2026-10-08). Hits older than {@link #WINDOW_MS} before the latest
 * one are dropped, so a pause does not slow the pace; the rate runs from the first kept hit to the latest, over the
 * units credited after the first. Not thread-safe beyond its own locking: fed and read from the client thread.
 * Pure: no Minecraft/Fabric imports.
 */
public final class Pace {
	/** Hits this long before the latest one no longer count. */
	public static final long WINDOW_MS = 3 * 60_000;
	/** A rate needs this many units after the first kept hit, over at least {@link #MIN_SPAN_MS}. */
	public static final int MIN_UNITS = 3;
	public static final long MIN_SPAN_MS = 15_000;

	private record Hit(long at, long units) {}

	private final Map<String, ArrayDeque<Hit>> hits = new HashMap<>();

	/** {@code units} (&gt; 0) credited to {@code key} at {@code now}. */
	public synchronized void credit(String key, long units, long now) {
		if (key == null || units <= 0) return;
		ArrayDeque<Hit> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
		q.addLast(new Hit(now, units));
		while (!q.isEmpty() && q.peekFirst().at() < now - WINDOW_MS) q.pollFirst();
	}

	/** Units per minute for {@code key} over its recent hits; 0 until there is enough to go on. */
	public synchronized double perMin(String key) {
		ArrayDeque<Hit> q = hits.get(key);
		if (q == null || q.size() < 2) return 0;
		long first = q.peekFirst().at();
		long last = q.peekLast().at();
		long span = last - first;
		long units = 0;
		boolean skip = true;
		for (Hit h : q) {
			if (skip) {
				skip = false; // the first hit only marks where the span starts
				continue;
			}
			units += h.units();
		}
		if (units < MIN_UNITS || span < MIN_SPAN_MS) return 0;
		return units / (span / 60_000.0);
	}

	/** Forgets every key (another world, a new session). */
	public synchronized void clear() {
		hits.clear();
	}
}
