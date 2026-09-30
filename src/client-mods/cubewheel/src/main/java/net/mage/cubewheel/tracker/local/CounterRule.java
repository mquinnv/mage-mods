package net.mage.cubewheel.tracker.local;

/**
 * What a tracked objective counts, parsed from its text: kind of action, the objective's target number,
 * what must be acted on and where. Pure: no Minecraft/Fabric imports.
 */
public record CounterRule(Kind kind, long target, Target what, World world) {
	public enum Kind { BREAK, HARVEST, KILL, FISH }

	public sealed interface Target permits Any, Group, Named {}

	/** Anything of the kind (any block, any fish). */
	public record Any() implements Target {}

	/** A group: crop, mob, monster, ore, logs. */
	public record Group(String group) implements Target {}

	/** A singular name ("stone", "mana wolf") matched against registry ids and display names. */
	public record Named(String singular) implements Target {}

	public sealed interface World permits AnyWorld, Special, NamedWorld {}

	public record AnyWorld() implements World {}

	/** One of the configured special worlds. */
	public record Special() implements World {}

	/** A world token, singularised ("tangleroot"). */
	public record NamedWorld(String token) implements World {}
}
