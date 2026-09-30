package net.mage.cubewheel.sva;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches and holds the SVA catalog, the player's own owned SVAs and one "compare with" player. Every request
 * is asynchronous, passes the gate (on ManaCube) and a shared budget of {@link #MAX_REQUESTS_PER_MINUTE};
 * the catalog is re-fetched at most every {@link #CATALOG_MAX_AGE_MS}, owned SVAs every
 * {@link #OWNED_MAX_AGE_MS} or on a manual refresh. Results are published through volatile fields, so the
 * render thread only ever reads. Nothing here talks to the game server. Pure: no Minecraft imports.
 */
public final class SvaService {
	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");

	public static final long CATALOG_MAX_AGE_MS = 6L * 60 * 60 * 1000;
	public static final long OWNED_MAX_AGE_MS = 10L * 60 * 1000;
	public static final long MANUAL_MIN_INTERVAL_MS = 15_000;
	public static final long RETRY_BACKOFF_MS = 60_000;
	public static final int MAX_REQUESTS_PER_MINUTE = 20;

	/** One GET; implementations must not throw but complete the future (exceptionally on I/O errors). */
	@FunctionalInterface
	public interface Http {
		CompletableFuture<Response> get(String url);
	}

	public record Response(int status, String body) {}

	public enum Phase { IDLE, LOADING, OFFLINE, LIMITED, FAILED }

	public record Status(Phase phase, String message) {
		static final Status IDLE = new Status(Phase.IDLE, "");
		static final Status LOADING = new Status(Phase.LOADING, "Loading…");
		static final Status OFFLINE = new Status(Phase.OFFLINE, "Offline: only fetched while on ManaCube");
		static final Status LIMITED = new Status(Phase.LIMITED, "Too many requests, try again in a minute");

		static Status failed(String message) {
			return new Status(Phase.FAILED, message);
		}
	}

	/** The compare target; {@code name} null = not comparing. {@code owned} is null until loaded. */
	public record Comparison(String name, Phase phase, String message, Set<String> owned) {
		static final Comparison NONE = new Comparison(null, Phase.IDLE, "", null);
	}

	private record Remote(String uuid, Set<String> owned, long fetchedAt) {}

	private final Http http;
	private final SvaCache cache;
	private final BooleanSupplier gate;
	private final LongSupplier clock;
	private final RateBudget budget = new RateBudget(MAX_REQUESTS_PER_MINUTE, 60_000);

	private volatile SvaCatalog catalog;
	private volatile long catalogFetchedAt;
	private volatile Status catalogStatus = Status.IDLE;
	private long catalogAttempt = Long.MIN_VALUE / 2;
	private boolean catalogInFlight;

	private volatile String self;
	private volatile Map<String, Integer> owned = Map.of();
	private volatile boolean ownedKnown;
	private volatile long ownedFetchedAt;
	private volatile Status ownedStatus = Status.IDLE;
	private long ownedAttempt = Long.MIN_VALUE / 2;
	private long lastManual = Long.MIN_VALUE / 2;
	private boolean ownedInFlight;

	private volatile Comparison comparison = Comparison.NONE;
	private final Map<String, Remote> compareCache = new HashMap<>();
	private int compareSeq;

	public SvaService(Http http, SvaCache cache, BooleanSupplier gate, LongSupplier clock) {
		this.http = http;
		this.cache = cache;
		this.gate = gate;
		this.clock = clock;
	}

	/**
	 * Reads the cached catalog from disk (no network). Parsing happens outside the lock so a caller on
	 * another thread never waits for it; a catalog fetched meanwhile wins.
	 */
	public void loadCached() {
		Optional<SvaCache.Entry> entry = cache.readCatalog();
		if (entry.isEmpty()) return;
		SvaCatalog parsed;
		try {
			parsed = SvaCatalog.parse(entry.get().body());
		} catch (IllegalArgumentException ex) {
			LOG.warn("[cubewheel] cached SVA catalog unusable: {}", ex.getMessage());
			return;
		}
		synchronized (this) {
			if (catalog != null) return;
			catalog = parsed;
			catalogFetchedAt = entry.get().fetchedAt();
		}
	}

	/** The local player's UUID; loads their cached owned SVAs (no network). Null clears. */
	public synchronized void setSelf(String uuid) {
		String dashed = uuid == null ? null : SvaApi.dashed(uuid);
		if (dashed != null && dashed.equals(self)) return;
		self = dashed;
		owned = Map.of();
		ownedKnown = false;
		ownedFetchedAt = 0;
		ownedStatus = Status.IDLE;
		if (dashed == null) return;
		cache.readOwned(dashed).ifPresent(e -> {
			try {
				owned = SvaApi.parseOwned(e.body());
				ownedKnown = true;
				ownedFetchedAt = e.fetchedAt();
			} catch (IllegalArgumentException ex) {
				LOG.warn("[cubewheel] cached owned SVAs unusable: {}", ex.getMessage());
			}
		});
	}

	public SvaCatalog catalog() {
		return catalog;
	}

	public long catalogFetchedAt() {
		return catalogFetchedAt;
	}

	public Status catalogStatus() {
		return catalogStatus;
	}

	/** itemType → count for the local player; empty if unknown. */
	public Map<String, Integer> owned() {
		return owned;
	}

	public boolean ownedKnown() {
		return ownedKnown;
	}

	public long ownedFetchedAt() {
		return ownedFetchedAt;
	}

	public Status ownedStatus() {
		return ownedStatus;
	}

	public Comparison comparison() {
		return comparison;
	}

	/**
	 * Fetches what is stale: the catalog when older than 6 h (or missing), the own owned SVAs when older than
	 * 10 min, or — {@code manual} — regardless of age but at most every {@link #MANUAL_MIN_INTERVAL_MS}.
	 * After a failure automatic retries wait {@link #RETRY_BACKOFF_MS}. Call on the client thread.
	 */
	public synchronized void refresh(boolean manual) {
		long now = clock.getAsLong();
		boolean manualAllowed = manual && now - lastManual >= MANUAL_MIN_INTERVAL_MS;
		if (manualAllowed) lastManual = now;
		boolean open = gate.getAsBoolean();

		boolean catalogStale = catalog == null || now - catalogFetchedAt >= CATALOG_MAX_AGE_MS;
		boolean catalogBackoff = now - catalogAttempt < RETRY_BACKOFF_MS && !(manualAllowed && catalog == null);
		if (catalogStale && !catalogInFlight && !catalogBackoff) {
			if (!open) catalogStatus = Status.OFFLINE;
			else fetchCatalog(now);
		}

		String me = self;
		if (me == null) return;
		boolean ownedStale = !ownedKnown || now - ownedFetchedAt >= OWNED_MAX_AGE_MS;
		boolean ownedBackoff = now - ownedAttempt < RETRY_BACKOFF_MS;
		if (!ownedInFlight && (manualAllowed || (ownedStale && !ownedBackoff))) {
			if (!open) ownedStatus = Status.OFFLINE;
			else fetchOwned(me, now);
		}
	}

	private void fetchCatalog(long now) {
		if (!budget.tryAcquire(now)) {
			catalogStatus = Status.LIMITED;
			return;
		}
		catalogAttempt = now;
		catalogInFlight = true;
		catalogStatus = Status.LOADING;
		call(SvaApi.catalogUrl()).whenComplete((body, err) -> {
			SvaCatalog parsed = null;
			String problem = err != null ? describe(err) : null;
			if (problem == null) {
				try {
					parsed = SvaCatalog.parse(body);
				} catch (IllegalArgumentException e) {
					problem = "API error: " + e.getMessage();
				}
			}
			synchronized (this) {
				catalogInFlight = false;
				if (parsed != null) {
					catalog = parsed;
					catalogFetchedAt = clock.getAsLong();
					catalogStatus = Status.IDLE;
				} else {
					catalogStatus = Status.failed(problem);
					LOG.warn("[cubewheel] SVA catalog fetch failed: {}", problem);
				}
			}
			if (parsed != null) cache.writeCatalog(body, catalogFetchedAt);
		});
	}

	private void fetchOwned(String uuid, long now) {
		if (!budget.tryAcquire(now)) {
			ownedStatus = Status.LIMITED;
			return;
		}
		ownedAttempt = now;
		ownedInFlight = true;
		ownedStatus = Status.LOADING;
		call(SvaApi.ownedUrl(uuid)).whenComplete((body, err) -> {
			Map<String, Integer> parsed = null;
			String problem = err != null ? describe(err) : null;
			if (problem == null) {
				try {
					parsed = SvaApi.parseOwned(body);
				} catch (IllegalArgumentException e) {
					problem = "API error: " + e.getMessage();
				}
			}
			long at;
			synchronized (this) {
				ownedInFlight = false;
				at = clock.getAsLong();
				if (!uuid.equals(self)) return; // the player changed meanwhile
				if (parsed != null) {
					owned = parsed;
					ownedKnown = true;
					ownedFetchedAt = at;
					ownedStatus = Status.IDLE;
				} else {
					ownedStatus = Status.failed(problem);
					LOG.warn("[cubewheel] owned SVA fetch failed: {}", problem);
				}
			}
			if (parsed != null) cache.writeOwned(uuid, body, at);
		});
	}

	/** Starts comparing with another player: Mojang name → UUID, then their owned SVAs (cached 10 min). */
	public synchronized void compare(String rawName) {
		String name = rawName == null ? "" : rawName.trim();
		int seq = ++compareSeq;
		if (!SvaApi.validName(name)) {
			comparison = new Comparison(name, Phase.FAILED, "Not a Minecraft name", null);
			return;
		}
		long now = clock.getAsLong();
		String key = name.toLowerCase(Locale.ROOT);
		Remote hit = compareCache.get(key);
		if (hit != null && now - hit.fetchedAt() < OWNED_MAX_AGE_MS) {
			comparison = new Comparison(name, Phase.IDLE, "", hit.owned());
			return;
		}
		if (!gate.getAsBoolean()) {
			comparison = new Comparison(name, Phase.OFFLINE, Status.OFFLINE.message(), null);
			return;
		}
		if (!budget.tryAcquire(now)) {
			comparison = new Comparison(name, Phase.LIMITED, Status.LIMITED.message(), null);
			return;
		}
		comparison = new Comparison(name, Phase.LOADING, "Looking up " + name + "…", null);
		call(SvaApi.mojangUrl(name)).handle((body, err) -> {
			if (err != null) {
				String msg = unwrap(err) instanceof HttpStatus s && (s.status == 404 || s.status == 204)
						? "No such player: " + name : describe(err);
				finishCompare(seq, new Comparison(name, Phase.FAILED, msg, null), null, null);
				return null;
			}
			Optional<String> uuid = SvaApi.parseMojang(body);
			if (uuid.isEmpty()) {
				finishCompare(seq, new Comparison(name, Phase.FAILED, "No such player: " + name, null), null, null);
				return null;
			}
			fetchCompareOwned(seq, name, key, uuid.get());
			return null;
		});
	}

	private synchronized void fetchCompareOwned(int seq, String name, String key, String uuid) {
		if (seq != compareSeq) return;
		long now = clock.getAsLong();
		if (!gate.getAsBoolean()) {
			comparison = new Comparison(name, Phase.OFFLINE, Status.OFFLINE.message(), null);
			return;
		}
		if (!budget.tryAcquire(now)) {
			comparison = new Comparison(name, Phase.LIMITED, Status.LIMITED.message(), null);
			return;
		}
		comparison = new Comparison(name, Phase.LOADING, "Loading " + name + "'s SVAs…", null);
		call(SvaApi.ownedUrl(uuid)).whenComplete((body, err) -> {
			if (err != null) {
				finishCompare(seq, new Comparison(name, Phase.FAILED, describe(err), null), null, null);
				return;
			}
			try {
				Set<String> theirs = Set.copyOf(SvaApi.parseOwned(body).keySet());
				finishCompare(seq, new Comparison(name, Phase.IDLE, "", theirs), key, new Remote(uuid, theirs, clock.getAsLong()));
			} catch (IllegalArgumentException e) {
				finishCompare(seq, new Comparison(name, Phase.FAILED, "API error: " + e.getMessage(), null), null, null);
			}
		});
	}

	private synchronized void finishCompare(int seq, Comparison result, String key, Remote remote) {
		if (key != null) compareCache.put(key, remote);
		if (seq == compareSeq) comparison = result;
	}

	public synchronized void clearComparison() {
		compareSeq++;
		comparison = Comparison.NONE;
	}

	/** A non-2xx reply. */
	static final class HttpStatus extends RuntimeException {
		final int status;

		HttpStatus(int status) {
			super("HTTP " + status, null, false, false);
			this.status = status;
		}
	}

	/** The body of a 2xx reply; other statuses and transport errors complete exceptionally. */
	private CompletableFuture<String> call(String url) {
		CompletableFuture<Response> f;
		try {
			f = http.get(url);
		} catch (RuntimeException e) {
			f = CompletableFuture.failedFuture(e);
		}
		return f.thenApply(r -> {
			if (r.status() < 200 || r.status() > 299) throw new HttpStatus(r.status());
			return r.body();
		});
	}

	private static Throwable unwrap(Throwable err) {
		Throwable t = err;
		while (t instanceof CompletionException && t.getCause() != null) t = t.getCause();
		return t;
	}

	private static String describe(Throwable err) {
		Throwable t = unwrap(err);
		if (t instanceof HttpStatus s) return "API error: HTTP " + s.status;
		if (t instanceof java.net.http.HttpTimeoutException) return "Offline: request timed out";
		if (t instanceof java.io.IOException) return "Offline: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
		return "Error: " + t;
	}
}
