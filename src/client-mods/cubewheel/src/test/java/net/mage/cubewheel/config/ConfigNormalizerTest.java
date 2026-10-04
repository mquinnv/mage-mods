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

	@Test void clientActionCommandsAreLeftAloneWhilePlainOnesGetASlash() {
		WheelNode action = new WheelNode();
		action.label = "Move panels";
		action.command = "  cubewheel:arrange ";
		WheelNode plain = new WheelNode();
		plain.label = "Crops";
		plain.command = "warp crops";
		List<WheelNode> out = ConfigNormalizer.normalizeNodes(new ArrayList<>(List.of(action, plain)));
		assertEquals("cubewheel:arrange", out.get(0).command);
		assertEquals("/warp crops", out.get(1).command);
	}

	@Test void clientActionArcEntriesAreLeftAloneToo() {
		WheelNode slice = new WheelNode();
		slice.label = "More";
		slice.command = "warp x";
		WheelNode arc = new WheelNode();
		arc.label = "Move panels";
		arc.command = "CubeWheel:Arrange";
		slice.arc = new ArrayList<>(List.of(arc));
		List<WheelNode> out = ConfigNormalizer.normalizeNodes(new ArrayList<>(List.of(slice)));
		assertEquals("CubeWheel:Arrange", out.get(0).arc.get(0).command);
	}
}
