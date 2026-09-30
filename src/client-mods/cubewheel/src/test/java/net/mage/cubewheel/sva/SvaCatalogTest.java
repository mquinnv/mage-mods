package net.mage.cubewheel.sva;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SvaCatalogTest {
	static String resource(String name) throws IOException {
		try (InputStream in = SvaCatalogTest.class.getResourceAsStream("/sva/" + name)) {
			assertNotNull(in, name);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	static SvaCatalog sample() throws IOException {
		return SvaCatalog.parse(resource("catalog-sample.json"));
	}

	@Test void parsesAndDedupesByItemType() throws IOException {
		SvaCatalog c = sample();
		assertEquals(15, c.all().size()); // 17 entries, carrotsword-20 and easter-boots listed twice
		Sva v = c.byType("valhallahelmet");
		assertEquals("VALHALLA HELMET", v.plainName());
		assertEquals(107, v.circulation());
		assertEquals("carved_pumpkin", v.itemId());
		assertEquals("manalabs:crates/viking/viking_helmet", v.itemModel());
		assertEquals(16, v.lore().size());
		assertEquals("Valhalla Helmet", v.plainLore().get(1));
		assertEquals(5, v.enchants().size());
	}

	@Test void allIsSortedByPlainName() throws IOException {
		List<Sva> all = sample().all();
		for (int i = 1; i < all.size(); i++) {
			assertTrue(all.get(i - 1).sortKey().compareTo(all.get(i).sortKey()) <= 0);
		}
	}

	static final String VALHALLA_MODEL = "manalabs:crates/viking/viking_helmet";
	static final String SCYTHE_MODEL = "manalabs:gear/sword/water_scythe";

	@Test void lookupByHoverName() throws IOException {
		SvaCatalog c = sample();
		SvaCatalog.Match m = c.match("Valhalla Helmet", "carved_pumpkin", VALHALLA_MODEL, List::of);
		assertEquals(List.of("valhallahelmet"), m.types());
		assertFalse(m.nameOnly());
		assertTrue(c.match("Diamond Sword", "diamond_sword", "minecraft:diamond_sword", List::of).isEmpty());
		assertFalse(c.match("Diamond Sword", "diamond_sword", "minecraft:diamond_sword", List::of).nameOnly());
		assertTrue(c.match(null, (String) null, null, List::of).isEmpty());
	}

	@Test void ambiguousNamesNarrowByMaterialThenModelThenLore() throws IOException {
		SvaCatalog c = sample();
		// same name, different material (these SVAs have no item model)
		assertEquals(List.of("carrotsword-20"), c.match("CARROT SWORD", "golden_carrot", "minecraft:golden_carrot", List::of).types());
		assertEquals(List.of("carrotsword2-20"), c.match("CARROT SWORD", "diamond_sword", "minecraft:diamond_sword", List::of).types());
		// same name, material and model: lore decides
		assertEquals(List.of("enhanced-oceanscythe"), c.match("DEEP OCEAN SCYTHE", "diamond_sword", SCYTHE_MODEL,
				() -> List.of("Unbreakable", "Enhanced Sahuagin Scythe", "", "+15% Extra Damage to Monsters")).types());
		assertEquals(List.of("oceanscythe"), c.match("DEEP OCEAN SCYTHE", "diamond_sword", SCYTHE_MODEL,
				() -> List.of("Sahuagin Scythe", "Upgrade this item with /enhance")).types());
		// nothing to tell them apart: both stay
		SvaCatalog.Match both = c.match("Deep Ocean Scythe", "diamond_sword", SCYTHE_MODEL, List::of);
		assertEquals(2, both.types().size());
		assertTrue(both.ambiguous());
	}

	@Test void renamedItemWithWrongMaterialIsNotVouchedFor() throws IOException {
		SvaCatalog c = sample();
		// a diamond sword renamed "Valhalla Helmet" in an anvil: the only candidate, but the wrong item
		SvaCatalog.Match m = c.match("Valhalla Helmet", "diamond_sword", "minecraft:diamond_sword", List::of);
		assertTrue(m.isEmpty());
		assertTrue(m.nameOnly());
		// unknown material is not good enough either
		assertTrue(c.match("Valhalla Helmet", null, VALHALLA_MODEL, List::of).nameOnly());
	}

	@Test void rightMaterialButVanillaModelIsNotVouchedFor() throws IOException {
		SvaCatalog c = sample();
		// a renamed carved pumpkin: right material, but the SVA has a resource-pack model and this has the vanilla one
		SvaCatalog.Match m = c.match("VALHALLA HELMET", "carved_pumpkin", "minecraft:carved_pumpkin", List::of);
		assertTrue(m.isEmpty());
		assertTrue(m.nameOnly());
		assertTrue(c.match("VALHALLA HELMET", "carved_pumpkin", null, List::of).nameOnly());
	}

	@Test void rightMaterialAndModelIsVouchedFor() throws IOException {
		SvaCatalog c = sample();
		assertEquals(List.of("valhallahelmet"), c.match("valhalla helmet", "carved_pumpkin", VALHALLA_MODEL, List::of).types());
		// an SVA without an item model only needs the material
		assertEquals(List.of("easter-boots"), c.match("Easter Bunny Boots", "diamond_boots", "minecraft:diamond_boots", List::of).types());
		assertTrue(c.match("Easter Bunny Boots", "golden_boots", "minecraft:golden_boots", List::of).nameOnly());
	}

	@Test void itemPartsAreOnlyComputedAfterTheNameMatches() throws IOException {
		SvaCatalog c = sample();
		int[] calls = {0};
		java.util.function.Supplier<String> counted = () -> {
			calls[0]++;
			return "x";
		};
		assertTrue(c.match("Just A Stick", counted, counted, List::of).isEmpty());
		assertEquals(0, calls[0]);
	}

	@Test void searchMatchesNameAndLoreWords() throws IOException {
		SvaCatalog c = sample();
		assertTrue(c.byType("valhallahelmet").matches("valhalla"));
		assertTrue(c.byType("valhallahelmet").matches("souls monsters"));
		assertTrue(c.byType("valhallahelmet").matches(""));
		assertTrue(!c.byType("valhallahelmet").matches("souls dolphins"));
	}

	@Test void ahQueryDropsDecorativeSymbols() throws IOException {
		SvaCatalog c = sample();
		assertEquals("SUN SWORD", c.byType("sunsword").ahQuery());
		assertEquals("WIZARDS BOOK", c.byType("wizardbook").ahQuery());
		assertEquals("MARSHMELLO'S HELMET", c.byType("marshmellohelmet").ahQuery());
	}

	@Test void toleratesMissingAndNullFields() {
		SvaCatalog c = SvaCatalog.parse("[{\"itemType\":\"x\",\"displayName\":\"&lX\"},null,{\"itemType\":\"y\"},"
				+ "{\"itemType\":\"z\",\"displayName\":\"Z\",\"lore\":null,\"enchants\":null,\"circulation\":null,\"leatherColor\":1234}]");
		assertEquals(2, c.all().size());
		assertEquals(0, c.byType("x").circulation());
		assertEquals(1234, c.byType("z").leatherColor());
		assertEquals(-1, c.byType("x").leatherColor());
	}

	@Test void rejectsNonArrays() {
		assertThrows(IllegalArgumentException.class, () -> SvaCatalog.parse("{\"status\":\"500\"}"));
		assertThrows(IllegalArgumentException.class, () -> SvaCatalog.parse("not json"));
		assertThrows(IllegalArgumentException.class, () -> SvaCatalog.parse(null));
	}

	/** The full saved API response, when present on this machine (not committed: ~716 KB). */
	@Test void fullSampleIfAvailable() throws IOException {
		Path full = Path.of(System.getProperty("cubewheel.svaSample",
				"/private/tmp/claude-501/-Users-michael-Projects-mage-mods/f24fc584-1490-439f-be4a-7a6cf618cb92/scratchpad/manamod-study/svas-survival.json"));
		assumeTrue(Files.isRegularFile(full));
		SvaCatalog c = SvaCatalog.parse(Files.readString(full));
		assertEquals(1226, c.all().size());
		assertEquals(List.of("valhallahelmet"), c.match("VALHALLA HELMET", "carved_pumpkin", VALHALLA_MODEL, List::of).types());
	}
}
