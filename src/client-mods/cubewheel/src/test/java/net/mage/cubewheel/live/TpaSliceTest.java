package net.mage.cubewheel.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.wheel.SliceViews;
import org.junit.jupiter.api.Test;

class TpaSliceTest {
	@Test void idleIsInert() {
		SliceViews.View v = new TpaSlice().view(0);
		assertEquals("TPA", v.label());
		assertTrue(v.inert());
	}

	@Test void requestMakesAnAcceptSliceUntilItTimesOut() {
		TpaSlice t = new TpaSlice();
		assertTrue(t.onChat("§cVetaPhoenix79§6 has requested to teleport to you.", 1_000));
		SliceViews.View v = t.view(2_000);
		assertEquals("Accept VetaPhoenix79", v.label());
		assertEquals("/tpaccept", v.command());
		assertFalse(v.inert());
		assertEquals("VetaPhoenix79", t.pending(1_000 + TpaSlice.DEFAULT_TIMEOUT_MS));
		assertNull(t.pending(1_001 + TpaSlice.DEFAULT_TIMEOUT_MS));
	}

	@Test void hereRequestsAndTimeoutLines() {
		TpaSlice t = new TpaSlice();
		t.onChat("VetaPhoenix79 has requested that you teleport to them.", 0);
		t.onChat("This request will timeout after 60 seconds.", 0);
		assertEquals("VetaPhoenix79", t.pending(60_000));
		assertNull(t.pending(60_001));
	}

	@Test void acceptedDeniedOrTimedOutEndsIt() {
		for (String end : new String[] {"Teleport request accepted.", "Teleport request denied.", "Teleport request has timed out."}) {
			TpaSlice t = new TpaSlice();
			t.onChat("VetaPhoenix79 has requested to teleport to you.", 0);
			assertTrue(t.onChat(end, 10), end);
			assertNull(t.pending(20), end);
		}
	}

	@Test void ourOwnOutgoingRequestAndChatterAreIgnored() {
		TpaSlice t = new TpaSlice();
		assertFalse(t.onChat("Request sent to VetaPhoenix79.", 0));
		assertFalse(t.onChat("To cancel this request, type /tpacancel.", 0));
		assertFalse(t.onChat("§r [Champion] Bob: Steve has requested to teleport to you lol", 0));
		assertNull(t.pending(0));
	}
}
