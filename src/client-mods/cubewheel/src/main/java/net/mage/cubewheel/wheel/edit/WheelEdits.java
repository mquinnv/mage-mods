package net.mage.cubewheel.wheel.edit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;
import net.mage.cubewheel.config.WheelNode;
import net.mage.cubewheel.wheel.edit.Path.Step;
import net.mage.cubewheel.wheel.edit.Row.Kind;

/**
 * The wheel editor's operations, pure and without side effects: each takes the wheel and returns a changed deep copy
 * (the input is never touched) or an error. Everything an edit produces is something saving keeps as it is (see
 * {@link WheelNodeCheck}), so the editor can't build a wheel that saving would alter.
 */
public final class WheelEdits {
	private WheelEdits() {}

	private static final Gson GSON = new Gson();
	private static final java.lang.reflect.Type LIST = new TypeToken<List<WheelNode>>() {}.getType();

	/**
	 * What an edit gave back: the new wheel and the path of the node it concerns (added, moved, or the neighbour
	 * left after a delete; null if none), or an error and the unchanged wheel.
	 */
	public record Result(List<WheelNode> wheel, Path selected, String error) {
		public boolean ok() { return error == null; }
	}

	/** See {@link Row#rows(List)}. */
	public static List<Row> rows(List<WheelNode> wheel) {
		return Row.rows(wheel);
	}

	/** Inserts {@code leaf} after the node at {@code at} in the same list, or at the end of the top level if null. */
	public static Result addLeaf(List<WheelNode> wheel, Path at, WheelNode leaf) {
		return insertAfter(wheel, at, leaf);
	}

	/** Inserts an empty ring after the node at {@code at} (same list), or at the end of the top level if null. */
	public static Result addRing(List<WheelNode> wheel, Path at, String label, String icon) {
		WheelNode ring = new WheelNode();
		ring.label = label;
		ring.icon = icon;
		ring.children = new ArrayList<>();
		return insertAfter(wheel, at, ring);
	}

	/** Appends {@code leaf} to the arc of the command leaf at {@code slice}. */
	public static Result addToArc(List<WheelNode> wheel, Path slice, WheelNode leaf) {
		List<WheelNode> w = copy(wheel);
		Loc at = locate(w, slice);
		if (at == null) return fail(wheel, "That entry no longer exists.");
		if (kind(at) != Kind.LEAF) return fail(wheel, "Only a command entry can have an arc.");
		WheelNode entry = prepared(leaf);
		String problem = WheelNodeCheck.problem(entry, true);
		if (problem != null) return fail(wheel, problem);
		WheelNode owner = at.node();
		if (owner.arc == null) owner.arc = new ArrayList<>();
		owner.arc.add(entry);
		return new Result(w, slice.arcEntry(owner.arc.size() - 1), null);
	}

	/** Appends {@code node} inside the ring (or a live ring's extras) at {@code ring}. */
	public static Result addChild(List<WheelNode> wheel, Path ring, WheelNode node) {
		List<WheelNode> w = copy(wheel);
		Loc at = locate(w, ring);
		if (at == null) return fail(wheel, "That entry no longer exists.");
		Kind k = kind(at);
		if (k != Kind.RING && k != Kind.LIVE_RING) return fail(wheel, "Only a ring can hold entries.");
		WheelNode entry = prepared(node);
		String problem = WheelNodeCheck.problem(entry, false);
		if (problem != null) return fail(wheel, problem);
		WheelNode owner = at.node();
		if (owner.children == null) owner.children = new ArrayList<>();
		owner.children.add(entry);
		return new Result(w, ring.child(owner.children.size() - 1), null);
	}

	/**
	 * Changes the node at {@code at}; a null argument leaves that field as it is. {@code command} applies to leaves
	 * and arc entries, {@code asArc} to rings and live rings; anything else is refused.
	 */
	public static Result edit(List<WheelNode> wheel, Path at, String label, String icon, String command, Boolean asArc) {
		List<WheelNode> w = copy(wheel);
		Loc loc = locate(w, at);
		if (loc == null) return fail(wheel, "That entry no longer exists.");
		Kind k = kind(loc);
		WheelNode n = loc.node();
		if (command != null && k != Kind.LEAF && k != Kind.ARC_ENTRY) return fail(wheel, "Only a command entry has a command.");
		if (asArc != null && k != Kind.RING && k != Kind.LIVE_RING) return fail(wheel, "Only a ring can be shown as an arc.");
		if (label != null) n.label = label;
		if (icon != null) n.icon = icon;
		if (command != null) {
			String saved = WheelNodeCheck.normalizedCommand(command);
			n.command = saved == null ? command : saved;
		}
		if (asArc != null) n.asArc = asArc;
		String problem = WheelNodeCheck.problem(n, loc.arc());
		if (problem != null) return fail(wheel, problem);
		return new Result(w, at, null);
	}

