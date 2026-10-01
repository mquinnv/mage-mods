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
	/**
	 * An optional second entry on the same slice, drawn just outside the ring: point further from the centre to
	 * take it (e.g. Crops on the ring, Spawners outside it). An outer entry may have its own outer, further out
	 * still (Sell menu → hand → all), up to {@link #MAX_OUTER} beyond the ring.
	 */
	public WheelNode outer;
	/** Tiers beyond the ring a slice may have. */
	public static final int MAX_OUTER = 2;

	/** This node with {@code outer} as its outer entry; returns this for chaining. */
	public WheelNode withOuter(WheelNode outer) {
		this.outer = outer;
		return this;
	}

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
