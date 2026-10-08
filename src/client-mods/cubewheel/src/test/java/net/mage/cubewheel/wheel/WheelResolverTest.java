package net.mage.cubewheel.wheel;

import net.mage.cubewheel.config.WheelNode;
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
		assertEquals("PV 2", WheelResolver.children(v, 3, List.of()).get(1).label);
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
	/** 2026-10-08: "i need a quick way to get to /home armor": the slice's own entries are pins, first, never doubled. */
	@Test void pinnedHomesComeFirstAndAFetchedTwinIsDropped() {
		WheelNode h = WheelNode.dynamic("H", "minecraft:red_bed", "homes",
				WheelNode.leaf("Armor", "minecraft:iron_chestplate", "/home armor"));
		List<WheelNode> out = WheelResolver.children(h, 3, List.of("home", "Armor", "farm"));
		assertEquals(List.of("Armor", "farm", "home"), out.stream().map(n -> n.label).toList());
		assertEquals("minecraft:iron_chestplate", out.get(0).icon);
		assertEquals(List.of("Armor"), WheelResolver.children(h, 3, List.of()).stream().map(n -> n.label).toList());
	}
	@Test void leafHasNoChildren() { assertTrue(WheelResolver.children(WheelNode.leaf("a", null, "/a"), 3, List.of()).isEmpty()); }
	@Test void homesWithNullChildrenDoesNotNpe() {
		WheelNode h = WheelNode.dynamic("H", null, "homes");
		h.children = null; // Simulate JSON deserialization without defaults
		List<WheelNode> out = WheelResolver.children(h, 3, List.of("home1", "home2"));
		assertEquals(List.of("home1", "home2"), out.stream().map(n -> n.label).toList());
	}
	@Test void vaultsWithNullChildrenDoesNotNpe() {
		WheelNode v = WheelNode.dynamic("V", null, "vaults");
		v.children = null; // Simulate JSON deserialization without defaults
		List<WheelNode> out = WheelResolver.children(v, 2, List.of());
		assertEquals(List.of("/pv 1", "/pv 2"), out.stream().map(n -> n.command).toList());
	}
	@Test void unknownDynamicReturnsOnlyExtras() {
		WheelNode u = WheelNode.dynamic("U", null, "unknown_source", WheelNode.leaf("extra", null, "/ex"));
		List<WheelNode> out = WheelResolver.children(u, 3, List.of("home1"));
		assertEquals(List.of("/ex"), out.stream().map(n -> n.command).toList());
	}
}
