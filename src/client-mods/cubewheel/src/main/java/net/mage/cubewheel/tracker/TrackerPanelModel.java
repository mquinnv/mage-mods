package net.mage.cubewheel.tracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.mage.cubewheel.tracker.local.ObjectiveInfo;

/**
 * The "Tracker" HUD panel, in the left-hand column under the Jobs panel and in its compact style: the source's
 * marker (⚒ ✦ ⚑ ★ •) in the tag column, a short title ("✪4 Skill Level", "King of the Jungle") and a short count
 * on the right ("1,902/2,500", "~", "✓?"). Built from {@link TrackerStore#hudSections}, so pinning, hiding,
 * the line cap and the world filter work as before; a grey "— Pinned" / "— This world" … heading starts each group
 * when there is more than one. A quest with several objectives gets an indented "↳ 10% Golden Knights" row per
 * objective. Pure: no Minecraft/Fabric imports.
 */
public final class TrackerPanelModel {
	public static final String TITLE = "Tracker";
	/** Longest title shown; longer ones are cut with "…". */
	static final int MAX_TITLE = 40;
	/** Longest objective row (it may run across the whole panel width). */
	static final int MAX_DETAIL = 40;
	static final String DETAIL_INDENT = " ↳ ";
	static final String HEADING_MARK = "— ";

	/** "Rank [✪4]" → "✪4". */
	private static final Pattern RANK = Pattern.compile("(?i)^rank\\s*\\[([^\\]]+)]$");
	/** An objective row from {@link EntryLabel}: "10% Slay Golden Knights". */
	private static final Pattern DETAIL = Pattern.compile("^(\\d+%)\\s+(.*)$");

	/** How a line is drawn; the adapter picks the colours. */
	public enum Tone {
		/** A group heading ("— Pinned"). */
		HEADING,
		/** An objective row under its quest. */
		DETAIL,
		/** Read complete, or an estimate at its target ("✓?": probably ready to claim). */
		DONE,
		/** At or above the near threshold. */
		NEAR,
		/** Anything else. */
		NORMAL,
		/** An entry in the "Other worlds" group. */
		OTHER_WORLD
	}

	/**
	 * One panel row: {@code tag} (the source's marker, "" for headings and objective rows) in {@code tagColor},
	 * {@code text} and {@code right} (a short count, "" for headings and objective rows).
	 */
	public record Line(String tag, int tagColor, String text, String right, Tone tone, Activity activity, double progress) {
		public Line(String tag, int tagColor, String text, String right, Tone tone, Activity activity) {
			this(tag, tagColor, text, right, tone, activity, -1);
		}

		public Line(String tag, int tagColor, String text, String right, Tone tone) {
			this(tag, tagColor, text, right, tone, Activity.NONE);
		}
	}

	private TrackerPanelModel() {}

	/**
	 * Lines for {@code sections} (see {@link TrackerStore#hudSections}); {@code objectives} gives an entry's stored
	 * objective (may return null), {@code near} is {@code tracker.nearThreshold}. Empty = nothing to show.
	 */
	public static List<Line> build(List<TrackerStore.HudSection> sections, Function<String, ObjectiveInfo> objectives,
			double near, Collection<String> worldNames, long now) {
		return build(sections, objectives, near, worldNames, now, null);
	}

	/** As above; {@code activity} says how recently each entry (by id) made progress (null: none marked). */
	public static List<Line> build(List<TrackerStore.HudSection> sections, Function<String, ObjectiveInfo> objectives,
			double near, Collection<String> worldNames, long now, Function<String, Activity> activity) {
		List<Line> lines = new ArrayList<>();
		if (sections == null) return lines;
		boolean headings = sections.size() > 1;
		for (TrackerStore.HudSection s : sections) {
			if (s.rows().isEmpty()) continue;
			if (headings) lines.add(new Line("", 0, HEADING_MARK + label(s.kind()), "", Tone.HEADING));
			for (TrackerRow r : s.rows()) {
				Trackable t = r.item();
				ObjectiveInfo info = objectives == null ? null : objectives.apply(t.id());
				EntryLabel label = EntryLabel.of(t.name(), info);
				SourceTag tag = SourceTag.of(t.source());
				Activity act = activity == null ? Activity.NONE : activity.apply(t.id());
				lines.add(new Line(tag.glyph(), tag.argb(), title(t.source(), label.title(), worldNames),
						CompactJob.count(r, now), tone(r, near, s.kind()), act == null ? Activity.NONE : act,
						JobsPanelModel.progress(r)));
				if (info != null && info.subs() != null && info.subs().size() > 1) {
					// One row per objective, its count in the right column: 50% of "Slay 10 Golden Knights" is 5/10.
					for (ObjectiveInfo.Sub sub : info.subs()) {
						lines.add(new Line("", 0, DETAIL_INDENT + CompactJob.cut(detailName(sub, worldNames), MAX_DETAIL),
								detailCount(sub), Tone.DETAIL));
					}
				} else {
					for (String d : label.details()) {
						lines.add(new Line("", 0, detail(d, worldNames), "", Tone.DETAIL));
					}
				}
			}
		}
		return List.copyOf(lines);
	}

