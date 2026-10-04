package net.mage.cubewheel.wheel.edit;

import java.util.ArrayList;
import java.util.List;
import net.mage.cubewheel.config.WheelNode;

/** One line of the flattened wheel tree the editor lists: where the node is, how deep, and what sort it is. */
public record Row(Path path, int depth, WheelNode node, Kind kind) {
	/** What a node is, for the editor: which buttons apply and how the row is drawn. */
	public enum Kind {
		/** A command leaf. */
		LEAF,
		/** A ring of children. */
		RING,
		/** A live ring ({@code homes}/{@code vaults}), possibly with static extras in its children. */
		LIVE_RING,
		/** A live single slice ({@code boss}/{@code tpa}). */
		LIVE_SLICE,
		/** An entry in a slice's arc. */
		ARC_ENTRY
	}

	/** The kind of {@code node}; {@code inArc} when it is an arc entry. */
	public static Kind kindOf(WheelNode node, boolean inArc) {
		if (inArc) return Kind.ARC_ENTRY;
		if (node.isSlice()) return Kind.LIVE_SLICE;
		if (node.isDynamic()) return Kind.LIVE_RING;
		if (node.command == null && node.children != null) return Kind.RING;
		return Kind.LEAF;
	}

	/**
	 * Every node depth-first: a node's arc entries right after it (one level deeper), then its children (one level
	 * deeper). Null entries are skipped but still count in the indexes.
	 */
	public static List<Row> rows(List<WheelNode> wheel) {
		List<Row> out = new ArrayList<>();
		collect(wheel, null, false, out);
		return out;
	}

	private static void collect(List<WheelNode> list, Path parent, boolean arc, List<Row> out) {
		if (list == null) return;
		for (int i = 0; i < list.size(); i++) {
			WheelNode n = list.get(i);
			if (n == null) continue;
			Path p = parent == null ? Path.top(i) : arc ? parent.arcEntry(i) : parent.child(i);
			out.add(new Row(p, p.depth(), n, kindOf(n, arc)));
			collect(n.arc, p, true, out);
			collect(n.children, p, false, out);
		}
	}
}
