package com.mage.cubewheel.wheel;

import com.mage.cubewheel.config.WheelNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WheelResolverTest {
	@Test void ringReturnsChildren() {
		WheelNode r = WheelNode.ring("S", null, WheelNode.leaf("a", null, "/a"));
		assertEquals(List.of("/a"), WheelResolver.children(r, 3, List.of()).stream().map(n -> n.command).toList());
	}
	@Test void vaultsExpandThenExtras() {
		WheelNode v = WheelNode.dynamic("V", null, "vaults", WheelNode.leaf("EC", null, "/ec"));
		assertEquals(List.of("/pv 1", "/pv 2", "/pv 3", "/ec"), WheelResolver.children(v, 3, List.of()).stream().map(n -> n.command).toList());
		assertEquals("Vault 2", WheelResolver.children(v, 3, List.of()).get(1).label);
	}
	@Test void zeroVaultsOnlyExtras() {
		WheelNode v = WheelNode.dynamic("V", null, "vaults");
		assertTrue(WheelResolver.children(v, 0, List.of()).isEmpty());
	}
	@Test void homesSortedCaseInsensitive() {
		WheelNode h = WheelNode.dynamic("H", "minecraft:red_bed", "homes");
		List<WheelNode> out = WheelResolver.children(h, 3, List.of("farm", "Base", "nether"));
		assertEquals(List.of("Base", "farm", "nether"), out.stream().map(n -> n.label).toList());
		assertEquals("/home Base", out.get(0).command);
		assertEquals("minecraft:red_bed", out.get(0).icon);
	}
	@Test void leafHasNoChildren() { assertTrue(WheelResolver.children(WheelNode.leaf("a", null, "/a"), 3, List.of()).isEmpty()); }
}
