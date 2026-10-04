package net.mage.cubewheel.wheel.edit;

import java.util.List;
import net.mage.cubewheel.ClientActions;
import net.mage.cubewheel.config.WheelNode;

/**
 * Says whether saving would keep a wheel node as it is. The rules mirror {@code ConfigNormalizer.normalizeNodes}
 * and {@code normalizeArc} and must stay in step with them (a test runs both over the same nodes). Looks at the
 * node itself, not what is inside its ring: a bad child is reported on the child's own row.
 */
public final class WheelNodeCheck {
	private WheelNodeCheck() {}

	/**
	 * Null when saving keeps {@code node} exactly as it is, otherwise a short sentence on what saving would drop or
	 * change. {@code inArc} when the node is an entry of a slice's arc.
	 */
	public static String problem(WheelNode node, boolean inArc) {
		return problem(node, inArc, WheelNode.MAX_OUTER);
	}

	/** {@code command} as saving writes it: trimmed, with a leading "/" unless it is a client action; null if blank. */
	public static String normalizedCommand(String command) {
		if (command == null || command.isBlank()) return null;
		String c = command.trim();
		return c.startsWith("/") || ClientActions.is(c) ? c : "/" + c;
	}

	private static boolean hasNull(List<WheelNode> list) {
		for (WheelNode n : list) {
			if (n == null) return true;
		}
		return false;
	}

	private static String problem(WheelNode n, boolean inArc, int outerLeft) {
		if (n == null) return "Empty entry: it would be dropped when saved.";
		if (n.label == null || n.label.isBlank()) return "No label: this entry would be dropped when saved.";
		boolean hasCommand = n.command != null && !n.command.isBlank();
		boolean blankCommand = n.command != null && !hasCommand;
		if (n.dynamic != null && WheelNode.SLICE_SOURCES.contains(n.dynamic)) {
			if (hasCommand) return "A live slice can't have a command: it would be dropped when saved.";
			if (n.command != null) return "A live slice has no command: the blank one would be cleared.";
			if (n.children != null) return "A live slice has no entries inside: they would be dropped when saved.";
		} else if (n.dynamic != null && WheelNode.RING_SOURCES.contains(n.dynamic)) {
			if (hasCommand) return "A live ring can't have a command: it would be dropped when saved.";
			if (blankCommand) return "A live ring has no command: the blank one would be cleared.";
			if (n.children == null) return "A live ring needs a list of extras: an empty one would be added.";
			if (hasNull(n.children)) return "An empty entry inside would be dropped when saved.";
		} else if (n.dynamic != null) {
			return "Unknown live source \"" + n.dynamic + "\": this entry would be dropped when saved.";
		} else if (hasCommand && n.children != null) {
			return "Both a command and entries inside: this entry would be dropped when saved.";
		} else if (!hasCommand && n.children == null) {
			return "No command and no entries inside: this entry would be dropped when saved.";
		} else if (hasCommand) {
			String saved = normalizedCommand(n.command);
			if (!saved.equals(n.command)) return "The command would be saved as \"" + saved + "\".";
		} else {
			if (blankCommand) return "A ring has no command: the blank one would be cleared.";
			if (hasNull(n.children)) return "An empty entry inside would be dropped when saved.";
		}
		if (n.outer != null) {
			if (outerLeft <= 0) return "Too many outer tiers: the extra ones would be dropped when saved.";
			String p = problem(n.outer, false, outerLeft - 1);
			if (p != null) return "Outer tier: " + p;
		}
		if (n.arc != null) {
			if (n.arc.isEmpty()) return "An empty arc would be removed when saved.";
			if (hasNull(n.arc)) return "An empty arc entry would be dropped when saved.";
		}
		if (inArc) {
			if (!hasCommand || n.dynamic != null) return "Only plain commands can sit in an arc: this would be dropped when saved.";
			if (n.outer != null || n.arc != null) return "An arc entry has no outer tier or arc of its own: they would be dropped when saved.";
		}
		return null;
	}
}