	/** Removes the node at {@code at} (with everything inside it); selects the neighbour left in its place. */
	public static Result delete(List<WheelNode> wheel, Path at) {
		List<WheelNode> w = copy(wheel);
		Loc loc = locate(w, at);
		if (loc == null) return fail(wheel, "That entry no longer exists.");
		loc.list().remove(loc.index());
		dropEmptyArc(loc);
		Path selected;
		if (!loc.list().isEmpty()) selected = at.withLast(new Step(loc.arc(), Math.min(loc.index(), loc.list().size() - 1)));
		else selected = at.parent();
		return new Result(w, selected, null);
	}

	/** Swaps the node with the one before it in its list. */
	public static Result moveUp(List<WheelNode> wheel, Path at) {
		return move(wheel, at, -1);
	}

	/** Swaps the node with the one after it in its list. */
	public static Result moveDown(List<WheelNode> wheel, Path at) {
		return move(wheel, at, 1);
	}

	/**
	 * Takes a ring's child (or a slice's arc entry) out to become its ring's (slice's) next sibling. Refused at the
	 * top level, which has nothing to move out of.
	 */
	public static Result moveOut(List<WheelNode> wheel, Path at) {
		Path holder = at.parent();
		if (holder == null) return fail(wheel, "This entry is already at the top level.");
		List<WheelNode> w = copy(wheel);
		Loc loc = locate(w, at);
		Loc up = locate(w, holder);
		if (loc == null || up == null) return fail(wheel, "That entry no longer exists.");
		WheelNode node = loc.list().remove(loc.index());
		dropEmptyArc(loc);
		int to = up.index() + 1;
		up.list().add(to, node);
		return new Result(w, holder.withLast(new Step(false, to)), null);
	}

	private static Result move(List<WheelNode> wheel, Path at, int by) {
		List<WheelNode> w = copy(wheel);
		Loc loc = locate(w, at);
		if (loc == null) return fail(wheel, "That entry no longer exists.");
		int to = loc.index() + by;
		if (to < 0) return fail(wheel, "Already first.");
		if (to >= loc.list().size()) return fail(wheel, "Already last.");
		WheelNode other = loc.list().get(to);
		loc.list().set(to, loc.node());
		loc.list().set(loc.index(), other);
		return new Result(w, at.withLast(new Step(loc.arc(), to)), null);
	}

	private static Result insertAfter(List<WheelNode> wheel, Path at, WheelNode node) {
		List<WheelNode> w = copy(wheel);
		List<WheelNode> list = w;
		int to;
		boolean arc = false;
		Loc loc = null;
		if (at != null) {
			loc = locate(w, at);
			if (loc == null) return fail(wheel, "That entry no longer exists.");
			list = loc.list();
			arc = loc.arc();
			to = loc.index() + 1;
		} else {
			to = w.size();
		}
		WheelNode entry = prepared(node);
		String problem = WheelNodeCheck.problem(entry, arc);
		if (problem != null) return fail(wheel, problem);
		list.add(to, entry);
		Path selected = loc == null ? Path.top(to) : at.withLast(new Step(arc, to));
		return new Result(w, selected, null);
	}

	/** A private copy of {@code node} with its command written the way saving writes it. */
	private static WheelNode prepared(WheelNode node) {
		WheelNode n = GSON.fromJson(GSON.toJson(node), WheelNode.class);
		if (n.command != null && !n.command.isBlank()) n.command = WheelNodeCheck.normalizedCommand(n.command);
		return n;
	}

	private static Result fail(List<WheelNode> wheel, String error) {
		return new Result(wheel, null, error);
	}

	private static List<WheelNode> copy(List<WheelNode> wheel) {
		List<WheelNode> c = GSON.fromJson(GSON.toJson(wheel, LIST), LIST);
		return c == null ? new ArrayList<>() : c;
	}

	/** An emptied arc is saved as no arc at all, so remove it rather than leave an empty list. */
	private static void dropEmptyArc(Loc loc) {
		if (loc.arc() && loc.list().isEmpty()) loc.owner().arc = null;
	}

	private static Kind kind(Loc loc) {
		return Row.kindOf(loc.node(), loc.arc());
	}

	/** Where a path lands: the list holding the node (owned by {@code owner}, null for the top level) and its index. */
	private record Loc(List<WheelNode> list, int index, WheelNode owner, boolean arc) {
		WheelNode node() { return list.get(index); }
	}

	/** Follows {@code path} through {@code wheel}; null if any step is out of range or missing. */
	private static Loc locate(List<WheelNode> wheel, Path path) {
		List<WheelNode> list = wheel;
		WheelNode owner = null;
		boolean arc = false;
		List<Step> steps = path.steps();
		for (int i = 0; i < steps.size(); i++) {
			Step s = steps.get(i);
			if (i > 0) {
				WheelNode held = list.get(steps.get(i - 1).index());
				owner = held;
				arc = s.arc();
				list = arc ? held.arc : held.children;
			} else if (s.arc()) {
				return null;
			}
			if (list == null || s.index() < 0 || s.index() >= list.size() || list.get(s.index()) == null) return null;
		}
		return new Loc(list, steps.get(steps.size() - 1).index(), owner, arc);
	}
}
