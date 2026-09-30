package com.mage.cubewheel.tracker;

import java.util.ArrayList;
import java.util.List;

/**
 * One tracker refresh run, started by one keypress/click: send each configured command in turn, wait
 * for its menu, let it be scanned, close it, then send the next. Pure state machine (no Minecraft/Fabric
 * imports): the adapter reports what is on screen each tick via {@link #step} and carries out the
 * returned {@link Action}.
 *
 * <p>Guarantees: at most one run per {@link #COOLDOWN_MS} (counted from each start); a run in progress
 * cannot be restarted; a command is only sent once the previous menu was closed or timed out; any
 * screen the run did not expect (the user opening chat, the inventory, the pause menu, or closing the
 * menu with Esc) aborts the run for good.
 */
public final class RefreshPolicy {
	public static final long COOLDOWN_MS = 60_000;
	/** How long to wait for a menu to open after its command, and for an open menu to fill. */
	public static final long MENU_TIMEOUT_MS = 3_000;
	/** Once a menu shows items, keep it open this long so late slot updates arrive before the scan. */
	public static final long SETTLE_MS = 250;
	/** Pause between closing a menu and sending the next command. */
	public static final long GAP_MS = 300;

	/** What is on screen this tick. */
	public enum View {
		/** No screen. */
		NONE,
		/** A server container menu without items yet. */
		MENU_LOADING,
		/** A server container menu showing items. */
		MENU_READY,
		/** Any other screen: chat, own inventory, pause menu, CubeWheel screens... */
		OTHER
	}

	public enum Kind { NONE, SEND, CLOSE_MENU, FINISHED, ABORTED }

	public enum Outcome { STARTED, COOLDOWN, RUNNING, NO_COMMANDS }

	/** What the adapter must do now; {@code command} is set for SEND, {@code reason} for ABORTED. */
	public record Action(Kind kind, String command, String reason) {
		static final Action IDLE = new Action(Kind.NONE, null, null);

		static Action send(String command) { return new Action(Kind.SEND, command, null); }

		static Action aborted(String reason) { return new Action(Kind.ABORTED, null, reason); }
	}

	public record Start(Outcome outcome, long waitMs) {
		/** Whole seconds left on the cooldown, rounded up. */
		public long waitSeconds() {
			return (waitMs + 999) / 1000;
		}
	}

	private enum State { IDLE, READY, WAITING, MENU_OPEN, DONE }

	private long lastStart = Long.MIN_VALUE / 2;
	private State state = State.IDLE;
	private List<String> commands = List.of();
	private int index;
	private long notBefore;
	private long sentAt;
	private long openedAt;
	private long filledAt;
	private boolean filled;
	private int handled;
	private int timedOut;

	public Start start(long now, List<String> requested) {
		if (running()) return new Start(Outcome.RUNNING, 0);
		List<String> cmds = new ArrayList<>();
		if (requested != null) {
			for (String c : requested) {
				if (c != null && !c.isBlank()) cmds.add(c.trim());
			}
		}
		if (cmds.isEmpty()) return new Start(Outcome.NO_COMMANDS, 0);
		long wait = COOLDOWN_MS - (now - lastStart);
		if (wait > 0) return new Start(Outcome.COOLDOWN, wait);
		lastStart = now;
		commands = List.copyOf(cmds);
		index = 0;
		handled = 0;
		timedOut = 0;
		state = State.READY;
		notBefore = now;
		return new Start(Outcome.STARTED, 0);
	}

	public boolean running() {
		return state != State.IDLE;
	}

	/** Menus handled (scanned and closed) in the current or last run. */
	public int handled() {
		return handled;
	}

	/** Commands whose menu did not open, or stayed empty, within the timeout. */
	public int timedOut() {
		return timedOut;
	}

	/** Stops the run (e.g. disconnect); returns false when nothing was running. The cooldown still applies. */
	public boolean abort() {
		if (!running()) return false;
		state = State.IDLE;
		return true;
	}

	public Action step(long now, View view) {
		switch (state) {
			case IDLE:
				return Action.IDLE;
			case DONE:
				state = State.IDLE;
				return new Action(Kind.FINISHED, null, null);
			case READY:
				if (view != View.NONE) return abort(view == View.OTHER ? "another screen was opened" : "an unexpected menu opened");
				if (now < notBefore) return Action.IDLE;
				state = State.WAITING;
				sentAt = now;
				return Action.send(commands.get(index));
			case WAITING:
				if (view == View.OTHER) return abort("another screen was opened");
				if (view == View.MENU_LOADING || view == View.MENU_READY) {
					state = State.MENU_OPEN;
					openedAt = now;
					filled = false;
					return menuOpen(now, view);
				}
				if (now - sentAt >= MENU_TIMEOUT_MS) {
					timedOut++;
					advance(now);
				}
				return Action.IDLE;
			case MENU_OPEN:
				return menuOpen(now, view);
			default:
				return Action.IDLE;
		}
	}

	private Action menuOpen(long now, View view) {
		if (view == View.NONE) return abort("the menu was closed");
		if (view == View.OTHER) return abort("another screen was opened");
		if (view == View.MENU_READY && !filled) {
			filled = true;
			filledAt = now;
		}
		if (filled && now - filledAt >= SETTLE_MS) {
			handled++;
			advance(now);
			return new Action(Kind.CLOSE_MENU, null, null);
		}
		if (now - openedAt >= MENU_TIMEOUT_MS) {
			timedOut++;
			advance(now);
			return new Action(Kind.CLOSE_MENU, null, null);
		}
		return Action.IDLE;
	}

	private void advance(long now) {
		index++;
		if (index >= commands.size()) {
			state = State.DONE;
		} else {
			state = State.READY;
			notBefore = now + GAP_MS;
		}
	}

	private Action abort(String reason) {
		state = State.IDLE;
		return Action.aborted(reason);
	}
}
