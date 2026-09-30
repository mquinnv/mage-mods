package com.mage.cubewheel.tracker;

import com.mage.cubewheel.tracker.ContainerScanner.ItemView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One tracker refresh run, started by one keypress/click: send each configured command in turn, wait
 * for its menu, let it be scanned, close it, then send the next. Pure state machine (no Minecraft/Fabric
 * imports): the adapter reports what is on screen each tick via {@link #step} and carries out the
 * returned {@link Action}. Times are milliseconds on a monotonic clock.
 *
 * <p>Guarantees: at most one run per {@link #COOLDOWN_MS} (counted from each start); a run in progress
 * cannot be restarted; a command is only sent once the previous menu was closed or timed out; any
 * screen the run did not expect (the user opening chat, the inventory, the pause menu, a menu that
 * {@link MenuClassifier} does not recognise, or closing the menu with Esc) aborts the run for good, and
 * so does the player pressing use or attack while the run waits between menus (that may open a chest or
 * an NPC's menu of their own). A recognised menu that arrives late (after its command timed out, during
 * the gap before the next command) is read and closed like any other; the run then carries on.
 */
public final class RefreshPolicy {
	public static final long COOLDOWN_MS = 60_000;
	/** How long to wait for a menu to open after its command, and for an open menu to fill. */
	public static final long MENU_TIMEOUT_MS = 3_000;
	/** Once a menu shows items, keep it open this long so late slot updates arrive before the scan. */
	public static final long SETTLE_MS = 250;
	/** Pause between closing a menu and sending the next command (also how long a late menu is awaited after the last command timed out). */
	public static final long GAP_MS = 300;

	/** What is on screen this tick. */
	public enum View {
		/** No screen. */
		NONE,
		/** A server container menu without items yet. */
		MENU_LOADING,
		/** A server container menu showing items that {@link MenuClassifier} recognises. */
		MENU_READY,
		/** Any other screen: chat, own inventory, pause menu, CubeWheel screens, unrecognised menus... */
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

	private enum State { IDLE, READY, WAITING, MENU_OPEN, LINGER, DONE }

	private static final String OTHER_SCREEN = "another screen was opened";
	private static final String UNEXPECTED_MENU = "an unexpected menu opened";
	private static final String USER_INPUT = "you used or attacked something";

	private long lastStart = Long.MIN_VALUE / 2;
	private State state = State.IDLE;
	private List<String> commands = List.of();
	private int index;
	private long notBefore;
	private long sentAt;
	private long openedAt;
	private long filledAt;
	private boolean filled;
	/** The open menu arrived late (not after a send): closing it does not advance the command list. */
	private boolean late;
	/** Where a late menu returns to once closed: READY (more commands) or DONE (the last one timed out). */
	private State afterLate;
	private int handled;
	private int timedOut;

	/**
	 * What a server container menu counts as for the run: MENU_LOADING while it has no items, MENU_READY
	 * when its items are recognised as a tracker menu, otherwise OTHER (so the run leaves it alone).
	 */
	public static View menuView(String title, List<ItemView> items, Map<String, String> titleSources) {
		if (items == null || items.isEmpty()) return View.MENU_LOADING;
		return MenuClassifier.classify(title, items, titleSources).isPresent() ? View.MENU_READY : View.OTHER;
	}

	/** "Refreshed N trackers", plus how many menus did not load. */
	public static String finishedMessage(int refreshed, int timedOut) {
		String msg = "Refreshed " + refreshed + (refreshed == 1 ? " tracker" : " trackers");
		if (timedOut > 0) msg += " (" + timedOut + (timedOut == 1 ? " menu" : " menus") + " did not load)";
		return msg;
	}

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

	/** Menus handled (scanned and closed after their command) in the current or last run. */
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

	/** {@link #step(long, View, boolean)} without user input. */
	public Action step(long now, View view) {
		return step(now, view, false);
	}

	/**
	 * Advances the run. {@code userInput}: the player pressed or holds use/attack this tick; while the run
	 * waits for a menu or between menus that aborts it (the player may be opening a menu of their own).
	 */
	public Action step(long now, View view, boolean userInput) {
		switch (state) {
			case IDLE:
				return Action.IDLE;
			case DONE:
				state = State.IDLE;
				return new Action(Kind.FINISHED, null, null);
			case READY:
				if (userInput) return abort(USER_INPUT);
				if (view == View.OTHER) return abort(OTHER_SCREEN);
				if (view != View.NONE) {
					if (index == 0) return abort(UNEXPECTED_MENU); // nothing was sent yet: not ours
					return openLate(now, view, State.READY);
				}
				if (now < notBefore) return Action.IDLE;
				state = State.WAITING;
				sentAt = now;
				return Action.send(commands.get(index));
			case WAITING:
				if (userInput) return abort(USER_INPUT);
				if (view == View.OTHER) return abort(OTHER_SCREEN);
				if (view == View.MENU_LOADING || view == View.MENU_READY) {
					state = State.MENU_OPEN;
					late = false;
					openedAt = now;
					filled = false;
					return menuOpen(now, view);
				}
				if (now - sentAt >= MENU_TIMEOUT_MS) {
					timedOut++;
					if (index + 1 >= commands.size()) {
						// The last command timed out: its menu may still come; wait one gap for it.
						index++;
						state = State.LINGER;
						notBefore = now + GAP_MS;
					} else {
						advance(now);
					}
				}
				return Action.IDLE;
			case LINGER:
				if (userInput || view == View.OTHER) return finish(); // nothing left to send; leave it alone
				if (view != View.NONE) return openLate(now, view, State.DONE);
				return now >= notBefore ? finish() : Action.IDLE;
			case MENU_OPEN:
				return menuOpen(now, view);
			default:
				return Action.IDLE;
		}
	}

	private Action openLate(long now, View view, State returnTo) {
		state = State.MENU_OPEN;
		late = true;
		afterLate = returnTo;
		openedAt = now;
		filled = false;
		return menuOpen(now, view);
	}

	private Action menuOpen(long now, View view) {
		if (view == View.NONE) return abort("the menu was closed");
		if (view == View.OTHER) return abort(late ? UNEXPECTED_MENU : OTHER_SCREEN);
		if (view == View.MENU_READY && !filled) {
			filled = true;
			filledAt = now;
		}
		if (filled && now - filledAt >= SETTLE_MS) {
			if (late) {
				state = afterLate;
				notBefore = now + GAP_MS;
			} else {
				handled++;
				advance(now);
			}
			return new Action(Kind.CLOSE_MENU, null, null);
		}
		if (now - openedAt >= MENU_TIMEOUT_MS) {
			// A late menu that never showed recognisable items is not ours to close: leave it to the player.
			if (late) return abort(UNEXPECTED_MENU);
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

	private Action finish() {
		state = State.IDLE;
		return new Action(Kind.FINISHED, null, null);
	}

	private Action abort(String reason) {
		state = State.IDLE;
		return Action.aborted(reason);
	}
}
