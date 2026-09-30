package com.mage.cubewheel.boosters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mage.cubewheel.boosters.BoosterParser.Kind;
import com.mage.cubewheel.boosters.BoosterParser.Message;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoosterStoreTest {
	@TempDir Path dir;

	private static Message received(double x, String type, long ms) {
		return new Message(Kind.RECEIVED, x, type, ms);
	}

	@Test void receivedBoostersAreListedSoonestEndingFirst() {
		BoosterStore s = new BoosterStore(dir.resolve("b.json"));
		assertTrue(s.apply(received(2, "Sell", 30 * 60_000L), 1_000));
		assertTrue(s.apply(received(3, "Mob Head", 10 * 60_000L), 1_000));
		List<BoosterStore.Booster> a = s.active(1_000);
		assertEquals(2, a.size());
		assertEquals("3x Mob Head", a.get(0).label());
		assertEquals("2x Sell", a.get(1).label());
		assertEquals(1_000 + 30 * 60_000L, a.get(1).endsAt());
	}

	@Test void expiredBoostersDisappear() {
		BoosterStore s = new BoosterStore(dir.resolve("b.json"));
		s.apply(received(2, "Sell", 60_000), 0);
		assertEquals(1, s.active(59_999).size());
		assertTrue(s.active(60_000).isEmpty());
	}

	@Test void extendedSetsTheNewRemainingTime() {
		BoosterStore s = new BoosterStore(dir.resolve("b.json"));
		s.apply(received(2, "Sell", 5 * 60_000L), 0);
		assertTrue(s.apply(new Message(Kind.EXTENDED, 2, "sell", 35 * 60_000L), 60_000));
		assertEquals(60_000 + 35 * 60_000L, s.active(60_000).get(0).endsAt());
		assertEquals(1, s.active(60_000).size(), "same booster, keyed case-insensitively");
	}

	@Test void extendedWithoutAPriorReceiveStillTracks() {
		BoosterStore s = new BoosterStore(dir.resolve("b.json"));
		s.apply(new Message(Kind.EXTENDED, 2, "XP", 10_000), 0);
		assertEquals("2x XP", s.active(0).get(0).label());
	}

	@Test void receivingAgainNeverShortensAnActiveBooster() {
		BoosterStore s = new BoosterStore(dir.resolve("b.json"));
		s.apply(received(2, "Sell", 30 * 60_000L), 0);
		s.apply(received(2, "Sell", 5 * 60_000L), 60_000);
		assertEquals(30 * 60_000L, s.active(60_000).get(0).endsAt());
	}

	@Test void differentMultipliersAreSeparateBoosters() {
		BoosterStore s = new BoosterStore(dir.resolve("b.json"));
		s.apply(received(2, "Sell", 60_000), 0);
		s.apply(received(1.5, "Sell", 60_000), 0);
		assertEquals(2, s.active(0).size());
		assertTrue(s.active(0).stream().anyMatch(b -> b.label().equals("1.5x Sell")));
	}

	@Test void endedRemoves() {
		BoosterStore s = new BoosterStore(dir.resolve("b.json"));
		s.apply(received(2, "Sell", 60_000), 0);
		assertTrue(s.apply(new Message(Kind.ENDED, 2, "Sell", 0), 1_000));
		assertTrue(s.active(1_000).isEmpty());
		assertFalse(s.apply(new Message(Kind.ENDED, 2, "Sell", 0), 1_000));
	}

	@Test void persistsAbsoluteEndTimesAcrossRestarts() throws Exception {
		Path f = dir.resolve("b.json");
		BoosterStore s = new BoosterStore(f);
		s.apply(received(2, "Sell", 30 * 60_000L), 1_000);
		s.apply(received(2, "XP", 60_000), 1_000);
		s.save();
		BoosterStore t = new BoosterStore(f);
		t.load(1_000 + 2 * 60_000L); // two minutes later: the XP booster has run out
		List<BoosterStore.Booster> a = t.active(1_000 + 2 * 60_000L);
		assertEquals(1, a.size());
		assertEquals("2x Sell", a.get(0).label());
		assertEquals(1_000 + 30 * 60_000L, a.get(0).endsAt());
	}

	@Test void corruptOrMissingFileLoadsEmpty() throws Exception {
		Path f = dir.resolve("b.json");
		BoosterStore s = new BoosterStore(f);
		s.load(0);
		assertTrue(s.active(0).isEmpty());
		Files.writeString(f, "{not json");
		s.load(0);
		assertTrue(s.active(0).isEmpty());
		Files.writeString(f, "[{\"type\":null,\"multiplier\":2,\"endsAt\":999999}]");
		s.load(0);
		assertTrue(s.active(0).isEmpty());
	}
}
