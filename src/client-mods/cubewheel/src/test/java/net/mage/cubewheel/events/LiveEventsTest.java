package net.mage.cubewheel.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Events that show themselves while they run: the Mana Pond's boss bar "Mana Pond (Spawn) 120/256" (captured
 * 2026-10-07; the pond runs ~5m20s and the bar goes when it ends).
 */
class LiveEventsTest {
	@Test void theManaPondBarMeansThePondIsRunning() {
		Map<String, LiveEvents.Running> r = LiveEvents.running(List.of("Mana Pond (Spawn) 120/256"));
		assertEquals(Map.of("Mana Pond", new LiveEvents.Running("Mana Pond", 120, 256)), r);
	}

	@Test void theBarIsMatchedLooselyAndTheRestIgnored() {
		assertEquals(256, LiveEvents.running(List.of("§bMANA POND §7(Spawn) §f0 / 256")).get("Mana Pond").max());
		assertEquals(5, LiveEvents.running(List.of("Mana Pond 5/256")).get("Mana Pond").progress());
		assertTrue(LiveEvents.running(List.of("Mana Golem Boss", "Mana Pondering 1/2", "Pond 3/4")).isEmpty());
		assertTrue(LiveEvents.running(List.of("Mana Pond (Spawn)")).isEmpty()); // no count: not a progress bar
		assertTrue(LiveEvents.running(List.of()).isEmpty());
		assertTrue(LiveEvents.running(null).isEmpty());
		assertTrue(LiveEvents.running(Arrays.asList((String) null, "")).isEmpty());
	}

	@Test void theFirstMatchingBarWins() {
		Map<String, LiveEvents.Running> r = LiveEvents.running(List.of("Boss 1/2", "Mana Pond (Spawn) 7/256", "Mana Pond 9/256"));
		assertEquals(1, r.size());
		assertEquals(7, r.get("Mana Pond").progress());
	}

	@Test void theRowReadsNowAndTheCount() {
		assertEquals("NOW 120/256", LiveEvents.nowText(new LiveEvents.Running("Mana Pond", 120, 256)));
		assertEquals("NOW 0/256", LiveEvents.nowText(new LiveEvents.Running("Mana Pond", 0, 256)));
	}
}
