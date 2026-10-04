package net.mage.cubewheel.charms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class VaultLineTest {
	@Test void showsFreeSlotsPerVault() {
		// 30 used of 45 -> 15 free; 45 used -> full; vault 4 never opened.
		assertEquals("PV 15·45·full·—", CharmsPanel.vaultLine(4, Map.of(1, 30, 2, 0, 3, 45)));
		assertEquals("", CharmsPanel.vaultLine(0, Map.of()));
	}
}
