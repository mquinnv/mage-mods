package net.mage.cubewheel.sidebar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SidebarGateTest {
	static final String DEFAULT = "(?i)survival";

	@Test void survivalSidebarTitleMatches() {
		assertTrue(SidebarGate.matches("SURVIVAL", DEFAULT));
		assertTrue(SidebarGate.matches("§e§lSURVIVAL", DEFAULT)); // formatting codes are ignored
		assertTrue(SidebarGate.matches("  ManaCube Survival  ", DEFAULT));
	}

	@Test void otherGamemodesAndHubDoNotMatch() {
		assertFalse(SidebarGate.matches("SKYBLOCK", DEFAULT));
		assertFalse(SidebarGate.matches("PARKOUR", DEFAULT));
		assertFalse(SidebarGate.matches("MANACUBE", DEFAULT));
	}

	@Test void noSidebarIsInactive() {
		assertFalse(SidebarGate.matches(null, DEFAULT));
		assertFalse(SidebarGate.matches("", DEFAULT));
		assertFalse(SidebarGate.matches("   ", DEFAULT));
	}

	@Test void blankPatternSwitchesTheGateOff() {
		assertTrue(SidebarGate.matches(null, ""));
		assertTrue(SidebarGate.matches("SKYBLOCK", null));
	}

	@Test void invalidPatternNeverMatches() {
		assertFalse(SidebarGate.matches("SURVIVAL", "(unclosed"));
	}
}
