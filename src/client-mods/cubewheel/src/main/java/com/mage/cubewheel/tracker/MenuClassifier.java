package com.mage.cubewheel.tracker;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.mage.cubewheel.tracker.ContainerScanner.ItemView;

/**
 * Decides which tracker source a server GUI belongs to. Pure: no Minecraft/Fabric imports.
 *
 * <p>ManaCube menu titles are custom-font glyphs ("⻔⻔⻔⻔⻔⻔⻔⻔䮄") that draw a picture, not text, so
 * the configured title patterns rarely match there. Menus are recognised by what their items say.
 */
public final class MenuClassifier {
	private static final Pattern PRESTIGE_RANK = Pattern.compile("^Rank \\[✪\\d+]");
	private static final Pattern PRESTIGE_LEVEL = Pattern.compile("^Prestige \\d+ - ");
	/** A menu nobody told us about is still tracked when this many of its items show explicit progress. */
	private static final int GENERIC_MIN_ITEMS = 2;

	private MenuClassifier() {}

	public static Optional<String> classify(String title, List<ItemView> items, Map<String, String> titleSources) {
		Optional<String> byTitle = TrackerSources.match(title, titleSources);
		if (byTitle.isPresent() || items == null) return byTitle;
		int progressItems = 0;
		for (ItemView item : items) {
			if (item == null) continue;
			String name = ContainerScanner.strip(item.name()).trim();
			List<String> lore = item.lore() == null ? List.of() : item.lore();
			if (PRESTIGE_RANK.matcher(name).find() || PRESTIGE_LEVEL.matcher(name).find()) return Optional.of("prestige");
			if (name.equals("JOBS PROFILE") || anyLine(lore, "browse job listings")) return Optional.of("jobs");
			if (name.endsWith(" Quests") || (anyLine(lore, "quest") && anyLineStarts(lore, "progress:"))) {
				return Optional.of("pquests");
			}
			if (anyLineStarts(lore, "progress:")) progressItems++;
		}
		return progressItems >= GENERIC_MIN_ITEMS ? Optional.of("menu") : Optional.empty();
	}

	private static boolean anyLine(List<String> lore, String needle) {
		for (String line : lore) {
			if (line != null && ContainerScanner.strip(line).toLowerCase(Locale.ROOT).contains(needle)) return true;
		}
		return false;
	}

	private static boolean anyLineStarts(List<String> lore, String prefix) {
		for (String line : lore) {
			if (line != null && ContainerScanner.strip(line).trim().toLowerCase(Locale.ROOT).startsWith(prefix)) return true;
		}
		return false;
	}
}
