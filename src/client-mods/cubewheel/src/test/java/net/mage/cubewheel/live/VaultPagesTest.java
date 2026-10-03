package net.mage.cubewheel.live;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.mage.cubewheel.tracker.ContainerScanner.ItemView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultPagesTest {
	@TempDir Path dir;

	/** The bottom row of a real /pv page (2026-10-01): pages 1-4 open, 5-7 locked. */
	private static List<ItemView> realRow() {
		List<ItemView> l = new ArrayList<>();
		l.add(new ItemView(1, "minecraft:diamond", "Diamond", List.of()));
		l.add(new ItemView(45, "minecraft:ender_pearl", "Previous Page", List.of()));
		l.add(new ItemView(46, "minecraft:lime_wool", "Page 1", List.of("Click me to go to page 1")));
		for (int p = 2; p <= 4; p++)
			l.add(new ItemView(45 + p, "minecraft:ender_pearl", "Page " + p, List.of("Click me to go to page " + p)));
		for (int p = 5; p <= 7; p++)
			l.add(new ItemView(45 + p, "minecraft:lime_wool", "§cPage " + p,
					List.of("You have not unlocked page " + p, "Unlock more pages in /cubitshop")));
		l.add(new ItemView(53, "minecraft:ender_pearl", "Next Page", List.of()));
		return l;
	}

	@Test void countsTheUnlockedPages() {
		assertEquals(OptionalInt.of(4), VaultPages.unlocked(realRow()));
	}

	@Test void aMenuWithoutPageButtonsSaysNothing() {
		assertTrue(VaultPages.unlocked(List.of(new ItemView(0, "minecraft:stone", "Page turner", List.of()))).isEmpty());
		assertTrue(VaultPages.unlocked(List.of()).isEmpty());
	}

	@Test void countsArePerPlayerAndPersist() {
		VaultPages s = new VaultPages(dir.resolve("vaults.json"));
		assertNull(s.count("Qualan"));
		assertTrue(s.set("Qualan", 4));
		assertFalse(s.set("qualan", 4));
		assertTrue(s.save());
		VaultPages t = new VaultPages(dir.resolve("vaults.json"));
		t.load();
		assertEquals(4, t.count("QUALAN"));
		assertNull(t.count("VetaPhoenix79"));
	}
}
