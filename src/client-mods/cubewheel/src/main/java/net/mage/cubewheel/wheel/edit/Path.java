package net.mage.cubewheel.wheel.edit;

import java.util.ArrayList;
import java.util.List;

/**
 * Identifies one node of the wheel tree: the steps from the top-level list down to it. The first step is an index
 * into the top-level list; each later step picks either child {@code i} of the node so far or arc entry {@code i}.
 */
public record Path(List<Step> steps) {
	/** One step down: arc entry {@code index} of the node so far when {@code arc}, else its child {@code index}. */
	public record Step(boolean arc, int index) {}

	public Path {
		steps = List.copyOf(steps);
	}

	/** The top-level node at {@code index}. */
	public static Path top(int index) {
		return new Path(List.of(new Step(false, index)));
	}

	/** This node's child {@code index}. */
	public Path child(int index) {
		return then(new Step(false, index));
	}

	/** This node's arc entry {@code index}. */
	public Path arcEntry(int index) {
		return then(new Step(true, index));
	}

	private Path then(Step step) {
		List<Step> s = new ArrayList<>(steps);
		s.add(step);
		return new Path(s);
	}

	/** Nesting level: 0 for a top-level node. */
	public int depth() {
		return steps.size() - 1;
	}

	/** The final step (a path always has at least one). */
	public Step last() {
		return steps.get(steps.size() - 1);
	}

	/** The path of the node holding this one (as a child or arc entry); null at the top level. */
	public Path parent() {
		return steps.size() <= 1 ? null : new Path(steps.subList(0, steps.size() - 1));
	}

	/** This path with its final step replaced. */
	public Path withLast(Step step) {
		List<Step> s = new ArrayList<>(steps.subList(0, steps.size() - 1));
		s.add(step);
		return new Path(s);
	}
}
