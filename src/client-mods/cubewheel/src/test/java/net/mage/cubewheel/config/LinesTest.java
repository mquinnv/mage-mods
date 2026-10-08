package net.mage.cubewheel.config;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LinesTest {
	@Test
	void mapRoundTripKeepsOrder() {
		Map<String, String> m = DefaultConfig.bossWarps();
		List<String> problems = new ArrayList<>();
		Map<String, String> back = Lines.parseMap(Lines.mapLines(m), problems, "a -> b");
		assertEquals(new ArrayList<>(m.entrySet()), new ArrayList<>(back.entrySet()));
		assertTrue(problems.isEmpty());
	}

	@Test
	void mapKeyMayContainArrow() {
		List<String> problems = new ArrayList<>();
		Map<String, String> m = Lines.parseMap(List.of("(?i)a->b -> /warp x"), problems, "a -> b");
		assertEquals("/warp x", m.get("(?i)a->b"));
		assertTrue(problems.isEmpty());
	}

	@Test
	void mapBlankLinesSkippedSilently() {
		List<String> problems = new ArrayList<>();
		Map<String, String> m = Lines.parseMap(List.of("", "   ", "a -> b"), problems, "k -> v");
		assertEquals(Map.of("a", "b"), m);
		assertTrue(problems.isEmpty());
	}

	@Test
	void mapBadLinesEachAddOneProblem() {
		for (String bad : List.of("foo", " -> v", "k -> ")) {
			List<String> problems = new ArrayList<>();
			Map<String, String> m = Lines.parseMap(List.of(bad), problems, "source id -> regex");
			assertTrue(m.isEmpty(), bad);
			assertEquals(1, problems.size(), bad);
			assertEquals("\"" + bad.trim() + "\" ignored: expected  source id -> regex", problems.get(0));
		}
	}

	@Test
	void mapRepeatedKeyKeepsLater() {
		List<String> problems = new ArrayList<>();
		Map<String, String> m = Lines.parseMap(List.of("a -> 1", "b -> 2", "a -> 3"), problems, "k -> v");
		assertEquals("3", m.get("a"));
		assertTrue(problems.isEmpty());
		assertInstanceOf(LinkedHashMap.class, m);
	}

	@Test
	void eventRoundTripWithDefaults() {
		List<CubeWheelConfig.EventDef> defs = DefaultConfig.events();
		List<String> problems = new ArrayList<>();
		List<CubeWheelConfig.EventDef> back = Lines.parseEvents(Lines.eventLines(defs), problems);
		assertTrue(problems.isEmpty());
		assertEquals(defs.size(), back.size());
		for (int i = 0; i < defs.size(); i++) same(defs.get(i), back.get(i));
	}

	@Test
	void eventFormats() {
		CubeWheelConfig.EventDef tz = new CubeWheelConfig.EventDef("Boss", "every 2h from 01:30");
		tz.timezone = "Europe/London";
		CubeWheelConfig.EventDef off = new CubeWheelConfig.EventDef("LPS", "at 07:58");
		off.enabled = false;
		CubeWheelConfig.EventDef both = new CubeWheelConfig.EventDef("Golden Knight", "every 3h from 00:15");
		both.timezone = "America/New_York";
		both.enabled = false;
		List<CubeWheelConfig.EventDef> defs = List.of(tz, off, both);
		List<String> lines = Lines.eventLines(defs);
		assertEquals(List.of("Boss | every 2h from 01:30 | Europe/London", "LPS | at 07:58 | off",
				"Golden Knight | every 3h from 00:15 | America/New_York | off"), lines);
		List<String> problems = new ArrayList<>();
		List<CubeWheelConfig.EventDef> back = Lines.parseEvents(lines, problems);
		assertTrue(problems.isEmpty());
		for (int i = 0; i < defs.size(); i++) same(defs.get(i), back.get(i));
	}

	/** A pinned event (Mana Pond, Michael 2026-10-07) keeps its pin through the settings editor. */
	@Test
	void eventPinnedRoundTrips() {
		CubeWheelConfig.EventDef pond = new CubeWheelConfig.EventDef("Mana Pond", "at 03:00, 06:00");
		pond.pinned = true;
		CubeWheelConfig.EventDef all = new CubeWheelConfig.EventDef("X", "at 01:00");
		all.timezone = "UTC";
		all.enabled = false;
		all.pinned = true;
		List<String> lines = Lines.eventLines(List.of(pond, all));
		assertEquals(List.of("Mana Pond | at 03:00, 06:00 | pinned", "X | at 01:00 | UTC | off | pinned"), lines);
		List<String> problems = new ArrayList<>();
		List<CubeWheelConfig.EventDef> back = Lines.parseEvents(lines, problems);
		assertTrue(problems.isEmpty());
		same(pond, back.get(0));
		same(all, back.get(1));
		// Any order, any case; a plain line is not pinned.
		List<CubeWheelConfig.EventDef> r = Lines.parseEvents(List.of("A | at 01:00 | PINNED | off", "B | at 02:00"), problems);
		assertTrue(problems.isEmpty());
		assertTrue(r.get(0).pinned);
		assertFalse(r.get(0).enabled);
		assertFalse(r.get(1).pinned);
	}

	@Test
	void eventOffIsCaseInsensitiveAndBlankLinesSkipped() {
		List<String> problems = new ArrayList<>();
		List<CubeWheelConfig.EventDef> r = Lines.parseEvents(List.of("", "LPS | at 07:58 | OFF"), problems);
		assertTrue(problems.isEmpty());
		assertEquals(1, r.size());
		assertFalse(r.get(0).enabled);
		assertNull(r.get(0).timezone);
	}

	@Test
	void eventBadLinesEachAddOneProblem() {
		for (String bad : List.of("KOTH", "KOTH | ", " | at 00:30", "A | at 01:00 | Europe/London | Asia/Tokyo")) {
			List<String> problems = new ArrayList<>();
			assertTrue(Lines.parseEvents(List.of(bad), problems).isEmpty(), bad);
			assertEquals(1, problems.size(), bad);
		}
	}

	private static void same(CubeWheelConfig.EventDef a, CubeWheelConfig.EventDef b) {
		assertEquals(a.name, b.name);
		assertEquals(a.when, b.when);
		assertEquals(a.timezone, b.timezone);
		assertEquals(a.enabled, b.enabled);
		assertEquals(a.pinned, b.pinned);
	}
}
