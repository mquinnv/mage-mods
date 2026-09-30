package net.mage.cubewheel.tracker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.mage.cubewheel.tracker.ContainerScanner.ItemView;

/**
 * Values a server menu states about the player that aren't progress counters, e.g. the /party menu's
 * "Upgrade Party Level … Current Level: 55". They keep matching objectives ("Reach Party Level 55") current
 * the same way the sidebar keeps "Reach 2,500 Skill Level" current. Pure: no Minecraft/Fabric imports.
 */
public final class MenuFacts {
	private static final Pattern UPGRADE = Pattern.compile("(?i)^Upgrade (.+?)\\s*$");
	private static final Pattern CURRENT_LEVEL = Pattern.compile("(?i)^Current Level:\\s*([\\d,]+)\\s*$");
	/** Fact name → objectives it answers. */
	private static final Map<String, Pattern> LINKS = Map.of(
			"Party Level", Pattern.compile("(?i)\\breach party level [\\d,]+"));

	private MenuFacts() {}

	/** Facts stated by {@code items}: an "Upgrade <X>" item carrying "Current Level: N" gives {@code X → N}. */
	public static Map<String, Double> of(List<ItemView> items) {
		Map<String, Double> facts = new LinkedHashMap<>();
		if (items == null) return facts;
		for (ItemView item : items) {
			if (item == null || item.lore() == null) continue;
			String subject = null;
			Double level = null;
			for (String raw : item.lore()) {
				String line = ContainerScanner.strip(raw).trim();
				Matcher u = UPGRADE.matcher(line);
				if (u.matches()) subject = u.group(1);
				Matcher c = CURRENT_LEVEL.matcher(line);
				if (c.matches()) {
					OptionalDouble v = ProgressExtractor.parseNumber(c.group(1));
					if (v.isPresent()) level = v.getAsDouble();
				}
			}
			if (subject != null && level != null) facts.put(subject, level);
		}
		return facts;
	}

	/** Applies known facts to the objectives they answer; returns how many entries changed. */
	public static int apply(Map<String, Double> facts, TrackerStore store, long now) {
		if (facts == null || store == null) return 0;
		int touched = 0;
		for (Map.Entry<String, Double> fact : facts.entrySet()) {
			Pattern names = LINKS.get(fact.getKey());
			if (names != null) touched += store.applyLiveValue(names, fact.getValue(), now);
		}
		return touched;
	}
}
