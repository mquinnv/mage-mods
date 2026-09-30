package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorldResolverTest {
	static final List<String> SPECIAL = List.of("wolfhaven", "tangleroots", "sandara", "icehaven", "morend", "burninglands");

	@Test void dimensionNamedAfterTheWorld() {
		WorldInfo w = WorldResolver.resolve("minecraft:wolfhaven", List.of(), SPECIAL);
		assertTrue(w.known());
		assertTrue(w.special());
		assertTrue(w.tokens().contains("wolfhaven"));
	}

	@Test void sidebarWorldLine() {
		WorldInfo w = WorldResolver.resolve("minecraft:overworld", List.of("§7Money: $2.89M", "§6☀ §fWorld: §eSandara"), SPECIAL);
		assertTrue(w.tokens().contains("sandara"));
		assertTrue(w.special());
		WorldInfo t = WorldResolver.resolve(null, List.of("World: Tangleroots"), SPECIAL);
		assertTrue(t.known());
		assertTrue(t.special());
		assertTrue(t.tokens().contains("tangleroot"));
	}

	@Test void overworldIsKnownNotSpecial() {
		WorldInfo w = WorldResolver.resolve("minecraft:overworld", List.of("Skills: Lvl 1851"), SPECIAL);
		assertTrue(w.known());
		assertFalse(w.special());
		assertTrue(w.tokens().contains("overworld"));
		assertFalse(w.tokens().contains("minecraft"));
	}

	@Test void customNamespaceAndPathParts() {
		WorldInfo w = WorldResolver.resolve("manacube:world_sandara", null, SPECIAL);
		assertTrue(w.tokens().contains("sandara"));
		assertTrue(w.tokens().contains("manacube"));
		assertTrue(w.special());
	}

	@Test void multiWordWorldNamesAreNormalised() {
		for (WorldInfo w : List.of(
				WorldResolver.resolve("minecraft:burning_lands", List.of(), SPECIAL),
				WorldResolver.resolve("minecraft:burninglands", List.of(), SPECIAL),
				WorldResolver.resolve("minecraft:overworld", List.of("World: Burning Lands"), SPECIAL))) {
			assertTrue(w.tokens().contains("burningland"), w.toString());
			assertTrue(w.special(), w.toString());
		}
		// A spaced special-world entry matches an unspaced world too.
		assertTrue(WorldResolver.resolve("minecraft:burninglands", null, List.of("Burning Lands")).special());
		assertEquals("burningland", WorldResolver.key("Burning_Lands"));
	}

	@Test void emptyInputIsUnknown() {
		assertEquals(WorldInfo.UNKNOWN, WorldResolver.resolve(null, null, SPECIAL));
		assertEquals(WorldInfo.UNKNOWN, WorldResolver.resolve("", List.of("Money: 5"), null));
	}
}
