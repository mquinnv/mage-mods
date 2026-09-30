package net.mage.cubewheel.sva;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyTextTest {
	@Test void stripsAmpersandAndSectionCodes() {
		assertEquals("VALHALLA HELMET", LegacyText.strip("&r&lV&lA&lL&lH&lA&lL&lL&lA &lH&lE&lL&lM&lE&lT"));
		assertEquals("☀ SUN SWORD ☀", LegacyText.strip("&r☀ &lSUN SWORD ☀"));
		assertEquals("➟ 2x Souls from Monsters", LegacyText.strip("&r§x§D§4§A§F§3§7➟ 2x Souls from Monsters"));
	}

	@Test void keepsLiteralAmpersand() {
		assertEquals("Bosses & Minibosses", LegacyText.strip("§7Bosses & Minibosses"));
		assertEquals("Fish & Chips&!", LegacyText.strip("Fish & Chips&!"));
	}

	@Test void normalizeIsCaseAndSpaceInsensitive() {
		assertEquals("season vault access ✔", LegacyText.normalize("&r§8Season Vault Access  ✔ "));
		assertEquals(LegacyText.normalize("Valhalla Helmet"), LegacyText.normalize("&r&lV&lA&lL&lH&lA&lL&lL&lA &lH&lE&lL&lM&lE&lT"));
	}

	@Test void parsesColoursAndDecorations() {
		List<LegacyText.Span> spans = LegacyText.parse("&r§f§lITEM EFFECTS: §7(While Worn)");
		assertEquals(2, spans.size());
		assertEquals("ITEM EFFECTS: ", spans.get(0).text());
		assertEquals(0xFFFFFF, spans.get(0).rgb());
		assertTrue(spans.get(0).bold());
		assertEquals("(While Worn)", spans.get(1).text());
		assertEquals(0xAAAAAA, spans.get(1).rgb());
		assertFalse(spans.get(1).bold(), "a colour code resets decorations");
	}

	@Test void parsesHexColours() {
		List<LegacyText.Span> spans = LegacyText.parse("§x§D§4§A§F§3§7➟ Regen");
		assertEquals(1, spans.size());
		assertEquals(0xD4AF37, spans.get(0).rgb());
		assertEquals(0x1CECFF, LegacyText.parse("&#1CECFFhi").get(0).rgb());
	}

	@Test void defaultColourIsMinusOneAndResetClears() {
		List<LegacyText.Span> spans = LegacyText.parse("plain §c§lred§r back");
		assertEquals(-1, spans.get(0).rgb());
		assertEquals(0xFF5555, spans.get(1).rgb());
		assertTrue(spans.get(1).bold());
		assertEquals(-1, spans.get(2).rgb());
		assertFalse(spans.get(2).bold());
	}

	@Test void nullAndTrailingMarkerAreSafe() {
		assertEquals("", LegacyText.strip(null));
		assertEquals("a§", LegacyText.strip("a§"));
		assertEquals("a&", LegacyText.strip("a&"));
		assertEquals(List.of(), LegacyText.parse(""));
	}
}
