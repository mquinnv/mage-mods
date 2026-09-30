package com.mage.cubewheel.sva;

import java.util.Map;
import java.util.TreeSet;

/** Text for the SVA tooltip line and the catalog screen. Pure. */
public final class SvaFormat {
	private SvaFormat() {}

	/**
	 * "✦ SVA · Circulation: 107", with " · owned" (" ×N" for several) when the player owns one of the matched
	 * SVAs; several candidates list each distinct circulation ("27 / 342"). Null for no match.
	 */
	public static String tooltipLine(SvaCatalog.Match match, Map<String, Integer> owned) {
		if (match == null || match.isEmpty()) return null;
		TreeSet<Integer> circ = new TreeSet<>();
		int count = 0;
		for (Sva s : match.svas()) {
			circ.add(s.circulation());
			count += owned.getOrDefault(s.itemType(), 0);
		}
		StringBuilder b = new StringBuilder("✦ SVA · Circulation: ");
		boolean first = true;
		for (int c : circ) {
			if (!first) b.append(" / ");
			b.append(c);
			first = false;
		}
		if (count > 0) b.append(" · owned");
		if (count > 1) b.append(" ×").append(count);
		return b.toString();
	}

	/** "just now", "5 min ago", "3 h ago", "2 d ago". */
	public static String age(long ms) {
		long min = Math.max(0, ms) / 60_000;
		if (min < 1) return "just now";
		if (min < 60) return min + " min ago";
		long h = min / 60;
		if (h < 48) return h + " h ago";
		return (h / 24) + " d ago";
	}
}
