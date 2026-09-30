package net.mage.cubewheel.homes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HomesFetchPolicyTest {
	@Test void fetchOnlyWhenStaleAndNotRecent() {
		HomesFetchPolicy p = new HomesFetchPolicy();
		assertFalse(p.shouldFetch(0, false));
		assertTrue(p.shouldFetch(0, true));
		p.armed(0);
		assertFalse(p.shouldFetch(10_000, true));
		assertTrue(p.shouldFetch(30_001, true));
	}
	@Test void suppressesOnlyWhileArmed() {
		HomesFetchPolicy p = new HomesFetchPolicy();
		p.armed(0);
		assertEquals(HomesFetchPolicy.Decision.ACCEPT_AND_SUPPRESS, p.onMessage(500, Optional.of(List.of("a"))));
		assertEquals(HomesFetchPolicy.Decision.ACCEPT_PASSIVE, p.onMessage(600, Optional.of(List.of("a"))));
	}
	@Test void unrelatedMessageNotSuppressed() {
		HomesFetchPolicy p = new HomesFetchPolicy();
		p.armed(0);
		assertEquals(HomesFetchPolicy.Decision.IGNORE, p.onMessage(100, Optional.empty()));
		assertTrue(p.isArmed(200));
		assertEquals(HomesFetchPolicy.Decision.ACCEPT_AND_SUPPRESS, p.onMessage(300, Optional.of(List.of("a"))));
	}
	@Test void windowExpires() {
		HomesFetchPolicy p = new HomesFetchPolicy();
		p.armed(0);
		assertFalse(p.isArmed(3_001));
		assertEquals(HomesFetchPolicy.Decision.ACCEPT_PASSIVE, p.onMessage(3_001, Optional.of(List.of("a"))));
	}
	@Test void forcedFetchIgnoresStalenessButKeepsMinInterval() {
		HomesFetchPolicy p = new HomesFetchPolicy();
		assertTrue(p.shouldForceFetch(0));
		p.armed(0);
		assertFalse(p.shouldForceFetch(29_999));
		assertTrue(p.shouldForceFetch(30_000));
	}
	@Test void nonUserInitiatedNeverFetches() {
		HomesFetchPolicy p = new HomesFetchPolicy();
		for (boolean force : new boolean[] {false, true})
			for (boolean stale : new boolean[] {false, true})
				for (long now : new long[] {0, 30_000, 1_000_000})
					assertFalse(p.mayFetch(now, false, force, stale), "force=" + force + " stale=" + stale + " now=" + now);
	}
	@Test void userOpenFetchesOnlyWhenStaleAndNotRecent() {
		HomesFetchPolicy p = new HomesFetchPolicy();
		assertFalse(p.mayFetch(0, true, false, false));
		assertTrue(p.mayFetch(0, true, false, true));
		p.armed(0);
		assertFalse(p.mayFetch(29_999, true, false, true));
		assertTrue(p.mayFetch(30_000, true, false, true));
	}
	@Test void userRefreshIgnoresStalenessButKeepsMinInterval() {
		HomesFetchPolicy p = new HomesFetchPolicy();
		assertTrue(p.mayFetch(0, true, true, false));
		p.armed(0);
		assertFalse(p.mayFetch(29_999, true, true, false));
		assertTrue(p.mayFetch(30_000, true, true, false));
	}
}
