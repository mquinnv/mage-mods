package net.mage.cubewheel.tracker.local;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which worlds a tracked entry can be worked on in, read from its name and objective lines ("Slay 16/64
 * Tigers in Tangleroots", "Harvest or Mine 10,000 Wolfhaven Resources", a prestige objective that must be
 * done in the special worlds), and how that relates to the current world. Drives
 * {@code tracker.worldFilter}. Pure: no Minecraft/Fabric imports.
 */
public final class WorldScope {
	private static final Pattern SPLIT = Pattern.compile("[^a-z0-9]+");

	/** How an entry relates to the current world. */
	public enum Relevance { CURRENT, NEUTRAL, OTHER }

	/** {@code tracker.worldFilter}. */
	public enum Mode {
		/** Current world's entries first, other worlds' last. */
		SORT,
		/** As SORT, and other worlds' entries leave the HUD unless pinned. */
		HIDE,
		OFF;

		/** Case-insensitive; anything unknown is the default, SORT. */
		public static Mode parse(String s) {
			if (s == null) return SORT;
			return switch (s.trim().toLowerCase(Locale.ROOT)) {
				case "hide" -> HIDE;
				case "off" -> OFF;
				default -> SORT;
			};
		}
	}

	/** World keys ({@link WorldResolver#key}) named by the entry, and whether it needs a special world. */
	public record Scope(Set<String> worlds, boolean special) {
		public static final Scope NONE = new Scope(Set.of(), false);

		public Scope {
			worlds = worlds == null ? Set.of() : Set.copyOf(worlds);
		}

		public boolean scoped() {
			return special || !worlds.isEmpty();
		}
	}

	/** The world keys of the last {@code worldNames} seen: the config's list rarely changes, so they are worked out once. */
	private record Wanted(List<String> names, Set<String> keys) {}

	private static volatile Wanted lastWanted = new Wanted(List.of(), Set.of());

	private WorldScope() {}

	/** The keys ({@link WorldResolver#key}) of {@code worldNames}, blanks left out; cached for the last list asked about. */
	static Set<String> wantedKeys(Collection<String> worldNames) {
		if (worldNames == null || worldNames.isEmpty()) return Set.of();
		Wanted w = lastWanted;
		if (worldNames instanceof List<?> && w.names().equals(worldNames)) return w.keys();
		Set<String> keys = new LinkedHashSet<>();
		for (String n : worldNames) {
			String k = WorldResolver.key(n);
			if (!k.isEmpty()) keys.add(k);
		}
		Set<String> frozen = java.util.Collections.unmodifiableSet(keys);
		lastWanted = new Wanted(java.util.Collections.unmodifiableList(new ArrayList<>(worldNames)), frozen);
		return frozen;
	}

	/** The configured {@code worldNames} that {@code name} or {@code info}'s objective lines mention, as one or two words. */
	public static Scope of(String name, ObjectiveInfo info, Collection<String> worldNames) {
		List<String> texts = new ArrayList<>();
		if (name != null) texts.add(name);
		if (info != null) for (ObjectiveInfo.Sub sub : info.subs()) if (sub.text() != null) texts.add(sub.text());
		Set<String> wanted = wantedKeys(worldNames);
		if (wanted.isEmpty() && (info == null || !info.special())) return Scope.NONE; // nothing to look for
		Set<String> found = new LinkedHashSet<>();
		for (String text : texts) {
			String[] words = SPLIT.split(text.toLowerCase(Locale.ROOT));
			for (int i = 0; i < words.length; i++) {
				if (words[i].isEmpty()) continue;
				String one = WorldResolver.key(words[i]);
				if (wanted.contains(one)) found.add(one);
				if (i + 1 < words.length) {
					String two = WorldResolver.key(words[i] + words[i + 1]);
					if (wanted.contains(two)) found.add(two);
				}
			}
		}
		boolean special = info != null && info.special();
		return found.isEmpty() && !special ? Scope.NONE : new Scope(found, special);
	}

	/** NEUTRAL when the entry is not world-scoped or the current world is unknown. */
	public static Relevance relevance(Scope scope, WorldInfo at) {
		if (scope == null || !scope.scoped() || at == null || !at.known()) return Relevance.NEUTRAL;
		for (String w : scope.worlds()) if (at.tokens().contains(w)) return Relevance.CURRENT;
		if (scope.special() && at.special()) return Relevance.CURRENT;
		return Relevance.OTHER;
	}

	/**
	 * True when {@code rule} cannot be worked on in {@code at}: a bare vanilla job naming a specific thing
	 * ("Mine 245 Coal", "Harvest 850 Beetroot", "Slay 120 Zombies", "Catch 61 YellowSeaShroom": world
	 * {@link CounterRule.AnyWorld}, target {@link CounterRule.Named}) while in a mana world. The mana worlds have
	 * their own variants of those things and ManaCube does not credit the bare job there
	 * ({@code RuleMatcher} already refuses mana-world ore for such rules), so the Jobs panel treats the listing as
	 * another world's (Michael 2026-10-07). Anything else stays possible: {@link CounterRule.Any} and
	 * {@link CounterRule.Group} targets (Resources, Monsters, Fish, ores, crops) exist everywhere, rules naming a
	 * world or needing a special one are judged by {@link #relevance}, and a null rule (nothing parsed) is unknown,
	 * not impossible. False outside a known mana world and for a null {@code at}.
	 */
	public static boolean impossibleIn(CounterRule rule, WorldInfo at) {
		if (at == null || !at.known() || !at.special() || rule == null) return false;
		return rule.world() instanceof CounterRule.AnyWorld && rule.what() instanceof CounterRule.Named;
	}
}
