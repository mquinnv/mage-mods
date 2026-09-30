package com.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrackerSourcesTest {
	@Test void matchesFirst() {
		Map<String, String> m = new LinkedHashMap<>(); m.put("jobs", "(?i)jobs"); m.put("pquests", "(?i)quest"); m.put("bad", "(");
		assertEquals(Optional.of("jobs"), TrackerSources.match("Your Jobs", m));
		assertEquals(Optional.of("pquests"), TrackerSources.match("Party Quests (1/2)", m));
		assertEquals(Optional.empty(), TrackerSources.match("Chest", m));
	}
}
