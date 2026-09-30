package com.mage.cubewheel.homes;

import java.util.List;
import java.util.Optional;

/** Decides when to send /homes and whether to swallow the reply. Pure: no Minecraft/Fabric imports. */
public final class HomesFetchPolicy {
	public static final long MIN_INTERVAL_MS = 30_000;
	public static final long WINDOW_MS = 3_000;
	public static final long MAX_AGE_MS = 300_000;

	public enum Decision { IGNORE, ACCEPT_AND_SUPPRESS, ACCEPT_PASSIVE }

	private long lastSent = Long.MIN_VALUE / 2;
	private boolean armed;

	public boolean shouldFetch(long now, boolean cacheStale) {
		return cacheStale && now - lastSent >= MIN_INTERVAL_MS;
	}

	/** User-requested refresh: ignores cache age but still honours MIN_INTERVAL_MS. */
	public boolean shouldForceFetch(long now) {
		return now - lastSent >= MIN_INTERVAL_MS;
	}

	/**
	 * The single "may this call send /homes?" decision. Only a direct user activation
	 * ({@code userInitiated}) can ever send; refreshes, Back, tick-driven and reply-driven
	 * re-resolves pass false and never send. {@code force} is the "↻ Refresh" entry (ignores
	 * cache age); otherwise the cache must be {@code stale}. Both keep MIN_INTERVAL_MS.
	 */
	public boolean mayFetch(long now, boolean userInitiated, boolean force, boolean stale) {
		if (!userInitiated) return false;
		return force ? shouldForceFetch(now) : shouldFetch(now, stale);
	}

	/** Records that a /homes command was sent at {@code now}. */
	public void armed(long now) {
		lastSent = now;
		armed = true;
	}

	public boolean isArmed(long now) {
		return armed && now - lastSent <= WINDOW_MS;
	}

	public Decision onMessage(long now, Optional<List<String>> parsed) {
		if (!isArmed(now)) armed = false;
		if (parsed.isEmpty()) return Decision.IGNORE;
		if (isArmed(now)) {
			armed = false;
			return Decision.ACCEPT_AND_SUPPRESS;
		}
		return Decision.ACCEPT_PASSIVE;
	}
}
