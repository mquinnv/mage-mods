package net.mage.cubewheel.config;

import java.util.ArrayList;
import java.util.List;

/** One entry of the wheel: a command leaf, a ring of children, or a dynamic list source. */
public final class WheelNode {
	public String label;
	public String icon;
	public String command;
	public List<WheelNode> children;
	public String dynamic;

	public boolean isLeaf() { return command != null; }

	public boolean isRing() { return children != null && dynamic == null && command == null; }

	public boolean isDynamic() { return dynamic != null; }

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
