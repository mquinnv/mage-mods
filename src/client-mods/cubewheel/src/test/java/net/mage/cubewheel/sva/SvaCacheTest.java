package net.mage.cubewheel.sva;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import net.mage.cubewheel.sidebar.Gamemode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** One cache directory per gamemode: Survival keeps the old layout, Skyblock gets a subdirectory. */
class SvaCacheTest {
	private static final String UUID = "7f0e9af0-4a31-4e69-8377-14bfd7ed5a90";
	@TempDir Path dir;

	@Test void gamemodesNeverShareCatalogOrIndex() {
		SvaCache survival = new SvaCache(dir, Gamemode.SURVIVAL);
		SvaCache skyblock = new SvaCache(dir, Gamemode.SKYBLOCK);
		survival.writeCatalog("[\"survival\"]", 1_000);
		assertTrue(skyblock.readCatalog().isEmpty());
		skyblock.writeCatalog("[\"skyblock\"]", 2_000);
		skyblock.writeOwned(UUID, "[\"mine\"]", 3_000);
		assertEquals("[\"survival\"]", survival.readCatalog().orElseThrow().body());
		assertEquals(1_000, survival.readCatalog().orElseThrow().fetchedAt());
		assertEquals("[\"skyblock\"]", skyblock.readCatalog().orElseThrow().body());
		assertEquals(2_000, skyblock.readCatalog().orElseThrow().fetchedAt());
		assertTrue(survival.readOwned(UUID).isEmpty());
		assertEquals("[\"mine\"]", skyblock.readOwned(UUID).orElseThrow().body());
	}

	@Test void fileLayout() {
		new SvaCache(dir, Gamemode.SURVIVAL).writeCatalog("[]", 1);
		new SvaCache(dir, Gamemode.SKYBLOCK).writeCatalog("[]", 1);
		assertTrue(Files.isRegularFile(dir.resolve("svas-survival.json")));
		assertTrue(Files.isRegularFile(dir.resolve("skyblock").resolve("svas-skyblock.json")));
		assertTrue(Files.isRegularFile(dir.resolve("skyblock").resolve("index.json")));
	}
}
