package net.mage.cubewheel.tracker;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.function.ToDoubleFunction;
import java.util.regex.Pattern;
import net.mage.cubewheel.tracker.local.ObjectiveInfo;

/**
 * The "Focus" panel (bottom left): the entry you last made progress on, in full. Its heading ("Hunting · Experienced",
 * or a quest's name), the objective as the server wrote it, the world it is tied to, a big progress bar with the
 * count and percentage, your pace from the local counter ("3.2/min · ~19 min left") once there is enough to go on,
 * and the listing's reward lines (Michael 2026-10-08). Pure: no Minecraft/Fabric imports.
 */
public final class FocusPanelModel {
	/** A job listing's name: "Hunting Experienced · Slay Void Scuttler in Morend". */
	private static final Pattern LISTING =
			Pattern.compile("^(?:(.+?) )?(Beginner|Experienced|Heavy) · (.+)$", Pattern.CASE_INSENSITIVE);

	/**
	 * What the panel shows. {@code heading}: the industry and tier, or the quest's name; {@code tag}: the world
	 * ("MO", "" if none); {@code objective}: the objective text; {@code count}: "12/73"; {@code fraction} 0..1;
	 * {@code pace}: "" until known; {@code rewards}: the listing's reward lines.
	 */
	public record Focus(String heading, String tag, String objective, String count, double fraction, boolean done,
			String pace, List<String> rewards) {
		public int percent() {
			return (int) Math.floor(Math.max(0, Math.min(1, fraction)) * 100);
		}
	}

	private FocusPanelModel() {}

	/** As {@link #of(TrackerStore, String, Collection, long, ToDoubleFunction)} with no pace known. */
	public static Optional<Focus> of(TrackerStore store, String key, Collection<String> worldNames, long now) {
		return of(store, key, worldNames, now, k -> 0);
	}

	/**
	 * The focus for counter {@code key} (an entry id or sub key) at {@code now}; empty if the entry is unknown.
	 * {@code perMin} gives the key's recent rate ({@link Pace}; 0 = not known yet).
	 */
	public static Optional<Focus> of(TrackerStore store, String key, Collection<String> worldNames, long now,
			ToDoubleFunction<String> perMin) {
		if (store == null || key == null) return Optional.empty();
		String id = TrackerStore.baseId(key);
		Optional<TrackerRow> row = store.row(id);
		Optional<ProgressLabel> label = ProgressLabel.of(store, key, worldNames);
		if (row.isEmpty() || label.isEmpty()) return Optional.empty();
		Trackable t = row.get().item();
		ObjectiveInfo info = store.objective(id).orElse(null);
		String title = EntryLabel.of(t.name(), info).title();
		String heading;
		String objective;
		Matcher m = LISTING.matcher(title);
		if (m.matches()) {
			heading = (m.group(1) == null ? "" : m.group(1).trim() + " · ") + titleCase(m.group(2));
			objective = m.group(3).trim();
		} else {
			int dot = title.indexOf(" · ");
			heading = dot > 0 ? title.substring(0, dot) : title;
			objective = dot > 0 ? title.substring(dot + 3) : "";
		}
		ProgressLabel l = label.get();
		if (TrackerStore.subIndex(key) >= 0) objective = l.name(); // one objective of a multi-objective quest
		String tag = CompactJob.worldTag(store.scope(t, worldNames));
		double fraction = l.target() > 0 ? Math.max(0, Math.min(1, l.current() / (double) l.target())) : -1;
		if (l.done()) fraction = 1;
		String pace = l.done() ? "" : pace(perMin.applyAsDouble(key), l.current(), l.target());
		return Optional.of(new Focus(heading, tag, objective, l.count(), fraction, l.done(), pace, store.rewards(id)));
	}

	/**
	 * "3.2/min · ~19 min left" from the recent rate {@code perMin} ({@link Pace}: the last few minutes' credits, so
	 * a fishing session at a fish every few seconds is not averaged with the hours before it); "" when the rate is
	 * not known yet or the entry is done. The time left assumes the pace holds.
	 */
	static String pace(double perMin, long current, long target) {
		if (perMin <= 0 || target <= 0 || current < 0 || current >= target) return "";
		long leftMs = Math.round((target - current) / perMin * 60_000);
		return String.format(Locale.ROOT, "%s/min · ~%s left", rate(perMin), duration(leftMs));
	}

	/** "3.2" below 10, "12" from there. */
	static String rate(double perMin) {
		return perMin < 10 ? String.format(Locale.ROOT, "%.1f", perMin) : Long.toString(Math.round(perMin));
	}

	/** "45 s", "19 min", "1 h 20 min", "2 d 3 h". */
	static String duration(long ms) {
		long s = Math.max(0, ms / 1000);
		if (s < 60) return s + " s";
		long min = s / 60;
		if (min < 60) return min + " min";
		long h = min / 60;
		min %= 60;
		if (h < 24) return min == 0 ? h + " h" : h + " h " + min + " min";
		long d = h / 24;
		h %= 24;
		return h == 0 ? d + " d" : d + " d " + h + " h";
	}

	private static String titleCase(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
	}
}
