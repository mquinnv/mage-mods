package net.mage.cubewheel.tracker.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Last-resort naming of a kill (or catch) from its loot: a loot item containing a kill objective's target
 * as whole words, compared singular ("Tiger Hide" -> "tiger", "Frog Legs" -> "frog", "Sad Firefly" ->
 * "firefly" for "Catch 61 Fireflies"); only when no whole name fits, a multi-word target by its last word ("Moose
 * Skin" -> "zombie moose"). The "+N Mana" entry is never loot. Credits only when exactly one
 * distinct target fits (every rule with that target, e.g. a job and a quest); two different targets
 * fitting credit nothing.
 * Pure: no Minecraft/Fabric imports.
 */
public final class LootMatch {
	/** The target read from the loot and the rule ids to credit (sorted). */
	public record Credit(String target, List<String> ruleIds) {}

	private LootMatch() {}

	public static Optional<Credit> match(List<String> lootItems, Map<String, CounterRule> rules, WorldInfo at) {
		if (lootItems == null || lootItems.isEmpty() || rules == null) return Optional.empty();
		List<String[]> loot = new ArrayList<>();
		for (String item : lootItems) {
			String[] w = words(item);
			if (w.length > 0 && !(w.length == 1 && w[0].equals("mana"))) loot.add(w);
		}
		Map<String, List<String>> byTarget = targets(loot, rules, at, false);
		// Only when no whole name fits: the mob's last word ("Zombie Moose" from "Moose Skin", "Dart Frog" from "Frog Leg").
		if (byTarget.isEmpty()) byTarget = targets(loot, rules, at, true);
		if (byTarget.size() != 1) return Optional.empty();
		Map.Entry<String, List<String>> only = byTarget.entrySet().iterator().next();
		List<String> ids = new ArrayList<>(only.getValue());
		ids.sort(null);
		return Optional.of(new Credit(only.getKey(), List.copyOf(ids)));
	}

	/**
	 * Kill targets the loot names, each with the rule ids that have it. {@code lastWord}: a multi-word target is
	 * matched by its last word alone ("moose" for "zombie moose").
	 */
	private static Map<String, List<String>> targets(List<String[]> loot, Map<String, CounterRule> rules, WorldInfo at,
			boolean lastWord) {
		Map<String, List<String>> byTarget = new TreeMap<>();
		for (Map.Entry<String, CounterRule> e : rules.entrySet()) {
			CounterRule r = e.getValue();
			if (r.kind() != CounterRule.Kind.KILL || !(r.what() instanceof CounterRule.Named n)) continue;
			if (!RuleMatcher.worldMatches(r.world(), at)) continue;
			String[] want = words(n.singular());
			if (want.length == 0 || lastWord && want.length < 2) continue;
			// "Rattle Snake" also matches loot that writes it as one word ("Rattlesnake Meat").
			String[] joined = want.length > 1 ? new String[] { String.join("", want) } : null;
			String[] last = new String[] { want[want.length - 1] };
			for (String[] l : loot) {
				boolean fits = lastWord ? contains(l, last) : contains(l, want) || joined != null && contains(l, joined);
				if (fits) {
					byTarget.computeIfAbsent(String.join(" ", want), k -> new ArrayList<>()).add(e.getKey());
					break;
				}
			}
		}
		return byTarget;
	}

	/** Words compared singular and lower case, decorations dropped ("Frog Legs" -> [frog, leg]). */
	static String[] words(String s) {
		String name = RuleMatcher.displayName(s);
		if (name.isEmpty()) return new String[0];
		return Arrays.stream(name.split(" ")).map(Singular::word).toArray(String[]::new);
	}

	/** Is {@code want} a run of whole words in {@code loot}? Every word is singular, so plurals never matter. */
	private static boolean contains(String[] loot, String[] want) {
		outer:
		for (int start = 0; start + want.length <= loot.length; start++) {
			for (int i = 0; i < want.length; i++) if (!loot[start + i].equals(want[i])) continue outer;
			return true;
		}
		return false;
	}
}
