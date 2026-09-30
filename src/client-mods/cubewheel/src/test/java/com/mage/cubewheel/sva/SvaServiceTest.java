package com.mage.cubewheel.sva;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SvaServiceTest {
	static final String SELF = "7f0e9af0-4a31-4e69-8377-14bfd7ed5a90";
	static final String OTHER = "11111111-2222-4333-8444-555555555555";

	@TempDir Path dir;
	final List<String> requests = new ArrayList<>();
	final Map<String, SvaService.Response> replies = new HashMap<>();
	boolean gate = true;
	long now = 1_000_000;
	/** When set, requests stay pending until completed by the test. */
	CompletableFuture<SvaService.Response> pending;

	SvaService svc;

	@BeforeEach void setUp() throws IOException {
		replies.put(SvaApi.catalogUrl(), new SvaService.Response(200, SvaCatalogTest.resource("catalog-sample.json")));
		replies.put(SvaApi.ownedUrl(SELF), new SvaService.Response(200, SvaCatalogTest.resource("owned-sample.json")));
		replies.put(SvaApi.mojangUrl("Friend"), new SvaService.Response(200, "{\"id\":\"11111111222243338444555555555555\",\"name\":\"Friend\"}"));
		replies.put(SvaApi.ownedUrl(OTHER), new SvaService.Response(200, "[{\"itemType\":\"sunsword\"},{\"itemType\":\"samuraikatana\"}]"));
		svc = newService();
	}

	SvaService newService() {
		return new SvaService(url -> {
			requests.add(url);
			if (pending != null) return pending;
			SvaService.Response r = replies.get(url);
			return CompletableFuture.completedFuture(r == null ? new SvaService.Response(404, "") : r);
		}, new SvaCache(dir.resolve("cache")), () -> gate, () -> now);
	}

	@Test void firstRefreshFetchesCatalogAndOwnedThenCaches() {
		svc.setSelf(SELF);
		svc.refresh(false);
		assertEquals(List.of(SvaApi.catalogUrl(), SvaApi.ownedUrl(SELF)), requests);
		assertNotNull(svc.catalog());
		assertEquals(SvaService.Phase.IDLE, svc.catalogStatus().phase());
		assertEquals(2, svc.owned().get("iridium-scythe"));
		// a second automatic refresh within the windows sends nothing
		now += 5 * 60_000;
		svc.refresh(false);
		assertEquals(2, requests.size());
		// a fresh service reads both from disk without the network
		SvaService again = newService();
		again.loadCached();
		again.setSelf(SELF);
		assertNotNull(again.catalog());
		assertTrue(again.ownedKnown());
		again.refresh(false);
		assertEquals(2, requests.size());
	}

	@Test void ownedRefetchesAfterTenMinutesCatalogAfterSixHours() {
		svc.setSelf(SELF);
		svc.refresh(false);
		now += SvaService.OWNED_MAX_AGE_MS + 1;
		svc.refresh(false);
		assertEquals(List.of(SvaApi.catalogUrl(), SvaApi.ownedUrl(SELF), SvaApi.ownedUrl(SELF)), requests);
		now += SvaService.CATALOG_MAX_AGE_MS;
		svc.refresh(false);
		assertEquals(5, requests.size());
		assertEquals(SvaApi.catalogUrl(), requests.get(3));
	}

	@Test void manualRefreshForcesOwnedButNotCatalog() {
		svc.setSelf(SELF);
		svc.refresh(false);
		now += SvaService.MANUAL_MIN_INTERVAL_MS;
		svc.refresh(true);
		assertEquals(List.of(SvaApi.catalogUrl(), SvaApi.ownedUrl(SELF), SvaApi.ownedUrl(SELF)), requests);
		svc.refresh(true); // too soon after the last manual one
		assertEquals(3, requests.size());
	}

	@Test void closedGateSendsNothingAndSaysOffline() {
		gate = false;
		svc.setSelf(SELF);
		svc.refresh(true);
		svc.compare("Friend");
		assertEquals(List.of(), requests);
		assertEquals(SvaService.Phase.OFFLINE, svc.catalogStatus().phase());
		assertEquals(SvaService.Phase.OFFLINE, svc.comparison().phase());
	}

	@Test void apiErrorsAreReportedAndRetriedOnlyAfterABackoff() {
		replies.put(SvaApi.catalogUrl(), new SvaService.Response(503, "down"));
		svc.refresh(false);
		assertEquals(SvaService.Phase.FAILED, svc.catalogStatus().phase());
		assertTrue(svc.catalogStatus().message().contains("503"));
		assertNull(svc.catalog());
		svc.refresh(false);
		assertEquals(1, requests.size());
		now += SvaService.RETRY_BACKOFF_MS;
		replies.put(SvaApi.catalogUrl(), new SvaService.Response(200, "{\"not\":\"an array\"}"));
		svc.refresh(false);
		assertEquals(2, requests.size());
		assertEquals(SvaService.Phase.FAILED, svc.catalogStatus().phase());
	}

	@Test void networkFailureKeepsCachedData() {
		svc.refresh(false);
		SvaCatalog before = svc.catalog();
		now += SvaService.CATALOG_MAX_AGE_MS + 1;
		pending = new CompletableFuture<>();
		svc.refresh(false);
		assertEquals(SvaService.Phase.LOADING, svc.catalogStatus().phase());
		svc.refresh(false); // already in flight
		assertEquals(2, requests.size());
		pending.completeExceptionally(new java.net.http.HttpTimeoutException("timed out"));
		assertEquals(SvaService.Phase.FAILED, svc.catalogStatus().phase());
		assertTrue(svc.catalog() == before);
	}

	@Test void budgetLimitsRequests() {
		// 25 look-ups of unknown names within one minute: each costs a request until the budget is spent
		for (int i = 0; i < 25; i++) svc.compare("Friend" + i);
		assertEquals(RateBudgetLimit.MAX, requests.size());
		assertEquals(SvaService.Phase.LIMITED, svc.comparison().phase());
		now += 60_000;
		svc.compare("Friend");
		assertEquals(SvaService.Phase.IDLE, svc.comparison().phase());
	}

	@Test void compareResolvesNameThenOwnedAndCaches() {
		svc.compare("Friend");
		assertEquals(List.of(SvaApi.mojangUrl("Friend"), SvaApi.ownedUrl(OTHER)), requests);
		SvaService.Comparison c = svc.comparison();
		assertEquals("Friend", c.name());
		assertEquals(SvaService.Phase.IDLE, c.phase());
		assertTrue(c.owned().contains("sunsword"));
		svc.clearComparison();
		assertNull(svc.comparison().name());
		svc.compare("friend"); // cached for 10 minutes, case-insensitive
		assertEquals(2, requests.size());
		assertTrue(svc.comparison().owned().contains("samuraikatana"));
	}

	@Test void compareUnknownOrInvalidName() {
		svc.compare("Nobody_Here");
		assertEquals(SvaService.Phase.FAILED, svc.comparison().phase());
		assertTrue(svc.comparison().message().toLowerCase().contains("no such player"));
		svc.compare("bad name!");
		assertEquals(1, requests.size());
		assertEquals(SvaService.Phase.FAILED, svc.comparison().phase());
	}

	@Test void noSelfMeansNoOwnedRequest() {
		svc.refresh(true);
		assertEquals(List.of(SvaApi.catalogUrl()), requests);
		assertFalse(svc.ownedKnown());
	}

	/** Mirrors SvaService.MAX_REQUESTS_PER_MINUTE so the test reads clearly. */
	static final class RateBudgetLimit {
		static final int MAX = SvaService.MAX_REQUESTS_PER_MINUTE;
	}
}
