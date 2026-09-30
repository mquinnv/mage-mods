package com.mage.cubewheel.homes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HomesParserTest {
	static HomesParser.Reply r(String t, String... clicks) { return new HomesParser.Reply(t, List.of(clicks)); }
	@Test void clickEventsWin() {
		assertEquals(Optional.of(List.of("base", "farm_2")), HomesParser.parse(r("Homes: base, farm_2", "/home base", "/home farm_2")));
	}
	@Test void clickEventsDedupAndIgnoreOthers() {
		assertEquals(Optional.of(List.of("base")), HomesParser.parse(r("Homes: base", "/home base", "/home base", "/spawn", "/home")));
	}
	@Test void textFallback() {
		assertEquals(Optional.of(List.of("base", "farm", "nether")), HomesParser.parse(r("Homes: base, farm, nether")));
	}
	@Test void textFallbackWithCountAndTrailingPeriod() {
		assertEquals(Optional.of(List.of("a", "b-1", "c")), HomesParser.parse(r("Your homes (3): a, b-1, c.")));
	}
	@Test void multiLineUsesHomesLine() {
		assertEquals(Optional.of(List.of("x")), HomesParser.parse(r("-----\nHomes: x\n-----")));
	}
	@Test void rejectsSentenceWithSpaces() { assertEquals(Optional.empty(), HomesParser.parse(r("Home: you have no homes set"))); }
	@Test void rejectsUnrelated() { assertEquals(Optional.empty(), HomesParser.parse(r("<Steve> hello: world"))); }
	@Test void rejectsChatAndPlaceholders() {
		for (String t : new String[] {"Steve: home", "Bob: nice-home", "[Home] Steve: hi"})
			assertEquals(Optional.empty(), HomesParser.parse(r(t)), t);
	}
	@Test void noneMeansZeroHomes() {
		for (String t : new String[] {"Home: none", "Homes: None.", "Your homes (0): no"})
			assertEquals(Optional.of(List.of()), HomesParser.parse(r(t)), t);
		assertEquals(Optional.empty(), HomesParser.parse(r("Homes: none of your business")));
	}
	@Test void singleClickWithoutHeaderRejected() {
		assertEquals(Optional.empty(), HomesParser.parse(r("Home 'farm' set! Click to teleport", "/home farm")));
	}
	@Test void twoClicksWithoutHeader() {
		assertEquals(Optional.of(List.of("a", "b")), HomesParser.parse(r("click one", "/home a", "/home b")));
	}
	@Test void invalidClickRejectsWholeReply() {
		assertEquals(Optional.empty(), HomesParser.parse(r("Homes: a, b", "/home a", "/home bad.name")));
	}
	@Test void headerPlusOneClick() {
		assertEquals(Optional.of(List.of("farm")), HomesParser.parse(r("Homes: farm", "/home farm")));
	}
	@Test void outgoing() {
		assertEquals(Optional.of(new HomesParser.Edit(true, "farm")), HomesParser.parseOutgoing("sethome farm"));
		assertEquals(Optional.of(new HomesParser.Edit(true, "home")), HomesParser.parseOutgoing("sethome"));
		assertEquals(Optional.of(new HomesParser.Edit(false, "farm")), HomesParser.parseOutgoing("delhome farm"));
		assertEquals(Optional.empty(), HomesParser.parseOutgoing("home farm"));
	}
	@Test void paginationClicksAreIgnored() {
		assertEquals(Optional.of(List.of("a", "b")), HomesParser.parse(r("Homes: a, b  [Next page]", "/home a", "/home b", "/homes 2")));
		assertEquals(Optional.of(List.of("a", "b")), HomesParser.parse(r("click one", "/home a", "/homes 2", "/home b")));
	}
	@Test void paginationClickAloneIsNotAHome() {
		assertEquals(Optional.empty(), HomesParser.parse(r("Next page", "/homes 2", "/homes 3")));
	}
	@Test void paginationClickWithOddArgumentDoesNotInvalidate() {
		assertEquals(Optional.of(List.of("a", "b")), HomesParser.parse(r("Homes: a, b", "/home a", "/home b", "/homes page=2")));
	}
}
