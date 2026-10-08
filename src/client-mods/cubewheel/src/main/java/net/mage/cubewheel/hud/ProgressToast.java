package net.mage.cubewheel.hud;

import net.mage.cubewheel.config.CubeWheelConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * The popup under the crosshair when local counting adds progress to tracked entries: one line per entry, "+1 Mana
 * Wolves  ~10/74" in cyan, or "✓ Mana Wolves  ~74/74" in green when the increment completes it. Each line carries a
 * pie of how far along the entry is ({@link Shown#fraction}). An increment that crosses a quarter of the target
 * (25, 50, 75 %) makes its line a milestone, "50% · Mana Wolves  ~37/74" in gold, shown twice as long; within the
 * last few units ({@link #countdown}) the count reads "3 to go" in yellow (Michael 2026-10-08). One signal (a kill,
 * a block) counting for several entries shows up to {@link #MAX_LINES} lines, in the order the increments arrive
 * (the counter credits the most specific entry first), above the lines of earlier signals. Each line merges further
 * increments for its own entry ("+7 …") and restarts its own time; an increment for an entry not shown starts a new
 * line, and past {@link #MAX_LINES} the line idle longest goes. One-off notices ({@link #onNotice}: "Inventory full")
 * share the lines, on top. A line stays fully visible for {@code toast.seconds}
 * after its last increment, then fades over {@link #FADE_MS}. Thread-safe, as the counter and the HUD may call from
 * different threads. Pure: no Minecraft/Fabric imports.
 */
public final class ProgressToast {
	/** How long the fade after the visible time takes. */
	public static final long FADE_MS = 300;
	/** At most this many lines at once. */
	public static final int MAX_LINES = 3;
	/** Increments this close together come from one signal (a kill counting for a job and two quests at once). */
	static final long SAME_SIGNAL_MS = 50;
	/** The quarters an increment can cross. */
	static final int[] MILESTONES = {25, 50, 75};
	/** A milestone line stays visible this many times the configured seconds. */
	static final int MILESTONE_HOLD = 2;
	/** The countdown starts within this many units of the target (less for small targets, see {@link #countdown}). */
	static final int COUNTDOWN_UNITS = 5;
	public static final int GOLD = 0xFFFFAA00;

	/**
	 * What to draw: {@code alpha} runs from 1 (fully visible) down towards 0 during the fade; {@code fraction} is how
	 * far along the entry is, 0..1, for a pie before the text (-1: none, a notice or an entry without numbers).
	 */
	public record Shown(String text, int color, double alpha, double fraction) {
		public Shown(String text, int color, double alpha) {
			this(text, color, alpha, -1);
		}
	}

	private static final class Line {
		final String id;
		/** A one-off notice's text and colour, shown as is (null for progress lines). */
		String notice;
		int noticeColor;
		String name;
		long units;
		String count;
		boolean done;
		/** The entry's numbers (-1: none). */
		long current = -1, target = -1;
		/** The quarter (25, 50, 75) the latest increments crossed; 0 for none. */
		int milestone;
		/** When the last increment arrived; the line ends {@code seconds} + {@link #FADE_MS} after it. */
		long lastAt;

		Line(String id) {
			this.id = id;
		}
	}

	/** Top first. */
	private final List<Line> lines = new ArrayList<>();
	/** The line the last increment went to, and when: the next one of the same signal goes right below it. */
	private Line lastLine;
	private long lastAt;

	/**
	 * {@code units} objective units were counted for entry {@code id} (a {@code TrackerStore} counter key), shown as
	 * {@code name}; {@code count} is its count as the panels show it ("~10/74", "" if none) and {@code done} whether it
	 * is now at its target. Ignored while the popup is off.
	 */
	public synchronized void onIncrement(String id, String name, long units, String count, boolean done, long now,
			CubeWheelConfig.Toast cfg) {
		onIncrement(id, name, units, count, done, -1, -1, now, cfg);
	}

	/** As above, with the entry's numbers after the increment ({@code current} of {@code target}; -1 for none). */
	public synchronized void onIncrement(String id, String name, long units, String count, boolean done, long current,
			long target, long now, CubeWheelConfig.Toast cfg) {
		if (id == null || units <= 0 || cfg == null || !cfg.enabled) return;
		prune(now, cfg);
		Line line = find(id);
		if (line == null) {
			line = new Line(id);
		} else {
			lines.remove(line);
		}
		line.units = line.units + units;
		line.name = name == null ? "" : name;
		line.count = count == null ? "" : count;
		line.done = done;
		line.current = current;
		line.target = target;
		int crossed = milestone(current - units, current, target);
		if (crossed > 0) line.milestone = crossed; // a later increment of the same line keeps the quarter it crossed
		line.lastAt = now;
		boolean sameSignal = lastLine != null && lines.contains(lastLine) && now - lastAt < SAME_SIGNAL_MS;
		lines.add(sameSignal ? lines.indexOf(lastLine) + 1 : 0, line);
		while (lines.size() > MAX_LINES) lines.remove(idlest());
		lastLine = lines.contains(line) ? line : null;
		lastAt = now;
	}

	/**
	 * A one-off notice ({@code text} in {@code color}, see {@link Notices}): a new line on top, or the same notice's
	 * line moved there with its time restarted. Ignored while the popup is off.
	 */
	public synchronized void onNotice(String text, int color, long now, CubeWheelConfig.Toast cfg) {
		if (text == null || text.isEmpty() || cfg == null || !cfg.enabled) return;
		prune(now, cfg);
		String id = "notice:" + text;
		Line line = find(id);
		if (line == null) {
			line = new Line(id);
		} else {
			lines.remove(line);
		}
		line.notice = text;
		line.noticeColor = color;
		line.lastAt = now;
		lines.add(0, line);
		while (lines.size() > MAX_LINES) lines.remove(idlest());
	}

	/**
	 * Takes back {@code units} of entry {@code id} (the server rejected a break, a chat catch replaces the bobber's):
	 * its line's total drops, without restarting its time; all of it taken back removes the line. Entries not shown
	 * are ignored.
	 */
	public synchronized void onReverse(String id, long units, String count, boolean done) {
		onReverse(id, units, count, done, -1, -1);
	}

	/** As above, with the entry's numbers after the reversal. */
	public synchronized void onReverse(String id, long units, String count, boolean done, long current, long target) {
		Line line = find(id);
		if (line == null || units <= 0) return;
		line.units -= units;
		if (line.units <= 0) {
			lines.remove(line);
			return;
		}
		line.count = count == null ? "" : count;
		line.done = done;
		line.current = current;
		line.target = target;
		if (line.milestone > 0 && current >= 0 && target > 0 && current * 100 < line.milestone * target) line.milestone = 0;
	}

	/** What to draw at {@code now}, top line first; empty when there is nothing (none yet, gone, or the popup is off). */
	public synchronized List<Shown> lines(long now, CubeWheelConfig.Toast cfg) {
		if (cfg == null || !cfg.enabled) return List.of();
		List<Shown> out = new ArrayList<>();
		for (Line l : lines) {
			if (!visible(l, now, cfg)) continue;
			long age = now - l.lastAt;
			long visibleMs = visibleMs(l, cfg);
			double alpha = age < visibleMs ? 1.0 : 1.0 - (age - visibleMs) / (double) FADE_MS;
			if (l.notice != null) {
				out.add(new Shown(l.notice, l.noticeColor, alpha));
				continue;
			}
			out.add(new Shown(text(l), color(l), alpha, fraction(l.current, l.target)));
		}
		return out;
	}

	/** "+1 Mana Wolves  ~10/74", "50% · Mana Wolves  ~37/74", "+1 Mana Wolves  3 to go", "✓ Mana Wolves  ~74/74". */
	private static String text(Line l) {
		String head;
		if (l.done) head = "✓ " + l.name;
		else if (l.milestone > 0) head = l.milestone + "% · " + l.name;
		else head = "+" + l.units + " " + l.name;
		long left = countdown(l.current, l.target);
		String count = !l.done && left > 0 ? left + " to go" : l.count;
		return count.isEmpty() ? head : head + "  " + count;
	}

	private static int color(Line l) {
		if (l.done) return Panel.GREEN;
		if (l.milestone > 0) return GOLD;
		return countdown(l.current, l.target) > 0 ? Panel.YELLOW : Panel.CYAN;
	}

	/** The highest quarter (25, 50, 75) that going from {@code before} to {@code after} of {@code target} crossed; 0 for none. */
	static int milestone(long before, long after, long target) {
		if (target <= 0 || after < 0) return 0;
		int out = 0;
		for (int m : MILESTONES) {
			// Crossed: below the quarter before, at or past it now (and not at the target: that is "done").
			if (before * 100 < m * target && after * 100 >= m * target && after < target) out = m;
		}
		return out;
	}

	/**
	 * How many units are left when the countdown shows: within the last {@link #COUNTDOWN_UNITS}, or the last tenth
	 * of a small target (at least the last one); 0 when further away, done, or without numbers.
	 */
	static long countdown(long current, long target) {
		if (target <= 0 || current < 0 || current >= target) return 0;
		long window = Math.max(1, Math.min(COUNTDOWN_UNITS, Math.round(target / 10.0)));
		long left = target - current;
		return left <= window ? left : 0;
	}

	/** {@code current}/{@code target} clamped to 0..1; -1 without numbers. */
	static double fraction(long current, long target) {
		if (target <= 0 || current < 0) return -1;
		return Math.max(0, Math.min(1, current / (double) target));
	}

	private Line find(String id) {
		if (id == null) return null;
		for (Line l : lines) if (l.id.equals(id)) return l;
		return null;
	}

	/** The line whose last increment is oldest; the lowest one of a tie (the least specific of that signal). */
	private Line idlest() {
		Line out = lines.get(lines.size() - 1);
		for (int i = lines.size() - 1; i >= 0; i--) if (lines.get(i).lastAt < out.lastAt) out = lines.get(i);
		return out;
	}

	/** Lines that are gone start afresh: a later increment counts from +1 again. */
	private void prune(long now, CubeWheelConfig.Toast cfg) {
		lines.removeIf(l -> !visible(l, now, cfg));
	}

	private static boolean visible(Line l, long now, CubeWheelConfig.Toast cfg) {
		long age = now - l.lastAt;
		return age >= 0 && age < visibleMs(l, cfg) + FADE_MS;
	}

	/** The configured time; a milestone line holds {@link #MILESTONE_HOLD} times as long. */
	private static long visibleMs(Line l, CubeWheelConfig.Toast cfg) {
		long ms = Math.round(cfg.seconds * 1000);
		return l.milestone > 0 && !l.done ? ms * MILESTONE_HOLD : ms;
	}
}
