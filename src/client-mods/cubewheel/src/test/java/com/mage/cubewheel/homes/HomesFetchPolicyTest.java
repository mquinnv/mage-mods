package com.mage.cubewheel.homes;

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
}
