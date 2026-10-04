package net.mage.cubewheel.hud;

import net.mage.cubewheel.config.CubeWheelConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * The popup under the crosshair when local counting adds progress to tracked entries: one line per entry, "+1 Mana
 * Wolves  ~10/74" in cyan, or "✓ Mana Wolves  ~74/74" in green when the increment completes it. One signal (a kill,
 * a block) counting for several entries shows up to {@link #MAX_LINES} lines, in the order the increments arrive
 * (the counter credits the most specific entry first), above the lines of earlier signals. Each line merges further
 * increments for its own entry ("+7 …") and restarts its own time; an increment for an entry not shown starts a new
 * line, and past {@link #MAX_LINES} the line idle longest goes. A line stays fully visible for {@code toast.seconds}
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

	/** What to draw: {@code alpha} runs from 1 (fully visible) down towards 0 during the fade. */
	public record Shown(String text, int color, double alpha) {}

	private static final class Line {
		final String id;
		String name;
		long units;
		String count;
		boolean done;
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
		line.lastAt = now;
		boolean sameSignal = lastLine != null && lines.contains(lastLine) && now - lastAt < SAME_SIGNAL_MS;
		lines.add(sameSignal ? lines.indexOf(lastLine) + 1 : 0, line);
		while (lines.size() > MAX_LINES) lines.remove(idlest());
		lastLine = lines.contains(line) ? line : null;
		lastAt = now;
	}

	/**
	 * Takes back {@code units} of entry {@code id} (the server rejected a break, a chat catch replaces the bobber's):
	 * its line's total drops, without restarting its time; all of it taken back removes the line. Entries not shown
	 * are ignored.
	 */
	public synchronized void onReverse(String id, long units, String count, boolean done) {
		Line line = find(id);
		if (line == null || units <= 0) return;
		line.units -= units;
		if (line.units <= 0) {
			lines.remove(line);
			return;
		}
		line.count = count == null ? "" : count;
		line.done = done;
	}

	/** What to draw at {@code now}, top line first; empty when there is nothing (none yet, gone, or the popup is off). */
	public synchronized List<Shown> lines(long now, CubeWheelConfig.Toast cfg) {
		if (cfg == null || !cfg.enabled) return List.of();
		long visibleMs = visibleMs(cfg);
		List<Shown> out = new ArrayList<>();
		for (Line l : lines) {
			if (!visible(l, now, cfg)) continue;
			long age = now - l.lastAt;
			double alpha = age < visibleMs ? 1.0 : 1.0 - (age - visibleMs) / (double) FADE_MS;
			String head = l.done ? "✓ " + l.name : "+" + l.units + " " + l.name;
			String text = l.count.isEmpty() ? head : head + "  " + l.count;
			out.add(new Shown(text, l.done ? Panel.GREEN : Panel.CYAN, alpha));
		}
		return out;
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
		return age >= 0 && age < visibleMs(cfg) + FADE_MS;
	}

	private static long visibleMs(CubeWheelConfig.Toast cfg) {
		return Math.round(cfg.seconds * 1000);
	}
}
