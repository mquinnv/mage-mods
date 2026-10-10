package net.mage.cubewheel.tracker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.mage.cubewheel.tracker.local.ObjectiveExtractor;

/** Turns the items of a server GUI into tracker updates. Pure: no Minecraft/Fabric imports. */
public final class ContainerScanner {
	/** The filler item of a job listings page names its industry: "FARMING INDUSTRY". */
	private static final Pattern INDUSTRY = Pattern.compile("(?i)^(.+?)\\s+INDUSTRY$");
	/** The "current/max" counter inside a job objective ("Harvest 3,127/4,773 Cherry Logs"). */
	private static final Pattern COUNTER = Pattern.compile(
			"\\s*(?<![\\d.,])\\d[\\d,]*(?:\\.\\d+)?[kKmM]?\\s*/\\s*\\d[\\d,]*(?:\\.\\d+)?[kKmM]?(?![A-Za-z\\d])");
	/** The first whole number standing on its own in an objective ("Help defeat 1 Golden Knight": 1). */
	private static final Pattern TARGET = Pattern.compile("(?<![\\d.,/])(\\d[\\d,]*[kKmM]?)(?![\\d.,/A-Za-z])");

	/**
	 * Sources whose items are tracked only when they say "Progress: …" (or are marked complete). Quest menus
	 * are recognised by one item's lore mentioning a quest with a Progress line, so an unrelated menu can be
	 * tagged as one: the auction house was (Michael, 2026-10-07), and every listing with a percentage or ratio
	 * in its lore ("Item sales are taxed 3%", "+10% Woodcutting MCMMO XP") became a tracker entry. Real quest
	 * items always carry an explicit Progress line. Job listings, prestige ranks and challenges keep their
	 * counters inside the objective text and are not gated.
	 */
	private static final Set<String> PROGRESS_LINE_SOURCES = Set.of("pquests", "menu");

	/**
	 * The weekly challenge in the quests menu's bottom row: its first lore line names the kind ("Monster Slaying
	 * Challenge", "Weekly Farm Challenge"). It is no party quest: it rotates every week, so it is filed under
	 * "challenges" and the week before it is forgotten on the read that shows the new one (Michael 2026-10-10: a
	 * "Sandara Slayer" from Oct 3 lingered as a party quest, crediting kills, a week after its slot held "Carrot Grind").
	 */
	static final String CHALLENGES = "challenges";
	private static final Pattern CHALLENGE_KIND = Pattern.compile("(?i)\\bchallenge$");

	/** One non-empty container slot: registry id, hover name and lore lines as plain strings. */
	public record ItemView(int slot, String id, String name, List<String> lore) {}

	private ContainerScanner() {}

	/**
	 * Updates {@code store} with every named item whose lore holds progress; returns how many entries
	 * actually changed (see {@link TrackerStore#update}), so 0 means there is nothing new to save.
	 */
	public static int scan(String source, List<ItemView> items, TrackerStore store, long now) {
		return scan(source, items, store, now, null);
	}

