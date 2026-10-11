package net.mage.cubewheel.tracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.mage.cubewheel.tracker.local.ObjectiveInfo;
import net.mage.cubewheel.tracker.local.WorldScope;

/**
 * The "Quests" HUD panel (quests, prestige ranks, challenges: everything tracked that is not a job listing), in the
 * left-hand column under the Jobs panel and in its compact style: the source's
 * marker (⚒ ✦ ⚑ ★ •) in the tag column, a short title ("✪4 Skill Level", "King of the Jungle") and a short count
 * on the right ("1,902/2,500", "~", "✓?"). Built from {@link TrackerStore#hudSections}, so pinning, hiding,
 * the line cap and the world filter work as before; a grey "— Pinned" / "— This world" … heading starts each group
 * when there is more than one. A quest with several objectives gets an indented "↳ 10% Golden Knights" row per
 * objective. Pure: no Minecraft/Fabric imports.
 */
public final class TrackerPanelModel {
	/** What the panel holds once the Jobs panel has the job listings: quests, prestige ranks, challenges (Michael 2026-10-08). */
	public static final String TITLE = "Quests";

	/**
	 * The panel's title with the key that opens the picker screen, "Quests [X]", so the way in is always in sight;
	 * just the title when the key is unbound ({@code key} null or blank).
	 */
	public static String panelTitle(String key) {
		return key == null || key.isBlank() ? TITLE : TITLE + " [" + key.trim() + "]";
	}
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
		return build(sections, objectives, near, worldNames, now, activity, null);
	}

	/**
	 * As above; {@code subCounted} gives the units counted locally for an objective of a multi-objective entry
	 * (key {@link TrackerStore#subKey}), shown on its row ("~330/2,500"); null: none.
	 */
	public static List<Line> build(List<TrackerStore.HudSection> sections, Function<String, ObjectiveInfo> objectives,
			double near, Collection<String> worldNames, long now, Function<String, Activity> activity,
			Function<String, Long> subCounted) {
		List<Line> lines = new ArrayList<>();
		if (sections == null) return lines;
		int nextRank = nextPrestigeRank(sections);
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
				String right = CompactJob.count(r, now);
				Tone tone = tone(r, near, s.kind());
				// A met rank behind an unmet lower one is claimed later and is no action item now: left out
				// ("there's no action item for me until it gets unblocked", Michael 2026-10-08).
				if (PrestigeOrder.blocked(r, nextRank)) continue;
				String text = title(t.source(), label.title(), worldNames);
				if (s.kind() == TrackerStore.HudSection.Kind.OTHER_WORLDS) {
					// Which other world: the Jobs panel's two-letter tag ("the other worlds list ought to actually
					// have the abbreviation for the world", Michael 2026-10-08).
					String world = CompactJob.worldTag(WorldScope.of(t.name(), info, worldNames));
					if (!world.isEmpty()) text = world + " " + text;
				}
				lines.add(new Line(tag.glyph(), tag.argb(), text, right, tone, act == null ? Activity.NONE : act,
						JobsPanelModel.progress(r)));
				if (info != null && info.subs() != null && info.subs().size() > 1) {
					// One row per objective, its count in the right column: 50% of "Slay 10 Golden Knights" is 5/10,
					// plus what was counted locally since that read ("~330/2,500").
					for (int i = 0; i < info.subs().size(); i++) {
						ObjectiveInfo.Sub sub = info.subs().get(i);
						String subKey = TrackerStore.subKey(t.id(), i);
						Long extra = subCounted == null ? null : subCounted.apply(subKey);
						long[] dt = detailAmounts(sub, extra == null ? 0 : extra);
						String count = detailRight(dt, extra == null ? 0 : extra);
						// The objective's own activity (a count on it stamps its key too), so its row lights up.
						Activity subAct = activity == null ? Activity.NONE : activity.apply(subKey);
						lines.add(new Line("", 0, DETAIL_INDENT + CompactJob.cut(detailName(sub, worldNames), MAX_DETAIL),
								count, Tone.DETAIL, subAct == null ? Activity.NONE : subAct,
								dt == null ? -1 : Math.min(1, dt[0] / (double) dt[1])));
					}
				} else if (info != null && info.subs() != null && info.subs().size() == 1
						&& !label.title().equals(t.name()) && !objectiveInTitle(t.source(), t.name())) {
					// A quest's name says nothing about what to do ("Golemy Situation", "Catacombs Hunter": Michael
					// 2026-10-11), and the title keeps only the name: its one objective gets a row of its own, in units
					// when the menu gave only a percentage (4% of "Slay 25 Desert Golems" is 1/25). A row already in
					// units (a counter read, a parsed rule, or a local estimate) keeps its count on the entry's row.
					// The label's objective is the sub's text without its own counter ("Mine 0/15,000 Tangleroots Resources").
					ObjectiveInfo.Sub sub = new ObjectiveInfo.Sub(label.title().substring(t.name().length() + 3).trim(), null);
					boolean percent = !r.estimated() && r.shownMax() == 100;
					long[] dt = percent ? detailAmounts(new ObjectiveInfo.Sub(sub.text(), r.fraction() * 100), 0) : null;
					lines.add(new Line("", 0, DETAIL_INDENT + CompactJob.cut(detailName(sub, worldNames), MAX_DETAIL),
							detailRight(dt, 0), Tone.DETAIL, act == null ? Activity.NONE : act,
							dt == null ? -1 : Math.min(1, dt[0] / (double) dt[1])));
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
	 * Harvest Cherry Logs" → "Cherry Logs"; any other "Name · objective" keeps its name ("Jungle Pursuit") and the
	 * objective goes on a "↳" row of its own ({@link #build}). Cut at {@link #MAX_TITLE} characters.
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
		} else if ((JobsPanelModel.SOURCE.equals(source) || ContainerScanner.CALENDAR.equals(source)) && !objective.isEmpty()) {
			out = CompactJob.objective(objective, worldNames);
		} else {
			out = name.isEmpty() ? CompactJob.objective(objective, worldNames) : name;
		}
		return CompactJob.cut(out, MAX_TITLE);
	}

	/** True when {@link #title} shows the entry's objective itself: prestige ranks, job listings and calendar days carry it in the name. */
	static boolean objectiveInTitle(String source, String name) {
		if (JobsPanelModel.SOURCE.equals(source) || ContainerScanner.CALENDAR.equals(source)) return true;
		if (name == null) return false;
		int dot = name.indexOf(" · ");
		return RANK.matcher((dot < 0 ? name : name.substring(0, dot)).trim()).matches();
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
	/** {done, total} for an objective row: its read percent of its amount plus {@code extra} counted since; null if unknown. */
	static long[] detailAmounts(ObjectiveInfo.Sub sub, long extra) {
		if (sub == null || sub.percent() == null) return null;
		long total = 1;
		Matcher m = AMOUNT.matcher(sub.text() == null ? "" : sub.text());
		if (m.find()) {
			try {
				total = Math.max(1, Long.parseLong(m.group(1).replace(",", "")));
			} catch (NumberFormatException ignored) {
				total = 1;
			}
		}
		long done = Math.round(Math.max(0, Math.min(100, sub.percent())) * total / 100.0) + Math.max(0, extra);
		return new long[] {Math.min(done, total), total};
	}

	/** An objective row's count from {@link #detailAmounts}: "5/10", "~6/10" with units counted since; "" if unknown. */
	static String detailRight(long[] amounts, long extra) {
		if (amounts == null) return "";
		return (extra > 0 ? "~" : "") + CompactJob.number(amounts[0]) + "/" + CompactJob.number(amounts[1]);
	}

	/** The lowest prestige rank not yet met among the shown entries (the one to work on), else 0. */
	static int nextPrestigeRank(List<TrackerStore.HudSection> sections) {
		List<TrackerRow> rows = new ArrayList<>();
		for (TrackerStore.HudSection s : sections) rows.addAll(s.rows());
		return PrestigeOrder.nextRank(rows);
	}

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
