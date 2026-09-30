package net.mage.cubewheel.boosters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.boosters.BoosterParser.Kind;
import net.mage.cubewheel.boosters.BoosterParser.Message;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BoosterParserTest {
	private static Message parse(String s) {
		Optional<Message> m = BoosterParser.parse(s);
		assertTrue(m.isPresent(), "should parse: " + s);
		return m.get();
	}

	private static void none(String s) {
		assertTrue(BoosterParser.parse(s).isEmpty(), "must not parse: " + s);
	}

	@Test void received() {
		Message m = parse("You have received a 2x Sell Boost for 30m");
		assertEquals(Kind.RECEIVED, m.kind());
		assertEquals(2.0, m.multiplier());
		assertEquals("Sell", m.type());
		assertEquals(30 * 60_000L, m.durationMs());
	}

	@Test void receivedVariants() {
		assertEquals("Mob Head", parse("You have received a 3x Mob Head Boost for 1h 30m.").type());
		assertEquals(90 * 60_000L, parse("You have received a 3x Mob Head Boost for 1h 30m.").durationMs());
		Message mc = parse("you have received an 1.5x mcMMO Boost Booster for 15 minutes!");
		assertEquals("mcMMO", mc.type());
		assertEquals(1.5, mc.multiplier());
		assertEquals(15 * 60_000L, mc.durationMs());
		assertEquals("XP", parse("You received a 2x XP Booster for 45m").type());
		assertEquals("XP", parse("§aYou have received a §e2x XP Boost §afor §e10m").type());
		assertEquals("Sell", parse("[Boosters] You have received a 2x Sell Boost for 30m").type());
		assertEquals("SELL", parse("  You have received a 2X SELL BOOST for 30M").type());
	}

	@Test void extended() {
		Message m = parse("Your 2x Sell Boost has been extended from 5m 10s to 35m 10s");
		assertEquals(Kind.EXTENDED, m.kind());
		assertEquals("Sell", m.type());
		assertEquals(35 * 60_000L + 10_000L, m.durationMs());
		Message b = parse("Your 2x Mob Head Boost booster has been extended from 1m 0s to 31m 0s.");
		assertEquals("Mob Head", b.type());
		assertEquals(31 * 60_000L, b.durationMs());
	}

	@Test void ended() {
		Message m = parse("Your 2x Sell Boost has expired!");
		assertEquals(Kind.ENDED, m.kind());
		assertEquals("Sell", m.type());
		assertEquals(Kind.ENDED, parse("Your 2x XP Booster has ended.").kind());
	}

	@Test void playerChatNeverMatches() {
		// Real ManaCube player-chat shapes from capture (2026-09-30).
		none("§r  [Stardrop] KingBee: You have received a 2x Sell Boost for 30m");
		none("§r Orkagamertag: You have received a 2x Sell Boost for 30m");
		none("§r 235553: Your 2x Sell Boost has been extended from 1m to 31m");
		none("§r  ❤❤❤ Doobe: Your 2x Sell Boost has expired");
		none("Survival [SHOUT] MrDoobe: You have received a 2x Sell Boost for 30m");
		none("[Stardrop] KingBee: You have received a 2x Sell Boost for 30m");
		none("KingBee: You have received a 2x Sell Boost for 30m");
		none("*Maxiecat3030: You have received a 2x Sell Boost for 30m");
		assertTrue(BoosterParser.isPlayerChat("§r 䟿 [Pond✩Master] Matvii_UA: alr"));
		assertTrue(BoosterParser.isPlayerChat("Skyblock [SHOUT] imdabestmaan: 140c cf"));
	}

	@Test void unrelatedMessages() {
		none("[Morend] An Anomaly has occured: Jump Boost VIII");
		none("[Morend] The Jump Boost VIII Anomaly has ended");
		none("You have received a 2x Sell Boost for a while");
		none("You have received a Sell Boost for 30m");
		none("You have received 1 Spawnerpackage1 Crate Key(s). RIGHT-CLICK TO USE");
		none("");
		none(null);
		none("Your 2x Sell Boost has been extended from 5m to forever");
	}
}
