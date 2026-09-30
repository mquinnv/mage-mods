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
	/** One configured event. */
	public record Def(String name, EventSchedule schedule, ZoneId zone) {}

	/** One start of an event. */
	public record Occurrence(String name, Instant start, ZoneId zone) {
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

	/** The next start of every event, soonest first, at most {@code max}. */
	public List<Occurrence> upcoming(Instant now, int max) {
		List<Occurrence> out = new ArrayList<>(defs.size());
		for (Def d : defs) out.add(new Occurrence(d.name(), d.schedule().next(now, d.zone()), d.zone()));
		out.sort(Comparator.comparing(Occurrence::start).thenComparing(Occurrence::name));
		return out.size() > max ? List.copyOf(out.subList(0, Math.max(0, max))) : out;
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
