package com.mage.cubewheel.sva;

import java.util.ArrayDeque;

/** At most {@code max} acquisitions in any rolling window of {@code windowMs}. Thread-safe. Pure. */
public final class RateBudget {
	private final int max;
	private final long windowMs;
	private final ArrayDeque<Long> times = new ArrayDeque<>();

	public RateBudget(int max, long windowMs) {
		this.max = max;
		this.windowMs = windowMs;
	}

	public synchronized boolean tryAcquire(long now) {
		prune(now);
		if (times.size() >= max) return false;
		times.addLast(now);
		return true;
	}

	public synchronized int used(long now) {
		prune(now);
		return times.size();
	}

	private void prune(long now) {
		while (!times.isEmpty() && now - times.peekFirst() >= windowMs) times.removeFirst();
	}
}
