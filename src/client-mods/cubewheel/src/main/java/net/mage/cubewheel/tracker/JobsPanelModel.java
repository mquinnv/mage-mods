package net.mage.cubewheel.tracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.mage.cubewheel.tracker.local.ObjectiveInfo;
import net.mage.cubewheel.tracker.local.WorldScope;

/**
 * The left-hand "Jobs" panel: every tracked "jobs" entry, job listings grouped by industry (fixed order, unknown
 * industries after, alphabetically) and within one by tier (Beginner, Experienced, Heavy). Never capped. Finished
 * listings stay, marked " · hand in" (they still have to be claimed). The GOLDEN CRATE entry becomes the panel's
 * title ("Jobs · Golden Crate 4/5"). Hidden entries stay hidden. Pure: no Minecraft/Fabric imports.
 */
public final class JobsPanelModel {
	public static final String SOURCE = "jobs";
	public static final String TITLE = "Jobs";
	/** Industries in display order; others follow alphabetically, listings without an industry last. */
	static final List<String> INDUSTRY_ORDER =
			List.of("Farming", "Hunting", "Fishing", "Mining", "Woodcutting", "Excavation", "Brewing", "Enchanting");
	static final List<String> TIERS = List.of("Beginner", "Experienced", "Heavy");
	/** The group of jobs entries that are not listings (and listings read without their industry). */
	static final String OTHER = "Other";
	static final String INDUSTRY_MARK = "⚒ ";

	/** The item drawn before an industry heading instead of {@link #INDUSTRY_MARK}; null for unknown industries. */
	public static String industryIcon(String industry) {
		if (industry == null) return null;
		return switch (industry.toLowerCase(java.util.Locale.ROOT)) {
			case "farming" -> "minecraft:iron_hoe";
			case "fishing" -> "minecraft:fishing_rod";
			case "hunting" -> "minecraft:bow";
			case "mining" -> "minecraft:iron_pickaxe";
			case "woodcutting" -> "minecraft:iron_axe";
			case "excavation" -> "minecraft:iron_shovel";
			case "brewing" -> "minecraft:brewing_stand";
			case "enchanting" -> "minecraft:enchanting_table";
			default -> null;
		};
	}

	/** "⚒ Farming" -> "Farming"; other text unchanged. */
	public static String industryName(String headingText) {
		return headingText != null && headingText.startsWith(INDUSTRY_MARK) ? headingText.substring(INDUSTRY_MARK.length()) : headingText;
	}

