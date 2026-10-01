package net.mage.cubewheel.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * The version 3 wheel upgrade: the old wheel is replaced by the new default, and every command leaf whose
 * command the new default does not have anywhere (i.e. one you added yourself) is kept, appended under
 * More › Custom. Best effort: rings you made are flattened to their leaves. Pure: no Minecraft/Fabric imports.
 */
public final class WheelUpgrade {
	/** Entries per ring; more moved leaves are split into "Custom 1", "Custom 2", ... inside Custom. */
	static final int RING_MAX = 8;
	public static final String CUSTOM = "Custom";

	/** The upgraded wheel and the leaves moved into More › Custom, as "Label (/command)". */
	public record Result(List<WheelNode> wheel, List<String> moved) {}

	private WheelUpgrade() {}

	public static Result upgrade(List<WheelNode> old) {
		List<WheelNode> wheel = DefaultConfig.wheel();
		Set<String> known = new HashSet<>();
		for (WheelNode n : walk(wheel)) if (n.command != null) known.add(key(n.command));
		Map<String, WheelNode> keep = new LinkedHashMap<>();
		if (old != null) {
			for (WheelNode n : walk(old)) {
				if (n.command == null || n.command.isBlank() || n.label == null || n.label.isBlank()) continue;
				String k = key(n.command);
				if (known.contains(k) || keep.containsKey(k)) continue;
				keep.put(k, WheelNode.leaf(n.label, n.icon, n.command));
			}
		}
		List<String> moved = new ArrayList<>();
		for (WheelNode n : keep.values()) moved.add(n.label + " (" + n.command.trim() + ")");
		if (!keep.isEmpty()) more(wheel).children.add(custom(new ArrayList<>(keep.values())));
		return new Result(wheel, moved);
	}

	private static WheelNode custom(List<WheelNode> leaves) {
		WheelNode ring = WheelNode.ring(CUSTOM, "minecraft:name_tag");
		if (leaves.size() <= RING_MAX) {
			ring.children.addAll(leaves);
			return ring;
		}
		for (int i = 0; i < leaves.size(); i += RING_MAX) {
			WheelNode part = WheelNode.ring(CUSTOM + " " + (i / RING_MAX + 1), "minecraft:name_tag");
			part.children.addAll(leaves.subList(i, Math.min(leaves.size(), i + RING_MAX)));
			ring.children.add(part);
		}
		return ring;
	}

	private static WheelNode more(List<WheelNode> wheel) {
		for (WheelNode n : wheel) if (DefaultConfig.MORE.equals(n.label) && n.isRing()) return n;
		WheelNode more = WheelNode.ring(DefaultConfig.MORE, "minecraft:chest");
		wheel.add(more);
		return more;
	}

	/** Every node of the tree: rings' and dynamic nodes' children and slices' outer tiers included. */
	static List<WheelNode> walk(List<WheelNode> roots) {
		List<WheelNode> out = new ArrayList<>();
		Deque<WheelNode> q = new ArrayDeque<>();
		for (WheelNode n : roots) if (n != null) q.add(n);
		while (!q.isEmpty()) {
			WheelNode n = q.poll();
			out.add(n);
			if (n.children != null) for (WheelNode c : n.children) if (c != null) q.add(c);
			if (n.outer != null) q.add(n.outer); // a slice's outer tiers count as part of the tree
			if (n.arc != null) for (WheelNode a : n.arc) if (a != null) q.add(a);
		}
		return out;
	}

	/** "/Warp  Crops " and "warp crops" are the same command. */
	static String key(String command) {
		String c = command.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		return c.startsWith("/") ? c.substring(1) : c;
	}
}
