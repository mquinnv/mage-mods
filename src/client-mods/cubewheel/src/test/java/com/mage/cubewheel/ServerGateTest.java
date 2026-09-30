package com.mage.cubewheel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ServerGateTest {
	@Test
	void matching() {
		List<String> h = List.of("manacube.com", "manacube.net");
		assertTrue(ServerGate.matches("play.manacube.com", h));
		assertTrue(ServerGate.matches("PLAY.MANACUBE.COM:25565", h));
		assertTrue(ServerGate.matches("manacube.net", h));
		assertFalse(ServerGate.matches("notmanacube.com", h));
		assertFalse(ServerGate.matches("mage.example.org", h));
		assertFalse(ServerGate.matches(null, h));
	}

	@Test
	void nullOrEmptyHostsNeverMatch() {
		assertFalse(ServerGate.matches("play.manacube.com", null));
		assertFalse(ServerGate.matches("play.manacube.com", List.of()));
		assertFalse(ServerGate.matches("", List.of("manacube.com")));
	}
}
