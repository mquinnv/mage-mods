package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class UnclaimedDeathsTest {
	/** The captured magic-book kill: the Mooshroom dies with no hit, its loot line follows 74 ms later. */
	@Test void aLootLineRightAfterADeathNamesThatMob() {
		UnclaimedDeaths<String> d = new UnclaimedDeaths<>();
		d.died("Mooshroom", 1_000);
		assertEquals(Optional.of("Mooshroom"), d.claim(1_074));
		assertEquals(Optional.empty(), d.claim(1_074)); // once
	}

	@Test void onlyInsideTheWindow() {
		UnclaimedDeaths<String> d = new UnclaimedDeaths<>();
		d.died("early", 1_000);
		assertEquals(Optional.empty(), d.claim(1_000 + UnclaimedDeaths.BEFORE_MS + 1));
		assertEquals(Optional.of("early"), d.claim(1_000 + UnclaimedDeaths.BEFORE_MS));
		d.died("late", 5_000);
		assertEquals(Optional.empty(), d.claim(5_000 - UnclaimedDeaths.AFTER_MS - 1));
		assertEquals(Optional.of("late"), d.claim(5_000 - UnclaimedDeaths.AFTER_MS));
	}

	/** Two kills in a row: each line takes the nearest death, so neither is counted as the other. */
	@Test void eachLineTakesTheNearestDeath() {
		UnclaimedDeaths<String> d = new UnclaimedDeaths<>();
		d.died("first", 1_000);
		d.died("second", 2_000);
		assertEquals(Optional.of("second"), d.claim(2_070));
		assertEquals(Optional.of("first"), d.claim(1_070));
	}

	@Test void expiresAndIsBounded() {
		UnclaimedDeaths<String> d = new UnclaimedDeaths<>();
		d.died("old", 0);
		d.expire(UnclaimedDeaths.BEFORE_MS + LootKills.AFTER_MS + UnclaimedDeaths.AFTER_MS + 1);
		assertEquals(Optional.empty(), d.claim(100));
		for (int i = 0; i < UnclaimedDeaths.MAX + 5; i++) d.died("m" + i, 10_000);
		int n = 0;
		while (d.claim(10_000).isPresent()) n++;
		assertEquals(UnclaimedDeaths.MAX, n);
		d.died(null, 10_000);
		assertEquals(Optional.empty(), d.claim(10_000));
	}
}
