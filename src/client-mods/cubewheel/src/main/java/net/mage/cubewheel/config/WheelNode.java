package net.mage.cubewheel.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One entry of the wheel: a command leaf, a ring of children, a dynamic list source, or a dynamic slice (a
 * single entry whose label and command are live, e.g. "boss").
 */
public final class WheelNode {
	/** Dynamic sources that fill a ring with generated entries. */
	public static final Set<String> RING_SOURCES = Set.of("homes", "vaults");
	/** Dynamic sources that are one live slice (label/command from a provider), never a ring. */
	public static final Set<String> SLICE_SOURCES = Set.of("boss");

	public String label;
	public String icon;
	public String command;
	public List<WheelNode> children;
	public String dynamic;

	public boolean isLeaf() { return command != null; }

	public boolean isRing() { return children != null && dynamic == null && command == null; }

	public boolean isDynamic() { return dynamic != null; }

	/** A live single slice ({@link #SLICE_SOURCES}): activating it never opens a ring. */
	public boolean isSlice() { return dynamic != null && SLICE_SOURCES.contains(dynamic); }

	/** A live single slice such as {@code {"label": "Boss event", "dynamic": "boss"}}. */
	public static WheelNode slice(String label, String icon, String source) {
		WheelNode n = new WheelNode();
		n.label = label;
		n.icon = icon;
		n.dynamic = source;
		return n;
	}

	public static WheelNode leaf(String label, String icon, String command) {
		WheelNode n = new WheelNode();
		n.label = label;
		n.icon = icon;
		n.command = command;
		return n;
	}

	public static WheelNode ring(String label, String icon, WheelNode... children) {
		WheelNode n = new WheelNode();
		n.label = label;
		n.icon = icon;
		n.children = new ArrayList<>(List.of(children));
		return n;
	}

	public static WheelNode dynamic(String label, String icon, String source, WheelNode... extras) {
		WheelNode n = new WheelNode();
		n.label = label;
		n.icon = icon;
		n.dynamic = source;
		n.children = new ArrayList<>(List.of(extras));
		return n;
	}
}
