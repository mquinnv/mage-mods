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

	/**
	 * Sources whose items are tracked only when they say "Progress: …" (or are marked complete). Quest menus
	 * are recognised by one item's lore mentioning a quest with a Progress line, so an unrelated menu can be
	 * tagged as one: the auction house was (Michael, 2026-10-07), and every listing with a percentage or ratio
	 * in its lore ("Item sales are taxed 3%", "+10% Woodcutting MCMMO XP") became a tracker entry. Real quest
	 * items always carry an explicit Progress line. Job listings, prestige ranks and challenges keep their
	 * counters inside the objective text and are not gated.
	 */
	private static final Set<String> PROGRESS_LINE_SOURCES = Set.of("pquests", "menu");

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
		int updated = 0;
		for (ItemView item : items) {
			if (item == null || item.lore() == null) continue;
			String name = strip(item.name()).trim();
			if (name.isEmpty()) continue;
			List<String> lore = new ArrayList<>(item.lore().size());
			for (String line : item.lore()) lore.add(strip(line));
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
				p = ProgressExtractor.extract(List.of(objective));
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
			boolean changed = store.update(source, display, progress, now);
			// The objective text feeds local counting (tracker.local); a read is authoritative, so any
			// estimate for this entry was just dropped by update().
			changed |= store.setObjective(Trackable.idOf(source, display), ObjectiveExtractor.extract(name, lore, listingObjective));
			if (seen != null) seen.accept(Trackable.idOf(source, display));
			if (changed) updated++;
		}
		if (listingsPage && industry != null) {
			// Listings of this industry that are no longer offered were rerolled or completed.
			String prefix = industry + " ";
			updated += store.forgetUnpinned(t -> source.equals(t.source()) && t.name() != null
					&& t.name().startsWith(prefix) && isJobListingName(t.name().substring(prefix.length()))
					&& !listed.contains(t.name()));
		}
		return updated;
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
