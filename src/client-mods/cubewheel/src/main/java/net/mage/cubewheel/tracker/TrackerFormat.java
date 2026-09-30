package net.mage.cubewheel.tracker;

import net.mage.cubewheel.tracker.local.Accuracy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Text formatting for tracker HUD lines. Pure: no Minecraft/Fabric imports. */
public final class TrackerFormat {
	private TrackerFormat() {}

	public static String line(Trackable t, long now) {
		return line(TrackerRow.plain(t), now);
	}

	/**
	 * "Name  1,234 / 1,500 (82%) · 12m". An estimated row reads "Name  ~6,437 / 10,000 (64%) · 5m", and
	 * "✓?" follows the percentage once the estimate reaches the target. The age is that of the last
	 * authoritative read.
	 */
	public static String line(TrackerRow row, long now) {
		return line(row, row.item().name(), now);
	}

	/** As {@link #line(TrackerRow, long)} with {@code title} in place of the entry's name (see {@link EntryLabel}). */
	public static String line(TrackerRow row, String title, long now) {
		long pct = Math.round(row.fraction() * 100);
		if (!row.complete()) pct = Math.min(pct, 99);
		Trackable t = row.item();
		return title + "  " + (row.estimated() ? "~" : "") + fmt(row.shownCurrent()) + " / " + fmt(row.shownMax())
			+ " (" + pct + "%)" + (row.atCap() ? " ✓?" : "") + " · " + age(now - t.seenAt());
	}

	/** The picker's line: the HUD line plus "+N~", the objective units counted since the last read. */
	public static String pickerLine(TrackerRow row, long now) {
		return line(row, now) + (row.estimated() ? "  +" + String.format(Locale.ROOT, "%,d", row.counted()) + "~" : "");
	}

	/** Tooltip lines for an estimated row; {@code last} is the previous snap-back of this entry, if any. */
	public static List<String> estimateTooltip(TrackerRow row, Accuracy last, long now) {
		List<String> out = new ArrayList<>();
		String age = age(now - row.item().seenAt());
		out.add("Estimated from what you did since this menu was last read (" + (age.equals("now") ? "now" : age + " ago") + ").");
		if (last != null) {
			out.add("Last check: counted " + String.format(Locale.ROOT, "%,d", last.counted())
					+ ", actual " + fmt(Math.round(last.actual() * 10) / 10.0) + ".");
		}
		return out;
	}

	public static String age(long ms) {
		if (ms < 60_000) return "now";
		if (ms < 3_600_000L) return (ms / 60_000) + "m";
		if (ms < 86_400_000L) return (ms / 3_600_000L) + "h";
		return (ms / 86_400_000L) + "d";
	}

	private static String fmt(double v) {
		if (v == Math.rint(v)) return String.format(Locale.ROOT, "%,d", Math.round(v));
		return String.format(Locale.ROOT, "%,.1f", v);
	}
}
