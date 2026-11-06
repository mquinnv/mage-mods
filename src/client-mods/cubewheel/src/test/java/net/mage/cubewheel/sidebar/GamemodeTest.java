package net.mage.cubewheel.sidebar;

import static net.mage.cubewheel.sidebar.Gamemode.OTHER;
import static net.mage.cubewheel.sidebar.Gamemode.SKYBLOCK;
import static net.mage.cubewheel.sidebar.Gamemode.SURVIVAL;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GamemodeTest {
	private static final String SV = "(?i)survival";
	private static final String SB = "(?i)skyblock";

	@Test void survivalTitle() {
		assertEquals(SURVIVAL, Gamemode.detect("   SURVIVAL", SV, SB));
	}

	@Test void skyblockTitleWithFormatting() {
		assertEquals(SKYBLOCK, Gamemode.detect("§6§lSKYBLOCK", SV, SB));
	}

	@Test void noMatchIsOther() {
		assertEquals(OTHER, Gamemode.detect(null, SV, SB));
		assertEquals(OTHER, Gamemode.detect("", SV, SB));
		assertEquals(OTHER, Gamemode.detect("PARKOUR", SV, SB));
	}

	@Test void survivalIsCheckedFirst() {
		assertEquals(SURVIVAL, Gamemode.detect("SURVIVAL SKYBLOCK", SV, SB));
	}

	/** Michael 2026-11-06: unlike SidebarGate's blank=always, a blank Skyblock pattern must not claim every server. */
	@Test void blankSkyblockPatternNeverDetectsSkyblock() {
		assertEquals(OTHER, Gamemode.detect("SKYBLOCK", SV, ""));
		assertEquals(OTHER, Gamemode.detect("SKYBLOCK", SV, null));
	}

	@Test void blankSurvivalPatternKeepsGateOffMeaning() {
		assertEquals(SURVIVAL, Gamemode.detect("anything", "", SB));
	}

	/** The memo remembers one entry; alternating titles must still each get their own right answer. */
	@Test void alternatingTitlesStayCorrectThroughTheMemo() {
		for (int i = 0; i < 3; i++) {
			assertEquals(SURVIVAL, Gamemode.detect("SURVIVAL", SV, SB));
			assertEquals(SKYBLOCK, Gamemode.detect("SKYBLOCK", SV, SB));
			assertEquals(OTHER, Gamemode.detect("PARKOUR", SV, SB));
			assertEquals(OTHER, Gamemode.detect(null, SV, SB));
		}
		assertEquals(SKYBLOCK, Gamemode.detect("SKYBLOCK", SV, SB));
		assertEquals(OTHER, Gamemode.detect("SKYBLOCK", SV, ""), "different pattern, different answer");
	}

	@Test void ids() {
		assertEquals("survival", SURVIVAL.id());
		assertEquals("skyblock", SKYBLOCK.id());
		assertEquals("other", OTHER.id());
	}
}
