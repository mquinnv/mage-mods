package net.mage.cubewheel.wheel.edit;

import net.mage.cubewheel.config.WheelNode;

/** The grey text at the right of a wheel editor row: what the entry does (its command, or what sort of entry it is). */
public final class RowHint {
	private RowHint() {}

	/** E.g. "/spawn", "ring", "live: homes", "arc entry: /warp a"; notes a fan-out and an outer tier. */
	public static String of(Row row) {
		WheelNode n = row.node();
		String hint = switch (row.kind()) {
			case LEAF -> command(n);
			case ARC_ENTRY -> "arc entry: " + command(n);
			case RING -> "ring";
			case LIVE_RING, LIVE_SLICE -> "live: " + n.dynamic;
		};
		if (n.asArc && (row.kind() == Row.Kind.RING || row.kind() == Row.Kind.LIVE_RING)) hint += ", fans out";
		if (n.outer != null) hint += " + outer tier";
		return hint;
	}

	private static String command(WheelNode n) {
		return n.command == null || n.command.isBlank() ? "(no command)" : n.command;
	}
}
