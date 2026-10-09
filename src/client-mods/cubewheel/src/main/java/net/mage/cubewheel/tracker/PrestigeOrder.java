package net.mage.cubewheel.tracker;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prestige ranks are claimed in order: a met rank behind an unmet lower one cannot be handed in yet. Such a rank
 * is no action item, so the Focus and Quests panels leave it out until the lower rank is met (Michael 2026-10-08:
 * "it says ready to turn in, but it is blocked by earlier prestige missions … there's no action item for me until
 * it gets unblocked"). Pure: no Minecraft/Fabric imports.
 */
public final class PrestigeOrder {
	/** "Rank [✪8] · Catch 1,000 Fish" -> 8. */
	private static final Pattern RANK = Pattern.compile("\\[\u272A(\\d+)]");

	private PrestigeOrder() {}

	/** The prestige rank an entry is ("Rank [✪8] · …" -> 8), else 0. */
	public static int rank(Trackable t) {
		if (t == null || !"prestige".equals(t.source()) || t.name() == null) return 0;
		Matcher m = RANK.matcher(t.name());
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	/** Met: read complete, or the local estimate reached the target. */
	public static boolean met(TrackerRow r) {
		return r != null && (r.complete() || r.atCap());
	}

	/** The lowest prestige rank not yet met among {@code rows} (the one to work on), else 0. */
	public static int nextRank(Collection<TrackerRow> rows) {
		int next = 0;
		if (rows == null) return 0;
		for (TrackerRow r : rows) {
			int rank = rank(r.item());
			if (rank > 0 && !met(r) && (next == 0 || rank < next)) next = rank;
		}
		return next;
	}

	/** {@code r} is a met prestige rank above {@code nextRank} (from {@link #nextRank}): claimable only later. */
	public static boolean blocked(TrackerRow r, int nextRank) {
		if (r == null || nextRank <= 0) return false;
		int rank = rank(r.item());
		return rank > nextRank && met(r);
	}
}
