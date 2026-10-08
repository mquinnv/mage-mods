package net.mage.cubewheel.events;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Upcoming scheduled events and "starts soon" alerts. Pure: no Minecraft/Fabric imports.
 */
public final class EventTimer {
	/** One configured event; a {@code pinned} one is always listed (see {@link #upcoming}). */
	public record Def(String name, EventSchedule schedule, ZoneId zone, boolean pinned) {
		public Def(String name, EventSchedule schedule, ZoneId zone) {
			this(name, schedule, zone, false);
		}
	}

	/** One start of an event. */
	public record Occurrence(String name, Instant start, ZoneId zone, boolean pinned) {
		String key() {
			return name + "@" + start.toEpochMilli();
		}
	}

	private final List<Def> defs;
	/** Starts already alerted; pruned once they are in the past. */
	private final Set<String> alerted = new HashSet<>();

	public EventTimer(List<Def> defs) {
		this.defs = List.copyOf(defs);
	}

	/** A timer for new definitions that remembers which starts were already alerted (config reload). */
	public EventTimer rebuilt(List<Def> newDefs) {
		EventTimer t = new EventTimer(newDefs);
		t.alerted.addAll(alerted);
		return t;
	}

	/**
	 * The next start of every event, soonest first: the {@code max} soonest, then every pinned event's next start that
	 * is not among them (the Mana Pond, Michael 2026-10-07), those still in time order.
	 */
	public List<Occurrence> upcoming(Instant now, int max) {
		List<Occurrence> all = new ArrayList<>(defs.size());
		for (Def d : defs) all.add(new Occurrence(d.name(), d.schedule().next(now, d.zone()), d.zone(), d.pinned()));
		all.sort(Comparator.comparing(Occurrence::start).thenComparing(Occurrence::name));
		int soonest = Math.max(0, Math.min(max, all.size()));
		if (soonest == all.size()) return all;
		List<Occurrence> out = new ArrayList<>(all.subList(0, soonest));
		for (Occurrence o : all.subList(soonest, all.size())) {
			if (o.pinned()) out.add(o);
		}
		return List.copyOf(out);
	}

	/**
	 * Starts at most {@code leadMs} away that were not alerted yet (each start alerts once, also when the
	 * client joins inside the window). {@code leadMs <= 0} never alerts.
	 */
	public List<Occurrence> alerts(Instant now, long leadMs) {
		List<Occurrence> out = new ArrayList<>();
		if (leadMs <= 0) return out;
		alerted.removeIf(k -> Long.parseLong(k.substring(k.lastIndexOf('@') + 1)) <= now.toEpochMilli());
		for (Occurrence o : upcoming(now, Integer.MAX_VALUE)) {
			long left = o.start().toEpochMilli() - now.toEpochMilli();
			if (left <= leadMs && alerted.add(o.key())) out.add(o);
		}
		return out;
	}
}
