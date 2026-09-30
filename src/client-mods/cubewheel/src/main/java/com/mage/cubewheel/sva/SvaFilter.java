package com.mage.cubewheel.sva;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Catalog screen filtering, sorting and compare marks. Pure. */
public final class SvaFilter {
	private SvaFilter() {}

	public enum Show {
		ALL("All"), OWNED("Only owned"), NOT_OWNED("Not owned");

		public final String label;

		Show(String label) {
			this.label = label;
		}

		public Show next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	public enum Sort {
		NAME("A–Z"), RAREST("Rarest"), MOST_COMMON("Most common");

		public final String label;

		Sort(String label) {
			this.label = label;
		}

		public Sort next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	/** Who owns an SVA when comparing with another player. */
	public enum Mark { NONE, MINE, THEIRS, BOTH }

	/** {@code all} is expected in name order (as {@link SvaCatalog#all()} is); {@code mine} may be empty. */
	public static List<Sva> apply(List<Sva> all, String query, Show show, Set<String> mine, Sort sort) {
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<Sva> out = new ArrayList<>();
		for (Sva s : all) {
			boolean owned = mine.contains(s.itemType());
			if (show == Show.OWNED && !owned) continue;
			if (show == Show.NOT_OWNED && owned) continue;
			if (!s.matches(q)) continue;
			out.add(s);
		}
		switch (sort) {
			case RAREST -> out.sort(Comparator.comparingInt(Sva::circulation).thenComparing(Sva::sortKey));
			case MOST_COMMON -> out.sort(Comparator.comparingInt(Sva::circulation).reversed().thenComparing(Sva::sortKey));
			default -> { }
		}
		return out;
	}

	/** {@code theirs} null = not comparing. */
	public static Mark mark(String itemType, Set<String> mine, Set<String> theirs) {
		boolean m = mine.contains(itemType);
		boolean t = theirs != null && theirs.contains(itemType);
		return m && t ? Mark.BOTH : m ? Mark.MINE : t ? Mark.THEIRS : Mark.NONE;
	}
}
