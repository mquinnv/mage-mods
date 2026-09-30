package com.mage.cubewheel.sidebar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SidebarParserTest {
	/** ManaCube Survival sidebar as seen 2026-09-30 (icons are custom-font glyphs; lines from team prefix/suffix). */
	static final List<String> MANACUBE = List.of(
			"",
			" Money: $2.89M",
			" Souls: 18,975",
			" Mana: 132,276",
			" Cubits: 13.04",
			" Skills: Lvl 1851",
			" ",
			"play.manacube.com");

	@Test void parsesManaCubeSidebar() {
		Map<String, Double> v = SidebarParser.parse(MANACUBE);
		assertEquals(2_890_000, v.get("Money"), 1e-6);
		assertEquals(18_975, v.get("Souls"), 1e-9);
		assertEquals(132_276, v.get("Mana"), 1e-9);
		assertEquals(13.04, v.get("Cubits"), 1e-9);
		assertEquals(1851, v.get("Skills"), 1e-9);
		assertEquals(5, v.size());
	}

	@Test void keepsLineOrder() {
		assertEquals(List.of("Money", "Souls", "Mana", "Cubits", "Skills"),
				List.copyOf(SidebarParser.parse(MANACUBE).keySet()));
	}

	@Test void stripsLegacyFormattingAndGlyphs() {
		Map<String, Double> v = SidebarParser.parse(List.of("§6⛃ §eMoney§7: §a$1.5k§r", "§a§r Souls:§f 12"));
		assertEquals(1500, v.get("Money"), 1e-9);
		assertEquals(12, v.get("Souls"), 1e-9);
	}

	@Test void suffixesAndMultiWordKeys() {
		Map<String, Double> v = SidebarParser.parse(List.of("Bank Balance: 3.2B", "Tokens: 7K", "Gems: 1.25m"));
		assertEquals(3.2e9, v.get("Bank Balance"), 1e-3);
		assertEquals(7000, v.get("Tokens"), 1e-9);
		assertEquals(1_250_000, v.get("Gems"), 1e-6);
	}

	@Test void ignoresLinesWithoutKeyOrNumber() {
		Map<String, Double> v = SidebarParser.parse(Arrays.asList(null, "", "Rank: Mage", "play.manacube.com",
				"12:30", ": 5", "Party: none"));
		assertTrue(v.isEmpty(), v.toString());
	}

	@Test void clockTimesAreNotNumbers() {
		Map<String, Double> v = SidebarParser.parse(List.of("Time: 12:30", "Clock: 7:05pm", "Mana: 5"));
		assertEquals(Map.of("Mana", 5.0), v);
		assertFalse(SidebarParser.parseValue("12:30").isPresent());
	}

	@Test void firstOccurrenceWins() {
		Map<String, Double> v = SidebarParser.parse(List.of("Mana: 1", "Mana: 2"));
		assertEquals(1, v.get("Mana"), 1e-9);
	}

	@Test void nullInputIsEmpty() {
		assertTrue(SidebarParser.parse(null).isEmpty());
	}

	@Test void parseValueHandlesShapes() {
		assertEquals(2_890_000, SidebarParser.parseValue("$2.89M").getAsDouble(), 1e-6);
		assertEquals(1851, SidebarParser.parseValue("Lvl 1851").getAsDouble(), 1e-9);
		assertEquals(18_975, SidebarParser.parseValue("18,975").getAsDouble(), 1e-9);
		assertFalse(SidebarParser.parseValue("none").isPresent());
		assertFalse(SidebarParser.parseValue(null).isPresent());
	}
}
