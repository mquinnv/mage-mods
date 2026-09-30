package net.mage.cubewheel.events;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A daily schedule of start times, written in the config as
 * <ul>
 *   <li>{@code "at 08:00, 13:00, 17:00"} (24-hour, or with AM/PM: {@code "at 8:00AM, 1:00 PM"}), or</li>
 *   <li>{@code "every 3h from 00:15"} / {@code "every 2 hours at :30"} (from that time until midnight, every day).</li>
 * </ul>
 * Times are wall-clock times in the event's time zone. Pure: no Minecraft/Fabric imports.
 */
public final class EventSchedule {
	private static final Pattern AT = Pattern.compile("^at\\s+(.+)$");
	private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*(am|pm)?");
	private static final Pattern LIST_SEP = Pattern.compile("[\\s,]*");
	private static final Pattern EVERY = Pattern.compile(
			"^every\\s+(\\d{1,2})\\s*(?:h|hr|hrs|hour|hours)(?:\\s+from\\s+(\\S+(?:\\s*[ap]m)?)|\\s+at\\s+:(\\d{2}))?$");

	private final List<LocalTime> times;

	private EventSchedule(List<LocalTime> times) {
		this.times = List.copyOf(times);
	}

	/** Parses a spec; throws IllegalArgumentException with a readable reason. */
	public static EventSchedule parse(String spec) {
		if (spec == null || spec.isBlank()) throw new IllegalArgumentException("empty schedule");
		String s = spec.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
		Matcher at = AT.matcher(s);
		if (at.matches()) return new EventSchedule(parseList(at.group(1)));
		Matcher ev = EVERY.matcher(s);
		if (ev.matches()) {
			int hours = Integer.parseInt(ev.group(1));
			if (hours < 1 || hours > 24) throw new IllegalArgumentException("every: hours must be 1-24");
			LocalTime from = LocalTime.MIDNIGHT;
			if (ev.group(2) != null) from = parseTime(ev.group(2));
			if (ev.group(3) != null) {
				int min = Integer.parseInt(ev.group(3));
				if (min > 59) throw new IllegalArgumentException("every: minute must be 00-59");
				from = LocalTime.of(0, min);
			}
			List<LocalTime> out = new ArrayList<>();
			for (int m = from.getHour() * 60 + from.getMinute(); m < 24 * 60; m += hours * 60) {
				out.add(LocalTime.of(m / 60, m % 60));
			}
			return new EventSchedule(out);
		}
		throw new IllegalArgumentException("expected \"at HH:MM, ...\" or \"every Nh from HH:MM\": " + spec.trim());
	}

	private static List<LocalTime> parseList(String list) {
		TreeSet<LocalTime> out = new TreeSet<>();
		Matcher t = TIME.matcher(list);
		Matcher sep = LIST_SEP.matcher(list);
		int pos = 0;
		while (pos < list.length()) {
			if (!t.region(pos, list.length()).lookingAt()) throw new IllegalArgumentException("not a time: " + list.substring(pos));
			out.add(time(t.group(1), t.group(2), t.group(3)));
			pos = t.end();
			if (sep.region(pos, list.length()).lookingAt()) pos = sep.end();
		}
		if (out.isEmpty()) throw new IllegalArgumentException("no times");
		return new ArrayList<>(out);
	}

	private static LocalTime parseTime(String s) {
		Matcher t = TIME.matcher(s.trim());
		if (!t.matches()) throw new IllegalArgumentException("not a time: " + s);
		return time(t.group(1), t.group(2), t.group(3));
	}

	private static LocalTime time(String h, String m, String ampm) {
		int hour = Integer.parseInt(h), min = Integer.parseInt(m);
		if (min > 59) throw new IllegalArgumentException("minute out of range: " + h + ":" + m);
		if (ampm != null) {
			if (hour < 1 || hour > 12) throw new IllegalArgumentException("hour out of range for AM/PM: " + h + ":" + m);
			hour = hour % 12 + (ampm.equals("pm") ? 12 : 0);
		} else if (hour > 23) {
			throw new IllegalArgumentException("hour out of range: " + h + ":" + m);
		}
		return LocalTime.of(hour, min);
	}

	/** The day's start times, ascending. */
	public List<LocalTime> times() {
		return times;
	}

	/**
	 * The first start strictly after {@code now}. A time that does not exist on a DST day (spring forward)
	 * moves forward by the gap; a repeated hour (fall back) uses its first occurrence.
	 */
	public Instant next(Instant now, ZoneId zone) {
		LocalDate day = now.atZone(zone).toLocalDate().minusDays(1);
		for (int d = 0; d < 4; d++, day = day.plusDays(1)) {
			for (LocalTime t : times) {
				Instant at = ZonedDateTime.of(day, t, zone).toInstant();
				if (at.isAfter(now)) return at;
			}
		}
		throw new IllegalStateException("no start within 3 days"); // unreachable: times is never empty
	}
}
