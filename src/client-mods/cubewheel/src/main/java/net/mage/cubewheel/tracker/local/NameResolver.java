package net.mage.cubewheel.tracker.local;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A name for an unnamed hitbox mob (ModelEngine-style models: an invisible slime or interaction entity
 * carries the hits, a separate entity shows the name). In order: a named entity riding it or that it
 * rides; the nearest name tag (text display, armor stand, other display) within {@link #RANGE} blocks;
 * the nearest other custom-named entity within range. Pure: no Minecraft/Fabric imports.
 */
public final class NameResolver {
	public static final double RANGE = 3;

	/** How a candidate relates to the hit entity; declaration order is preference. */
	public enum Relation { RIDER, VEHICLE, TAG, OTHER }

	/**
	 * A named entity near the hit entity; {@code source} describes it for capture ("text_display #12"),
	 * {@code entityId} is its id (-1 if unknown).
	 */
	public record Candidate(Relation relation, String name, double distanceSq, String source, int entityId) {
		public Candidate(Relation relation, String name, double distanceSq, String source) {
			this(relation, name, distanceSq, source, -1);
		}
	}

	private NameResolver() {}

	public static Optional<Candidate> pick(List<Candidate> candidates) {
		if (candidates == null) return Optional.empty();
		return candidates.stream()
				.filter(c -> c != null && c.name() != null && !c.name().isBlank())
				.filter(c -> c.relation() == Relation.RIDER || c.relation() == Relation.VEHICLE || c.distanceSq() <= RANGE * RANGE)
				.min(Comparator.comparing(Candidate::relation).thenComparingDouble(Candidate::distanceSq));
	}

	/** A text display's first non-blank line, trimmed ("Tiger\n❤ 20" -> "Tiger"), or null. */
	public static String firstLine(String text) {
		if (text == null) return null;
		for (String line : text.split("\\R")) {
			if (!line.isBlank()) return line.trim();
		}
		return null;
	}
}
