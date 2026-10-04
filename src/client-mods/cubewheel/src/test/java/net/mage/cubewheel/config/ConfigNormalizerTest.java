package net.mage.cubewheel.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigNormalizerTest {
	@Test void aNegativeListThresholdIsClampedWithoutAnyFile() {
		CubeWheelConfig c = DefaultConfig.create();
		c.listThreshold = -5;
		List<String> warnings = new ArrayList<>();
		ConfigNormalizer.normalize(c, warnings);
		assertEquals(3, c.listThreshold);
		assertTrue(warnings.isEmpty());
	}

	@Test void anInvalidBossWarpRegexIsDroppedWithAWarning() {
		CubeWheelConfig c = DefaultConfig.create();
		c.events.bossWarps.clear();
		c.events.bossWarps.put("(unclosed", "/warp x");
		c.events.bossWarps.put("(?i)ok", "warp ok");
		List<String> warnings = new ArrayList<>();
		ConfigNormalizer.normalize(c, warnings);
		assertEquals(List.of("(?i)ok"), new ArrayList<>(c.events.bossWarps.keySet()));
		assertEquals("/warp ok", c.events.bossWarps.get("(?i)ok"));
		assertEquals(1, warnings.size());
		assertTrue(warnings.get(0).contains("(unclosed"));
	}
}
