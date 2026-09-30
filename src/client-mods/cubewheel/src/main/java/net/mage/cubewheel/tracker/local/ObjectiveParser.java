package net.mage.cubewheel.tracker.local;

import net.mage.cubewheel.tracker.local.CounterRule.Any;
import net.mage.cubewheel.tracker.local.CounterRule.AnyWorld;
import net.mage.cubewheel.tracker.local.CounterRule.Group;
import net.mage.cubewheel.tracker.local.CounterRule.Kind;
import net.mage.cubewheel.tracker.local.CounterRule.Named;
import net.mage.cubewheel.tracker.local.CounterRule.NamedWorld;
import net.mage.cubewheel.tracker.local.CounterRule.Special;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an objective line such as "Slay 1,000 Sandara Monsters" (or a job listing's "Harvest 3,127/4,773
 * Cherry Logs") into a {@link CounterRule}. Only
 * single-objective, non-hand-in items get a rule. Pure: no Minecraft/Fabric imports.
 */
public final class ObjectiveParser {
	private static final Pattern GRAMMAR = Pattern.compile(
			"^(?<verb>harvest or mine|harvest|mine|break|chop|dig|gather|kill|slay|slaughter|defeat|catch|fish)"
			// A /jobs listing embeds its counter: "Harvest 3,127/4,773 Cherry Logs"; the target is the max.
			+ "\\s+(?:\\d[\\d,]*\\s*/\\s*)?(?<n>\\d[\\d,]*)\\s+(?<noun>.+?)(?:\\s+in\\s+(?<in>.+))?$",
			Pattern.CASE_INSENSITIVE);
	/** Whole-noun groups (singular). A null value means "no rule" (bosses are not counted). */
	private static final Map<String, Optional<CounterRule.Target>> GROUPS = Map.of(
			"resource", Optional.of(new Any()),
			"block", Optional.of(new Any()),
			"crop", Optional.of(new Group("crop")),
			"mob", Optional.of(new Group("mob")),
			"monster", Optional.of(new Group("monster")),
			"log", Optional.of(new Group("logs")),
			"ore", Optional.of(new Group("ore")),
			"fish", Optional.of(new Any()));
	/** Nouns that make "harvest" mean "harvest a mature crop" rather than "break". */
	private static final Set<String> CROP_WORDS = Set.of("crop", "wheat", "carrot", "potato", "beetroot", "nether wart",
			"wart", "cocoa", "cocoa bean", "sweet berry", "berry", "melon", "pumpkin");

	private ObjectiveParser() {}

	public static Optional<CounterRule> parse(ObjectiveInfo info, Collection<String> worldTokens) {
		if (info == null || info.handIn() || info.subs().size() != 1) return Optional.empty();
		String line = info.subs().get(0).text();
		if (line == null) return Optional.empty();
		Matcher m = GRAMMAR.matcher(line.trim().replaceAll("\\s+", " "));
		if (!m.matches()) return Optional.empty();
		long target;
		try {
			target = Long.parseLong(m.group("n").replace(",", ""));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
		if (target <= 0) return Optional.empty();

		// World names compared as keys: "Burning Lands", "burning_lands" and "burninglands" are one world.
		Set<String> keys = new LinkedHashSet<>();
		if (worldTokens != null) {
			for (String t : worldTokens) {
				String k = WorldResolver.key(t);
				if (!k.isEmpty()) keys.add(k);
			}
		}
		CounterRule.World world = new AnyWorld();
		String noun = m.group("noun").toLowerCase(Locale.ROOT);
		// A leading world name of one or two words, as long as a noun is left after it.
		String[] words = noun.split(" ");
		for (int n = Math.min(2, words.length - 1); n >= 1; n--) {
			String key = WorldResolver.key(String.join(" ", java.util.Arrays.copyOfRange(words, 0, n)));
			if (keys.contains(key)) {
				world = new NamedWorld(key);
				noun = String.join(" ", java.util.Arrays.copyOfRange(words, n, words.length));
				break;
			}
		}
		if (m.group("in") != null) {
			String in = m.group("in").toLowerCase(Locale.ROOT);
			world = in.contains("special") ? new Special() : new NamedWorld(WorldResolver.key(in.replaceFirst("^the\\s+", "")));
		}
		if (info.special() && world instanceof AnyWorld) world = new Special();

		String singular = Singular.phrase(noun);
		String last = singular.substring(singular.lastIndexOf(' ') + 1);
		if (last.equals("boss")) return Optional.empty();
		CounterRule.Target what = GROUPS.containsKey(singular) ? GROUPS.get(singular).orElse(null) : new Named(singular);
		if (what == null) return Optional.empty();

		Kind kind = switch (m.group("verb").toLowerCase(Locale.ROOT)) {
			case "harvest" -> isCropNoun(what) ? Kind.HARVEST : Kind.BREAK;
			case "kill", "slay", "slaughter", "defeat" -> Kind.KILL;
			case "catch", "fish" -> Kind.FISH;
			default -> Kind.BREAK;
		};
		if (kind == Kind.FISH && !(what instanceof Any)) return Optional.empty(); // specific fish: not in v1
		if (kind != Kind.FISH && singular.equals("fish")) return Optional.empty();
		return Optional.of(new CounterRule(kind, target, what, world));
	}

	private static boolean isCropNoun(CounterRule.Target what) {
		if (what instanceof Group g) return g.group().equals("crop");
		return what instanceof Named n && CROP_WORDS.contains(n.singular());
	}
}
