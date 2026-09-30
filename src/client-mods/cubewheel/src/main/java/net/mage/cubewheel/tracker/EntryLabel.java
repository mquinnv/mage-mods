package net.mage.cubewheel.tracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.mage.cubewheel.tracker.local.ObjectiveInfo;

/**
 * What a HUD entry shows so it says what to do, not just a quest's name ("King of the Jungle"). A single
 * objective is appended to the title; several become detail lines with their own percentages. Names that
 * already carry their objective (job listings, prestige ranks) are left alone. Pure: no Minecraft/Fabric imports.
 */
public record EntryLabel(String title, List<String> details) {
	/** In-line counters ("16/64 ") and trailing bracketed progress ("[Lvl 1902/2,500]"). */
	private static final String COUNTER = "\\s*\\d[\\d,.]*\\s*/\\s*\\d[\\d,.]*\\s*";
	private static final String BRACKETS = "\\s*\\[[^\\]]*]\\s*$";

	public static EntryLabel of(String name, ObjectiveInfo info) {
		String title = name == null ? "" : name;
		if (info == null || info.subs() == null || info.subs().isEmpty()) return new EntryLabel(title, List.of());
		if (info.subs().size() == 1) {
			String objective = clean(info.subs().get(0).text());
			if (objective.isEmpty() || normalise(title).contains(normalise(objective))) return new EntryLabel(title, List.of());
			return new EntryLabel(title + " · " + objective, List.of());
		}
		List<String> details = new ArrayList<>();
		for (ObjectiveInfo.Sub sub : info.subs()) {
			String objective = clean(sub.text());
			if (objective.isEmpty()) continue;
			details.add(sub.percent() == null ? objective : Math.round(sub.percent()) + "% " + objective);
		}
		return new EntryLabel(title, List.copyOf(details));
	}

	private static String clean(String text) {
		if (text == null) return "";
		return text.replaceAll(BRACKETS, "").replaceAll(COUNTER, " ").replaceAll("\\s+", " ").trim();
	}

	private static String normalise(String s) {
		return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}
}