	/**
	 * A short title: "Rank [✪4] · Reach 2,500 Skill Level" → "✪4 Skill Level"; a job listing "Farming Heavy ·
	 * Harvest Cherry Logs" → "Cherry Logs"; any other "Name · objective" keeps its name ("Jungle Pursuit"), the
	 * count says how far along it is. Cut at {@link #MAX_TITLE} characters.
	 */
	static String title(String source, String title, Collection<String> worldNames) {
		String t = CompactJob.clean(title);
		int dot = t.indexOf(" · ");
		String name = dot < 0 ? t : t.substring(0, dot).trim();
		String objective = dot < 0 ? "" : t.substring(dot + 3).trim();
		Matcher rank = RANK.matcher(name);
		String out;
		if (rank.matches()) {
			String r = rank.group(1).trim();
			out = objective.isEmpty() ? r : r + " " + CompactJob.objective(objective, worldNames);
		} else if (JobsPanelModel.SOURCE.equals(source) && !objective.isEmpty()) {
			out = CompactJob.objective(objective, worldNames);
		} else {
			out = name.isEmpty() ? CompactJob.objective(objective, worldNames) : name;
		}
		return CompactJob.cut(out, MAX_TITLE);
	}

	private static final Pattern AMOUNT = Pattern.compile("(?<![\\d.,])(\\d[\\d,]*)(?![\\d.,]*%)");
	private static final Pattern STEP_LEAD = Pattern.compile("(?i)^(?:participate in slaying|participate in|complete the)\\s+");

	/** "Slay 10 Golden Knights" → "Golden Knights"; "Complete the Volcano Potion Quest" → "Volcano Potion Quest". */
	static String detailName(ObjectiveInfo.Sub sub, Collection<String> worldNames) {
		String text = sub == null || sub.text() == null ? "" : CompactJob.clean(sub.text());
		text = STEP_LEAD.matcher(text).replaceFirst("");
		return CompactJob.objective(text, worldNames);
	}

	/**
	 * The objective's share as units: its percentage of the amount it names ("Slay 10 Golden Knights" at 50% →
	 * "5/10"); an objective naming no amount is one step ("0/1"). "" when the menu gave no percentage.
	 */
	static String detailCount(ObjectiveInfo.Sub sub) {
		if (sub == null || sub.percent() == null) return "";
		long total = 1;
		Matcher m = AMOUNT.matcher(sub.text() == null ? "" : sub.text());
		if (m.find()) {
			try {
				total = Math.max(1, Long.parseLong(m.group(1).replace(",", "")));
			} catch (NumberFormatException ignored) {
				total = 1;
			}
		}
		long done = Math.round(Math.max(0, Math.min(100, sub.percent())) * total / 100.0);
		return CompactJob.number(done) + "/" + CompactJob.number(total);
	}

	/** "10% Slay Golden Knights" → " ↳ 10% Golden Knights", cut to fit. */
	static String detail(String d, Collection<String> worldNames) {
		Matcher m = DETAIL.matcher(d);
		String text = m.matches() ? m.group(1) + " " + CompactJob.objective(m.group(2), worldNames)
				: CompactJob.objective(d, worldNames);
		return DETAIL_INDENT + CompactJob.cut(text, MAX_DETAIL);
	}

	static String label(TrackerStore.HudSection.Kind kind) {
		return switch (kind) {
			case PINNED -> "Pinned";
			case THIS_WORLD -> "This world";
			case ANYWHERE -> "Anywhere";
			case OTHER_WORLDS -> "Other worlds";
		};
	}

	/** Green when done or probably done (an estimate at its target, "✓?"); yellow only means "close". */
	private static Tone tone(TrackerRow r, double near, TrackerStore.HudSection.Kind kind) {
		if (r.complete() || r.atCap()) return Tone.DONE;
		if (r.fraction() >= near) return Tone.NEAR;
		return kind == TrackerStore.HudSection.Kind.OTHER_WORLDS ? Tone.OTHER_WORLD : Tone.NORMAL;
	}
}
