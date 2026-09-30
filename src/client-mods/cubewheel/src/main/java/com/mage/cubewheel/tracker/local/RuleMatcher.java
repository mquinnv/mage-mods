package com.mage.cubewheel.tracker.local;

import com.mage.cubewheel.tracker.local.CounterRule.Any;
import com.mage.cubewheel.tracker.local.CounterRule.AnyWorld;
import com.mage.cubewheel.tracker.local.CounterRule.Group;
import com.mage.cubewheel.tracker.local.CounterRule.Named;
import com.mage.cubewheel.tracker.local.CounterRule.NamedWorld;
import com.mage.cubewheel.tracker.local.CounterRule.Special;

/** Does a signal advance a rule? Kind, target and world must all fit. Pure: no Minecraft/Fabric imports. */
public final class RuleMatcher {
	/** Objective nouns whose block id is a different word: "Sweet Berries" grow on sweet_berry_bush. */
	private static final java.util.Map<String, String> ID_ALIASES = java.util.Map.of(
			"sweet berry", "sweet berry bush",
			"cocoa bean", "cocoa");

	private RuleMatcher() {}

	public static boolean matches(CounterRule rule, Signal signal) {
		if (rule == null || signal == null || !worldMatches(rule.world(), signal.world())) return false;
		return switch (signal) {
			case Signal.BlockBroken b -> blockMatches(rule, b);
			case Signal.MobKilled m -> rule.kind() == CounterRule.Kind.KILL && !m.groups().contains("player")
					&& targetMatches(rule.what(), m.typeId(), m.name(), m.groups());
			case Signal.FishCaught f -> rule.kind() == CounterRule.Kind.FISH && rule.what() instanceof Any;
		};
	}

	private static boolean blockMatches(CounterRule rule, Signal.BlockBroken b) {
		if (rule.kind() != CounterRule.Kind.BREAK && rule.kind() != CounterRule.Kind.HARVEST) return false;
		if (b.crop() && !b.mature()) return false; // unripe crops never count
		if (rule.kind() == CounterRule.Kind.HARVEST && !b.crop()) return false;
		// "Resources"/"Blocks": instabreak vegetation is not a resource (plugins count real blocks).
		if (rule.what() instanceof Any && b.trivial() && !b.crop()) return false;
		return targetMatches(rule.what(), b.id(), b.name(), b.groups());
	}

	/** Unknown worlds never satisfy a world-scoped rule (under-count rather than over-count). */
	static boolean worldMatches(CounterRule.World want, WorldInfo at) {
		if (want instanceof AnyWorld) return true;
		if (at == null || !at.known()) return false;
		if (want instanceof Special) return at.special();
		return want instanceof NamedWorld n && (at.tokens().contains(n.token()) || at.tokens().contains(WorldResolver.key(n.token())));
	}

	/**
	 * Named targets match the registry path read as words ("oak_log" -> "oak log", singularised so
	 * "potatoes" is "potato"), or a display name that equals or ends with the name ("Mana Wolf" for
	 * "wolf" and "mana wolf").
	 */
	static boolean targetMatches(CounterRule.Target what, String id, String name, java.util.Set<String> groups) {
		return switch (what) {
			case Any a -> true;
			case Group g -> groups.contains(g.group());
			case Named n -> {
				String want = n.singular();
				String path = id == null ? "" : id.substring(id.indexOf(':') + 1);
				String fromId = Singular.phrase(path);
				String fromName = Singular.phrase(name);
				yield fromId.equals(want) || fromId.equals(ID_ALIASES.get(want))
						|| fromName.equals(want) || fromName.endsWith(" " + want);
			}
		};
	}
}