	/** "Farming Heavy · Harvest Cherry Logs" (see {@link ContainerScanner#jobListingName}). */
	private static final Pattern LISTING =
			Pattern.compile("^(?:(.+?) )?(Beginner|Experienced|Heavy) · (.+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern CRATE = Pattern.compile("(?i)^golden crate$");

	/** How a line is drawn; the adapter picks the colours. */
	public enum Tone {
		/** An industry heading. */
		INDUSTRY,
		/** An entry naming the world you are in. */
		CURRENT,
		/** An entry naming no world (or the world is unknown). */
		NEUTRAL,
		/** An entry naming another world. */
		OTHER_WORLD,
		/** An estimate at its target ("✓?"), not confirmed by a menu read. */
		AT_CAP,
		/** Read complete: hand it in. */
		DONE
	}

	/**
	 * One panel row in three columns: {@code tag} (the world the job is tied to, e.g. "TR"; "" if none), {@code text}
	 * (an industry heading, or the
	 * target without verb or world) and {@code right} (a short count, right-aligned). Unused columns are "".
	 */
	public record Line(String tag, String text, String right, Tone tone, Rarity rarity, Activity activity, double progress) {
		public Line(String tag, String text, String right, Tone tone, Rarity rarity, Activity activity) {
			this(tag, text, right, tone, rarity, activity, -1);
		}

		public Line(String tag, String text, String right, Tone tone, Rarity rarity) {
			this(tag, text, right, tone, rarity, Activity.NONE);
		}

		/** A row without a rarity (headings, non-fish targets). */
		public Line(String tag, String text, String right, Tone tone) {
			this(tag, text, right, tone, null);
		}
	}

	/** The panel's title (with the crate when known) and its lines; empty lines = nothing to show. */
	public record Model(String title, List<Line> lines) {}

	private JobsPanelModel() {}

	/** True for entries that belong in this panel (and leave the tracker HUD while it is on). */
	public static boolean isJob(Trackable t) {
		return t != null && SOURCE.equals(t.source());
	}

	/**
	 * Builds the panel from {@code rows} (all tracked rows, any source). {@code hidden} says which ids the picker
	 * hid, {@code objectives} gives an entry's stored objective (may return null) and {@code relevance} how an
	 * entry relates to the current world.
	 */
	public static Model build(List<TrackerRow> rows, Predicate<String> hidden, Function<String, ObjectiveInfo> objectives,
			Function<Trackable, WorldScope.Relevance> relevance, java.util.Collection<String> worldNames, long now) {
		return build(rows, hidden, objectives, relevance, worldNames, now, null);
	}

	/** As above; {@code activity} says how recently each entry (by id) made progress (null: none marked). */
	public static Model build(List<TrackerRow> rows, Predicate<String> hidden, Function<String, ObjectiveInfo> objectives,
			Function<Trackable, WorldScope.Relevance> relevance, java.util.Collection<String> worldNames, long now,
			Function<String, Activity> activity) {
		String crate = null;
		Map<String, List<Item>> groups = new LinkedHashMap<>();
		for (TrackerRow r : rows) {
			Trackable t = r.item();
			if (!isJob(t) || t.name() == null || (hidden != null && hidden.test(t.id()))) continue;
			if (CRATE.matcher(t.name().trim()).matches()) {
				crate = "Crate " + count(t.current()) + "/" + count(t.max());
				continue;
			}
			ObjectiveInfo info = objectives == null ? null : objectives.apply(t.id());
			String title = EntryLabel.of(t.name(), info).title();
			Matcher m = LISTING.matcher(title);
			String industry = OTHER;
			int tier = TIERS.size();
			String text = title;
			if (m.matches()) {
				if (m.group(1) != null) industry = m.group(1).trim();
				tier = tierIndex(m.group(2));
				text = m.group(3).trim();
			}
			WorldScope.Relevance rel = relevance == null ? WorldScope.Relevance.NEUTRAL : relevance.apply(t);
			groups.computeIfAbsent(industry, k -> new ArrayList<>()).add(new Item(r, tier, text, rel));
		}
		List<String> industries = new ArrayList<>(groups.keySet());
		industries.sort(Comparator.comparingInt(JobsPanelModel::industryRank).thenComparing(s -> s.toLowerCase(Locale.ROOT)));
		List<Line> lines = new ArrayList<>();
		for (String industry : industries) {
			List<Item> items = groups.get(industry);
			items.sort(Comparator.comparingInt(Item::tier).thenComparing(Item::text));
			lines.add(new Line("", INDUSTRY_MARK + industry, "", Tone.INDUSTRY));
			for (Item it : items) {
				// Rows run Beginner → Experienced → Heavy, so the left column is free for the world restriction.
				String tag = CompactJob.worldTag(WorldScope.of(it.row().item().name(),
						objectives == null ? null : objectives.apply(it.row().item().id()), worldNames));
				ObjectiveInfo obj = objectives == null ? null : objectives.apply(it.row().item().id());
				Rarity rarity = obj == null || obj.subs() == null ? null
						: obj.subs().stream().map(sub -> Rarity.inText(sub.text())).flatMap(java.util.Optional::stream)
								.findFirst().orElse(null);
				Activity act = activity == null ? Activity.NONE : activity.apply(it.row().item().id());
				lines.add(new Line(tag, CompactJob.target(it.text(), worldNames), CompactJob.count(it.row(), now), tone(it),
						rarity, act == null ? Activity.NONE : act, progress(it.row())));
			}
		}
		return new Model(crate == null ? TITLE : TITLE + " · " + crate, List.copyOf(lines));
	}

	private record Item(TrackerRow row, int tier, String text, WorldScope.Relevance rel) {}

	/** An entry's progress meter: 1 when done, else its (estimated) fraction; -1 when it has no target. */
	static double progress(TrackerRow row) {
		if (row.complete() || row.atCap()) return 1;
		return row.shownMax() > 0 ? Math.max(0, Math.min(1, row.fraction())) : -1;
	}

	private static Tone tone(Item it) {
		if (it.row().complete()) return Tone.DONE;
		if (it.row().atCap()) return Tone.AT_CAP;
		return switch (it.rel()) {
			case CURRENT -> Tone.CURRENT;
			case OTHER -> Tone.OTHER_WORLD;
			default -> Tone.NEUTRAL;
		};
	}

	private static int tierIndex(String tier) {
		for (int i = 0; i < TIERS.size(); i++) {
			if (TIERS.get(i).equalsIgnoreCase(tier)) return i;
		}
		return TIERS.size();
	}

	/** Known industries by their place, then unknown ones, then {@link #OTHER}. */
	private static int industryRank(String industry) {
		if (OTHER.equals(industry)) return Integer.MAX_VALUE;
		for (int i = 0; i < INDUSTRY_ORDER.size(); i++) {
			if (INDUSTRY_ORDER.get(i).equalsIgnoreCase(industry)) return i;
		}
		return INDUSTRY_ORDER.size();
	}

	private static String count(double v) {
		return v == Math.rint(v) ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
	}
}