	/**
	 * Like {@link #scan(String, List, TrackerStore, long)}; {@code seen} (optional) receives the id of every
	 * entry this menu showed, changed or not (a refresh run counts what it confirmed).
	 */
	public static int scan(String source, List<ItemView> items, TrackerStore store, long now, Consumer<String> seen) {
		if (source == null || items == null || store == null) return 0;
		String industry = industry(items);
		boolean listingsPage = false;
		Set<String> listed = new HashSet<>();
		Set<String> tiersSeen = new HashSet<>();
		Set<String> challenges = null; // the weekly challenges this quests page shows, once it shows one
		int updated = 0;
		for (ItemView item : items) {
			if (item == null || item.lore() == null) continue;
			String name = strip(item.name()).trim();
			if (name.isEmpty()) continue;
			List<String> lore = new ArrayList<>(item.lore().size());
			for (String line : item.lore()) lore.add(strip(line));
			String itemSource = source;
			if ("pquests".equals(source) && isWeeklyChallenge(lore)) {
				itemSource = CHALLENGES;
				if (challenges == null) challenges = new HashSet<>();
				challenges.add(name);
			}
			Matcher tier = MenuClassifier.JOB_LISTING.matcher(name);
			Optional<ProgressExtractor.Progress> p;
			String display;
			String listingObjective = null;
			if (tier.matches()) {
				// A job listing: named after industry, tier and objective; progress is the objective's own
				// counter, never the hand-in line below it.
				listingsPage = true;
				String objective = firstNonBlank(lore);
				if (objective == null) continue;
				listingObjective = objective;
				display = jobListingName(industry, tier.group(1), objective);
				listed.add(display);
				tiersSeen.add(titleCase(tier.group(1)));
				p = ProgressExtractor.extract(List.of(objective));
				// "Help defeat 1 Golden Knight in Tangleroots" (2026-10-08) carries no counter at all: a one-off boss
				// job reads as 0 of its stated number until it is handed in (the menu never says otherwise).
				if (p.isEmpty()) p = targetOnly(objective);
			} else {
				if (PROGRESS_LINE_SOURCES.contains(source) && !MenuClassifier.anyLineStarts(lore, "progress")
						&& !isMarkedComplete(lore)) {
					continue;
				}
				p = ProgressExtractor.extract(lore);
				display = null;
			}
			if (p.isEmpty()) continue;
			ProgressExtractor.Progress progress = p.get();
			// "COMPLETED" wins over a lagging number (a quest can read 99% and be done).
			if (isMarkedComplete(lore)) progress = new ProgressExtractor.Progress(progress.max(), progress.max());
			if (display == null) display = displayName(name, lore);
			if (itemSource != source) {
				// Read as a party quest before this build: the same name under "pquests" is that stale copy.
				final String stale = display;
				updated += store.forgetUnpinned(t -> source.equals(t.source()) && stale.equals(t.name()));
			}
			boolean changed = store.update(itemSource, display, progress, now);
			// The objective text feeds local counting (tracker.local); a read is authoritative, so any
			// estimate for this entry was just dropped by update().
			changed |= store.setObjective(Trackable.idOf(itemSource, display), ObjectiveExtractor.extract(name, lore, listingObjective));
			// A listing's reward lines ("- 20 Job XP (Hunting)", "- $70,000") feed the Focus panel.
			if (listingObjective != null) changed |= store.setRewards(Trackable.idOf(itemSource, display), rewards(lore));
			if (seen != null) seen.accept(Trackable.idOf(itemSource, display));
			if (changed) updated++;
		}
		if (challenges != null) {
			// The slot shows this week's challenge; a stored one it no longer shows is last week's, over for good.
			final Set<String> current = challenges;
			updated += store.forgetUnpinned(t -> CHALLENGES.equals(t.source()) && !current.contains(t.name()));
		}
		if (listingsPage && industry != null) {
			// A stored listing of this industry whose tier now shows a different job was rerolled or handed in.
			// A tier that is simply absent is left alone: clicking a listing (to hand in, or by accident) makes
			// ManaCube re-send the page with that slot empty for a moment while it processes the click
			// (capture 2026-10-01 14:34:25), and forgetting on that read dropped the entry and its local
			// estimate until the next read re-added it at zero.
			String prefix = industry + " ";
			updated += store.forgetUnpinned(t -> {
				if (!source.equals(t.source()) || t.name() == null || !t.name().startsWith(prefix)) return false;
				String rest = t.name().substring(prefix.length());
				return isJobListingName(rest) && tiersSeen.contains(rest.substring(0, rest.indexOf(" · ")))
						&& !listed.contains(t.name());
			});
		}
		return updated;
	}

	/** True when the first lore line names a challenge kind ("Monster Slaying Challenge", "Weekly Farm Challenge"). */
	static boolean isWeeklyChallenge(List<String> lore) {
		String first = firstNonBlank(lore);
		return first != null && CHALLENGE_KIND.matcher(first.trim()).find();
	}

