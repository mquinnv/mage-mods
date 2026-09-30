package net.mage.cubewheel.homes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HomesCacheTest {
	@TempDir Path dir;
	@Test void putGetStaleAndPersist() {
		HomesCache c = new HomesCache(dir.resolve("h.json"));
		assertTrue(c.isStale("play.manacube.com", 1000, 300_000));
		c.put("Play.ManaCube.com", List.of("a", "b"), 1000);
		assertFalse(c.isStale("play.manacube.com", 2000, 300_000));
		assertTrue(c.isStale("play.manacube.com", 1000 + 300_001, 300_000));
		c.add("play.manacube.com", "c"); c.add("play.manacube.com", "a"); c.remove("play.manacube.com", "b");
		assertEquals(List.of("a", "c"), c.get("play.manacube.com"));
		c.save();
		HomesCache d = new HomesCache(dir.resolve("h.json")); d.load();
		assertEquals(List.of("a", "c"), d.get("play.manacube.com"));
		assertEquals(1000, d.fetchedAt("play.manacube.com"));
	}
	@Test void loadMissingOrCorruptIsEmpty() throws Exception {
		Path f = dir.resolve("h.json"); Files.writeString(f, "{nope");
		HomesCache c = new HomesCache(f); c.load();
		assertEquals(List.of(), c.get("x"));
	}
	@Test void loadToleratesNullHomesAndNullEntries() throws Exception {
		Path f = dir.resolve("h.json"); Files.writeString(f, "{\"a\":{\"fetchedAt\":5},\"b\":null}");
		HomesCache c = new HomesCache(f); c.load();
		assertEquals(List.of(), c.get("a"));
		assertEquals(List.of(), c.get("b"));
		c.add("a", "x");
		assertEquals(List.of("x"), c.get("a"));
	}
}
