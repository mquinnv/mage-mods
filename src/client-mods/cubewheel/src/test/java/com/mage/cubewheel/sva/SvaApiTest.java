package com.mage.cubewheel.sva;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Owned parsing, Mojang lookups, filtering, the request budget and the disk cache. */
class SvaApiTest {
	@TempDir Path dir;

	@Test void ownedCountsByItemType() throws IOException {
		Map<String, Integer> owned = SvaApi.parseOwned(SvaCatalogTest.resource("owned-sample.json"));
		assertEquals(5, owned.size());
		assertEquals(2, owned.get("iridium-scythe"));
		assertEquals(1, owned.get("samuraikatana"));
		assertEquals(Map.of(), SvaApi.parseOwned("[]"));
		assertThrows(IllegalArgumentException.class, () -> SvaApi.parseOwned("{\"status\":\"400 Bad Request\"}"));
	}

	@Test void ownedJoinsTheCatalogByItemType() throws IOException {
		SvaCatalog c = SvaCatalogTest.sample();
		for (String type : SvaApi.parseOwned(SvaCatalogTest.resource("owned-sample.json")).keySet()) {
			assertTrue(c.byType(type) != null, type);
		}
	}

	@Test void urlsUseDashedUuids() {
		assertEquals("https://api.manacube.com/api/svas/survival", SvaApi.catalogUrl());
		assertEquals("https://api.manacube.com/api/svas/survival/7f0e9af0-4a31-4e69-8377-14bfd7ed5a90",
				SvaApi.ownedUrl("7f0e9af04a314e69837714bfd7ed5a90"));
		assertEquals("https://api.manacube.com/api/svas/survival/7f0e9af0-4a31-4e69-8377-14bfd7ed5a90",
				SvaApi.ownedUrl("7F0E9AF0-4A31-4E69-8377-14BFD7ED5A90"));
		assertThrows(IllegalArgumentException.class, () -> SvaApi.ownedUrl("../../x"));
		assertEquals("https://api.mojang.com/users/profiles/minecraft/Qualan", SvaApi.mojangUrl("Qualan"));
	}

	@Test void playerNamesAreValidated() {
		assertTrue(SvaApi.validName("Qualan"));
		assertTrue(SvaApi.validName("a_b"));
		assertFalse(SvaApi.validName("ab"));
		assertFalse(SvaApi.validName("has space"));
		assertFalse(SvaApi.validName("x/../y"));
		assertFalse(SvaApi.validName(null));
	}

	@Test void mojangProfile() {
		assertEquals(Optional.of("7f0e9af0-4a31-4e69-8377-14bfd7ed5a90"),
				SvaApi.parseMojang("{\n  \"id\" : \"7f0e9af04a314e69837714bfd7ed5a90\",\n  \"name\" : \"Qualan\"\n}"));
		assertEquals(Optional.empty(), SvaApi.parseMojang(""));
		assertEquals(Optional.empty(), SvaApi.parseMojang("{\"errorMessage\":\"Couldn't find any profile\"}"));
		assertEquals(Optional.empty(), SvaApi.parseMojang("{\"id\":\"nothex\"}"));
	}

	@Test void filterByQueryAndOwnership() throws IOException {
		SvaCatalog c = SvaCatalogTest.sample();
		Set<String> mine = Set.of("samuraikatana", "soulcrossbow");
		assertEquals(15, SvaFilter.apply(c.all(), "", SvaFilter.Show.ALL, mine, SvaFilter.Sort.NAME).size());
		assertEquals(List.of("samuraikatana", "soulcrossbow"),
				types(SvaFilter.apply(c.all(), "", SvaFilter.Show.OWNED, mine, SvaFilter.Sort.NAME)));
		assertEquals(13, SvaFilter.apply(c.all(), "", SvaFilter.Show.NOT_OWNED, mine, SvaFilter.Sort.NAME).size());
		assertEquals(List.of("soulcrossbow"),
				types(SvaFilter.apply(c.all(), "crossbow", SvaFilter.Show.OWNED, mine, SvaFilter.Sort.NAME)));
		List<Sva> rare = SvaFilter.apply(c.all(), "", SvaFilter.Show.ALL, mine, SvaFilter.Sort.RAREST);
		assertEquals("wizardbook", rare.get(0).itemType()); // circulation 0
		List<Sva> common = SvaFilter.apply(c.all(), "", SvaFilter.Show.ALL, mine, SvaFilter.Sort.MOST_COMMON);
		assertEquals("oceanscythe", common.get(0).itemType()); // 342
	}

	@Test void compareMarks() {
		Set<String> mine = Set.of("a", "b");
		Set<String> theirs = Set.of("b", "c");
		assertEquals(SvaFilter.Mark.MINE, SvaFilter.mark("a", mine, theirs));
		assertEquals(SvaFilter.Mark.BOTH, SvaFilter.mark("b", mine, theirs));
		assertEquals(SvaFilter.Mark.THEIRS, SvaFilter.mark("c", mine, theirs));
		assertEquals(SvaFilter.Mark.NONE, SvaFilter.mark("d", mine, theirs));
		assertEquals(SvaFilter.Mark.MINE, SvaFilter.mark("a", mine, null));
	}

	@Test void budgetAllowsTwentyPerMinute() {
		RateBudget b = new RateBudget(20, 60_000);
		for (int i = 0; i < 20; i++) assertTrue(b.tryAcquire(1_000 + i));
		assertFalse(b.tryAcquire(2_000));
		assertEquals(20, b.used(2_000));
		assertTrue(b.tryAcquire(61_000)); // the first slot has aged out
		assertFalse(b.tryAcquire(61_000));
	}

	@Test void cacheRoundTripsAndSurvivesCorruption() throws IOException {
		SvaCache cache = new SvaCache(dir.resolve("cubewheel-cache"));
		assertTrue(cache.readCatalog().isEmpty());
		cache.writeCatalog("[1]", 5_000);
		cache.writeOwned("7f0e9af0-4a31-4e69-8377-14bfd7ed5a90", "[]", 6_000);
		SvaCache again = new SvaCache(dir.resolve("cubewheel-cache"));
		assertEquals("[1]", again.readCatalog().orElseThrow().body());
		assertEquals(5_000, again.readCatalog().orElseThrow().fetchedAt());
		assertEquals(6_000, again.readOwned("7F0E9AF0-4A31-4E69-8377-14BFD7ED5A90").orElseThrow().fetchedAt());
		assertTrue(again.readOwned("00000000-0000-0000-0000-000000000000").isEmpty());
		Files.writeString(dir.resolve("cubewheel-cache").resolve("index.json"), "{broken");
		assertTrue(new SvaCache(dir.resolve("cubewheel-cache")).readCatalog().isEmpty());
	}

	private static List<String> types(List<Sva> list) {
		return list.stream().map(Sva::itemType).toList();
	}
}
