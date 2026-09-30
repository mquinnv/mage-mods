package net.mage.cubewheel.sva;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.config.ConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SvaConfigTest {
	@TempDir Path dir;

	@Test void missingOrNullSectionGetsDefaults() throws Exception {
		Path f = dir.resolve("cubewheel.json");
		Files.writeString(f, "{\"svas\":null}");
		ConfigStore store = new ConfigStore(f);
		assertNull(store.reload());
		assertTrue(store.current().svas.enabled);
		assertTrue(store.current().svas.tooltip);
		Files.writeString(f, "{\"svas\":{\"tooltip\":false}}");
		assertNull(store.reload());
		assertTrue(store.current().svas.enabled);
		assertFalse(store.current().svas.tooltip);
	}
}
