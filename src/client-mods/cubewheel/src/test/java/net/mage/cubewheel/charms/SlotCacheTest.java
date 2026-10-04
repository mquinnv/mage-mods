package net.mage.cubewheel.charms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The Charms panel parses a slot's lore only when the slot's item object changes. */
class SlotCacheTest {
	@Test void sameObjectIsParsedOnce() {
		SlotCache<String> cache = new SlotCache<>(36);
		AtomicInteger parses = new AtomicInteger();
		Object amulet = new Object();
		for (int frame = 0; frame < 5; frame++) assertEquals("amulet", cache.get(35, amulet, () -> { parses.incrementAndGet(); return "amulet"; }));
		assertEquals(1, parses.get());
	}

	@Test void anEqualButNewObjectIsParsedAgain() {
		SlotCache<String> cache = new SlotCache<>(36);
		AtomicInteger parses = new AtomicInteger();
		String a = new String("stack"), b = new String("stack"); // equal, not the same object
		cache.get(0, a, () -> "first " + parses.incrementAndGet());
		assertEquals("second 2", cache.get(0, b, () -> "second " + parses.incrementAndGet()));
		assertEquals("second 2", cache.get(0, b, () -> "third " + parses.incrementAndGet()));
	}

	@Test void slotsAreKeptApart() {
		SlotCache<String> cache = new SlotCache<>(36);
		Object item = new Object();
		assertEquals("in 3", cache.get(3, item, () -> "in 3"));
		assertEquals("in 4", cache.get(4, item, () -> "in 4"));
		assertEquals("in 3", cache.get(3, item, () -> "again"));
	}

	@Test void aFailedParseIsTriedAgain() {
		SlotCache<String> cache = new SlotCache<>(1);
		Object item = new Object();
		assertThrows(IllegalStateException.class, () -> cache.get(0, item, () -> { throw new IllegalStateException(); }));
		assertEquals("ok", cache.get(0, item, () -> "ok"));
	}
}
