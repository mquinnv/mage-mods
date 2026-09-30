package net.mage.cubewheel.tracker.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Last-resort naming of a kill from its loot: a loot item whose leading words are a kill objective's
 * target ("Tiger Hide" -> "tiger", "Frog Legs" -> "frog"). Credits only when exactly one distinct target
 * fits (every rule with that target, e.g. a job and a quest); two different targets fitting credit nothing.
 * Pure: no Minecraft/Fabric imports.
 */
public final class LootMatch {
	/** The target read from the loot and the rule ids to credit (sorted). */
	public record Credit(String target, List<String> ruleIds) {}

	private LootMatch() {}

	public static Optional<Credit> match(List<String> lootItems, Map<String, CounterRule> rules, WorldInfo at) {
		if (lootItems == null || lootItems.isEmpty() || rules == null) return Optional.empty();
		List<String[]> loot = new ArrayList<>();
		for (String item : lootItems) loot.add(words(item));
		Map<String, List<String>> byTarget = new TreeMap<>();
		for (Map.Entry<String, CounterRule> e : rules.entrySet()) {
			CounterRule r = e.getValue();
			if (r.kind() != CounterRule.Kind.KILL || !(r.what() instanceof CounterRule.Named n)) continue;
			if (!RuleMatcher.worldMatches(r.world(), at)) continue;
			String[] want = words(n.singular());
			if (want.length == 0) continue;
			for (String[] l : loot) {
				if (startsWith(l, want)) {
					byTarget.computeIfAbsent(String.join(" ", want), k -> new ArrayList<>()).add(e.getKey());
					break;
				}
			}
		}
		if (byTarget.size() != 1) return Optional.empty();
		Map.Entry<String, List<String>> only = byTarget.entrySet().iterator().next();
		List<String> ids = new ArrayList<>(only.getValue());
		ids.sort(null);
		return Optional.of(new Credit(only.getKey(), List.copyOf(ids)));
	}

	/** Words compared singular and lower case, decorations dropped ("Frog Legs" -> [frog, leg]). */
	static String[] words(String s) {
		String name = RuleMatcher.displayName(s);
		if (name.isEmpty()) return new String[0];
		return Arrays.stream(name.split(" ")).map(Singular::word).toArray(String[]::new);
	}

	private static boolean startsWith(String[] loot, String[] want) {
		if (want.length > loot.length) return false;
		for (int i = 0; i < want.length; i++) if (!loot[i].equals(want[i])) return false;
		return true;
	}
}