	/**
	 * The reward lines under a listing's "Reward" heading, without their "- ": "20 Job XP (Hunting)", "$70,000",
	 * "1 Golden Key"; the list ends at the first line that is blank or not a "- " item. Empty without a heading.
	 */
	static List<String> rewards(List<String> lore) {
		List<String> out = new ArrayList<>();
		boolean in = false;
		for (String raw : lore) {
			String line = raw == null ? "" : raw.trim();
			if (!in) {
				if (line.equalsIgnoreCase("reward") || line.equalsIgnoreCase("rewards") || line.equalsIgnoreCase("reward:")
						|| line.equalsIgnoreCase("rewards:")) in = true;
				continue;
			}
			if (!line.startsWith("-")) break;
			String item = line.substring(1).trim();
			if (!item.isEmpty()) out.add(item);
		}
		return out;
	}

	/** A listing objective's stated number, as 0/N, when it has no "current/max" counter: "Help defeat 1 Golden Knight". */
	static Optional<ProgressExtractor.Progress> targetOnly(String objective) {
		Matcher m = TARGET.matcher(objective);
		if (!m.find()) return Optional.empty();
		java.util.OptionalDouble n = ProgressExtractor.parseNumber(m.group(1));
		if (n.isEmpty() || !(n.getAsDouble() > 0)) return Optional.empty();
		return Optional.of(new ProgressExtractor.Progress(0, n.getAsDouble()));
	}

	/** "Farming Heavy · Harvest Cherry Logs" (without an industry item: "Heavy · Harvest Cherry Logs"). */
	static String jobListingName(String industry, String tier, String objective) {
		String what = COUNTER.matcher(objective).replaceFirst("").replaceAll("\\s+", " ").trim();
		String t = titleCase(tier);
		return (industry == null ? "" : industry + " ") + t + " · " + what;
	}

	private static boolean isJobListingName(String rest) {
		int dot = rest.indexOf(" · ");
		return dot > 0 && MenuClassifier.JOB_LISTING.matcher(rest.substring(0, dot) + " Objective").matches();
	}

	/** The industry of a listings page, title-cased ("FARMING INDUSTRY" -> "Farming"); null if absent. */
	static String industry(List<ItemView> items) {
		for (ItemView item : items) {
			if (item == null) continue;
			Matcher m = INDUSTRY.matcher(strip(item.name()).trim());
			if (m.matches()) return titleCase(m.group(1));
		}
		return null;
	}

	private static String titleCase(String s) {
		StringBuilder out = new StringBuilder();
		for (String w : s.trim().split("\\s+")) {
			if (w.isEmpty()) continue;
			if (out.length() > 0) out.append(' ');
			out.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1).toLowerCase(Locale.ROOT));
		}
		return out.toString();
	}

	private static String firstNonBlank(List<String> lines) {
		for (String l : lines) {
			if (l != null && !l.isBlank()) return l.trim();
		}
		return null;
	}

	/** True when a lore line is exactly a completion marker such as "COMPLETED" or "QUEST COMPLETED". */
	static boolean isMarkedComplete(List<String> lore) {
		for (String line : lore) {
			String t = line.trim().toUpperCase(Locale.ROOT);
			if (t.equals("COMPLETED") || t.equals("QUEST COMPLETED")) return true;
		}
		return false;
	}

	/**
	 * Items whose lore has an "OBJECTIVE" heading (prestige ranks: "Rank [✪4]") are named after the
	 * objective too, without its bracketed counter: "Rank [✪4] · Reach 2,500 Skill Level".
	 */
	static String displayName(String name, List<String> lore) {
		for (int i = 0; i < lore.size() - 1; i++) {
			if (!lore.get(i).trim().equalsIgnoreCase("OBJECTIVE")) continue;
			String objective = lore.get(i + 1).replaceAll("\\s*\\[[^\\]]*]\\s*$", "").trim();
			if (!objective.isEmpty()) return name + " · " + objective;
		}
		return name;
	}

	/** Removes legacy {@code §x} formatting codes. */
	static String strip(String s) {
		return s == null ? "" : s.replaceAll("§.", "");
	}
}
