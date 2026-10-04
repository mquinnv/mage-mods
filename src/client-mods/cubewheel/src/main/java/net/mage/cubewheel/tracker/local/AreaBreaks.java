package net.mage.cubewheel.tracker.local;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Area breaks: blocks the <em>server</em> breaks for the player (mcMMO Tree Feller, ManaCube harvester and
 * hammer tools), which never reach the client's own-break event. An own break, the start of breaking a block
 * (attack) or a mcMMO "&lt;ABILITY&gt; ACTIVATED" line opens a short <em>window</em> around that block; while
 * one is open, a server block update that turns a non-air, non-fluid block into air near it counts as a break
 * of the old block. Bounded against over-counting: {@link #WINDOW_TICKS} ticks, Chebyshev radius
 * {@link #RADIUS}, at most {@link #CAP} blocks per window; Tree Feller windows accept only logs, in a column
 * box ({@link #TREE_RADIUS} across, {@link #TREE_DOWN} down, {@link #TREE_UP} up) for {@link #TREE_TICKS}
 * ticks, at most {@link #TREE_CAP}. Never the same position twice while it is remembered, never a block the
 * player placed, never a plant that popped off because the block under (or over) it was just broken.
 * Pure: no Minecraft/Fabric imports.
 */
public final class AreaBreaks {
	public static final int WINDOW_TICKS = 20;
	public static final int RADIUS = 4;
	public static final int CAP = 64;
	public static final int TREE_TICKS = 40;
	public static final int TREE_RADIUS = 8;
	public static final int TREE_DOWN = 2;
	public static final int TREE_UP = 32;
	public static final int TREE_CAP = 256;
	/** How long a Tree Feller activation makes log breaks open Tree Feller windows (or until it wears off). */
	public static final int ABILITY_TICKS = 600;
	static final int MAX_WINDOWS = 16;
	static final int MAX_SEEN = 2048;
	/** Seen positions are forgotten this long after they were seen (longer than any window). */
	static final int SEEN_TICKS = 100;
	public static final long SUMMARY_INTERVAL_MS = 5_000;

	/** What opened a window. */
	public enum Trigger {
		BREAK, ATTACK, ABILITY;

		public String label() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public enum Result {
		COUNT, NOT_ARMED, NOT_BREAK, OUT_OF_RANGE, NOT_LOG, DUPLICATE, CASCADE, PLACED, CAP;

		public String label() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * A server block change, described with the state <em>before</em> it. {@code log}: the old block is a log
	 * (Tree Feller windows count only those). {@code popsOff}: a plant or other block that breaks by itself
	 * when its support goes (sugar cane, cactus, flowers, crops).
	 */
	public record Change(Pos pos, String id, int oldStateId, boolean oldAir, boolean oldFluid, boolean newAir,
			boolean log, boolean popsOff) {}

	private static final class Window {
		final Pos center;
		final Trigger trigger;
		final boolean tree;
		long until;
		int counted;

		Window(Pos center, Trigger trigger, boolean tree, long until) {
			this.center = center;
			this.trigger = trigger;
			this.tree = tree;
			this.until = until;
		}

		boolean contains(Pos p) {
			int dx = Math.abs(p.x() - center.x()), dz = Math.abs(p.z() - center.z()), dy = p.y() - center.y();
			if (tree) return dx <= TREE_RADIUS && dz <= TREE_RADIUS && dy >= -TREE_DOWN && dy <= TREE_UP;
			return dx <= RADIUS && dz <= RADIUS && Math.abs(dy) <= RADIUS;
		}

		int cap() {
			return tree ? TREE_CAP : CAP;
		}
	}

	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern WORN_OFF = Pattern.compile(
			"(?i)\\b(tree feller|super breaker|giga drill breaker|green terra)\\b[^a-z]*\\s+has\\s+worn\\s+off");

	private final ArrayDeque<Window> windows = new ArrayDeque<>();
	private final Map<Pos, Long> seen = new LinkedHashMap<>();
	private final Tally tally = new Tally();
	private long armedUntil = Long.MIN_VALUE;
	private long treeFellerUntil = Long.MIN_VALUE;

	/** An own break (already counted by the own-break path): remembered so it never counts again, and arms. */
	public void ownBreak(Pos pos, boolean log, long tick) {
		if (pos == null) return;
		see(pos, tick);
		open(pos, Trigger.BREAK, log && treeFeller(tick), tick);
	}

	/** The player started breaking {@code pos} (the server may break it, and more, itself). */
	public void attack(Pos pos, boolean log, long tick) {
		if (pos == null) return;
		open(pos, Trigger.ATTACK, log && treeFeller(tick), tick);
	}

	/**
	 * A mcMMO super ability activated ({@code treeFeller}: Tree Feller; else Super Breaker, Giga Drill Breaker or
	 * Green Terra). Tree Feller makes log breaks open Tree Feller windows for {@link #ABILITY_TICKS}. With a
	 * target block ({@code at}, may be null) a window opens there too.
	 */
	public void ability(boolean treeFeller, Pos at, long tick) {
		if (treeFeller) treeFellerUntil = tick + ABILITY_TICKS;
		if (at != null) open(at, Trigger.ABILITY, treeFeller, tick);
	}

	/** "Tree Feller has worn off": log breaks open ordinary windows again. */
	public void abilityEnded(boolean treeFeller) {
		if (treeFeller) treeFellerUntil = Long.MIN_VALUE;
	}

	public boolean treeFeller(long tick) {
		return tick <= treeFellerUntil;
	}

	/** Hot path: a single comparison. */
	public boolean armed(long tick) {
		return tick <= armedUntil;
	}

	private void open(Pos center, Trigger trigger, boolean tree, long tick) {
		long until = tick + (tree ? TREE_TICKS : WINDOW_TICKS);
		tally.trigger(trigger);
		Window existing = null;
		for (Window w : windows) {
			if (w.center.equals(center) && w.tree == tree) {
				existing = w;
				break;
			}
		}
		if (existing != null) {
			existing.until = Math.max(existing.until, until);
			windows.remove(existing);
			windows.addLast(existing); // newest last
		} else {
			windows.addLast(new Window(center, trigger, tree, until));
			while (windows.size() > MAX_WINDOWS) windows.removeFirst();
		}
		armedUntil = Math.max(armedUntil, until);
	}

	/**
	 * A server block change while (maybe) armed. {@code placed} may be null. Returns {@link Result#COUNT} when
	 * the old block should count as broken by the player; everything else is not counted.
	 */
	public Result onChange(Change c, long tick, PlacedBlocks placed) {
		Result r = decide(c, tick, placed);
		tally.result(r, c.id());
		return r;
	}

	private Result decide(Change c, long tick, PlacedBlocks placed) {
		if (!armed(tick)) return Result.NOT_ARMED;
		if (c.oldAir() || c.oldFluid() || !c.newAir()) return Result.NOT_BREAK;
		Long s = seen.get(c.pos());
		if (s != null && tick - s <= SEEN_TICKS) return Result.DUPLICATE;
		Window match = null;
		boolean notLog = false;
		Iterator<Window> it = windows.descendingIterator(); // newest first
		while (it.hasNext()) {
			Window w = it.next();
			if (w.until < tick || !w.contains(c.pos())) continue;
			if (w.tree && !c.log()) {
				notLog = true;
				continue;
			}
			match = w;
			break;
		}
		if (match == null) return notLog ? Result.NOT_LOG : Result.OUT_OF_RANGE;
		see(c.pos(), tick);
		if (c.popsOff() && (seenRecently(below(c.pos()), tick) || seenRecently(above(c.pos()), tick))) return Result.CASCADE;
		if (placed != null && placed.consumeIfPlaced(c.pos(), c.oldStateId())) return Result.PLACED;
		if (match.counted >= match.cap()) return Result.CAP;
		match.counted++;
		return Result.COUNT;
	}

	/** Records that a counted change matched no rule (capture only). */
	public void unmatched(String id) {
		tally.unmatched(id);
	}

	private boolean seenRecently(Pos p, long tick) {
		Long s = seen.get(p);
		return s != null && tick - s <= SEEN_TICKS;
	}

	private void see(Pos p, long tick) {
		seen.remove(p);
		seen.put(p, tick);
		Iterator<Pos> it = seen.keySet().iterator();
		while (seen.size() > MAX_SEEN && it.hasNext()) {
			it.next();
			it.remove();
		}
	}

	private static Pos below(Pos p) {
		return new Pos(p.x(), p.y() - 1, p.z());
	}

	private static Pos above(Pos p) {
		return new Pos(p.x(), p.y() + 1, p.z());
	}

	/** Each client tick: drops closed windows and old seen positions. */
	public void expire(long tick) {
		windows.removeIf(w -> w.until < tick);
		if (!seen.isEmpty()) seen.values().removeIf(t -> tick - t > SEEN_TICKS);
	}

	public void clear() {
		windows.clear();
		seen.clear();
		armedUntil = Long.MIN_VALUE;
		treeFellerUntil = Long.MIN_VALUE;
		tally.reset();
	}

	/**
	 * For capture, at most once per {@link #SUMMARY_INTERVAL_MS}: what opened windows and what the changes
	 * seen while armed did ("triggers attack=12; counted minecraft:warped_wart_block=9; skipped
	 * out_of_range=3"). Empty when nothing happened since the last summary or it is too soon.
	 */
	public Optional<String> summary(long now) {
		return tally.drain(now);
	}

	/** The ability a "... has worn off" line names: Tree Feller gives true, others false; else empty. */
	public static Optional<Boolean> wornOff(String text) {
		if (text == null || text.length() > 256) return Optional.empty();
		Matcher m = WORN_OFF.matcher(FORMATTING.matcher(text).replaceAll(""));
		if (!m.find()) return Optional.empty();
		return Optional.of(m.group(1).equalsIgnoreCase("tree feller"));
	}

	/** Counts for the capture summary. Bounded: at most 32 distinct block ids are itemised. */
	private static final class Tally {
		private static final int MAX_IDS = 32;
		private final Map<String, Integer> triggers = new TreeMap<>();
		private final Map<String, Integer> counted = new TreeMap<>();
		private final Map<String, Integer> unmatched = new TreeMap<>();
		private final Map<String, Integer> skipped = new TreeMap<>();
		private long last = Long.MIN_VALUE;

		void trigger(Trigger t) {
			triggers.merge(t.label(), 1, Integer::sum);
		}

		void result(Result r, String id) {
			if (r == Result.NOT_ARMED) return; // the hot path's normal case: not worth a line
			if (r == Result.COUNT) bump(counted, id);
			else skipped.merge(r.label(), 1, Integer::sum);
		}

		void unmatched(String id) {
			bump(unmatched, id);
		}

		private static void bump(Map<String, Integer> m, String id) {
			String key = id == null ? "?" : id;
			if (!m.containsKey(key) && m.size() >= MAX_IDS) key = "other";
			m.merge(key, 1, Integer::sum);
		}

		Optional<String> drain(long now) {
			if (last != Long.MIN_VALUE && now - last < SUMMARY_INTERVAL_MS) return Optional.empty();
			if (counted.isEmpty() && skipped.isEmpty() && unmatched.isEmpty()) {
				triggers.clear(); // triggers alone (ordinary mining) are not worth a line
				return Optional.empty();
			}
			StringBuilder b = new StringBuilder("triggers ").append(join(triggers));
			if (!counted.isEmpty()) b.append("; counted ").append(join(counted));
			if (!unmatched.isEmpty()) b.append("; no rule ").append(join(unmatched));
			if (!skipped.isEmpty()) b.append("; skipped ").append(join(skipped));
			reset();
			last = now;
			return Optional.of(b.toString());
		}

		private static String join(Map<String, Integer> m) {
			if (m.isEmpty()) return "none";
			StringBuilder b = new StringBuilder();
			for (Map.Entry<String, Integer> e : m.entrySet()) {
				if (!b.isEmpty()) b.append(' ');
				b.append(e.getKey()).append('=').append(e.getValue());
			}
			return b.toString();
		}

		void reset() {
			triggers.clear();
			counted.clear();
			unmatched.clear();
			skipped.clear();
		}
	}
}
