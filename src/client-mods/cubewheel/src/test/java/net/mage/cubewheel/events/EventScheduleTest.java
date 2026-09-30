package net.mage.cubewheel.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventScheduleTest {
	private static final ZoneId NY = ZoneId.of("America/New_York");

	private static List<LocalTime> times(String spec) {
		return EventSchedule.parse(spec).times();
	}

	@Test void atList() {
		assertEquals(List.of(LocalTime.of(8, 0), LocalTime.of(13, 0), LocalTime.of(17, 0)), times("at 08:00, 13:00, 17:00"));
		assertEquals(List.of(LocalTime.of(8, 0), LocalTime.of(13, 0)), times("AT 13:00 8:00")); // sorted
		assertEquals(List.of(LocalTime.of(0, 30), LocalTime.of(8, 0), LocalTime.of(13, 0)), times("at 8:00AM, 1:00 PM, 12:30am"));
		assertEquals(List.of(LocalTime.of(12, 15)), times("at 12:15 PM, 12:15"));  // duplicates collapse
	}

	@Test void every() {
		assertEquals(List.of(LocalTime.of(0, 15), LocalTime.of(3, 15), LocalTime.of(6, 15), LocalTime.of(9, 15),
				LocalTime.of(12, 15), LocalTime.of(15, 15), LocalTime.of(18, 15), LocalTime.of(21, 15)), times("every 3h from 00:15"));
		assertEquals(8, times("every 3 hours from 2:15").size());
		assertEquals(LocalTime.of(2, 15), times("every 3 hours from 2:15").get(0));
		assertEquals(12, times("every 2h at :30").size());
		assertEquals(LocalTime.of(0, 30), times("every 2h at :30").get(0));
		assertEquals(24, times("every 1h").size());
		// "from" is the first run of each day; runs continue until midnight and the pattern restarts daily.
		assertEquals(List.of(LocalTime.of(2, 0), LocalTime.of(7, 0), LocalTime.of(12, 0), LocalTime.of(17, 0), LocalTime.of(22, 0)),
				times("every 5h from 02:00"));
	}

	@Test void rejectsJunk() {
		for (String bad : new String[] {null, "", "sometimes", "at", "at 25:00", "at 12:60", "at 13:00pm", "every 0h", "every 25h",
				"every 3h from noon", "every 3h at :75", "at 08:00 and later"}) {
			assertThrows(IllegalArgumentException.class, () -> EventSchedule.parse(bad), String.valueOf(bad));
		}
	}

	@Test void nextIsStrictlyAfterNowAndCrossesMidnight() {
		EventSchedule s = EventSchedule.parse("at 08:00, 22:00");
		Instant now = Instant.parse("2026-09-30T02:30:00Z"); // 22:30 EDT on the 29th
		assertEquals(Instant.parse("2026-09-30T12:00:00Z"), s.next(now, NY)); // 08:00 EDT
		Instant at = Instant.parse("2026-09-30T12:00:00Z");
		assertEquals(Instant.parse("2026-10-01T02:00:00Z"), s.next(at, NY)); // exactly at 08:00 -> 22:00
	}

	@Test void pageTimesAreNewYorkLocalTimeNotFixedEst() {
		// Capture 2026-09-30: "[PIT KOTH] The event has begun!" at 12:30:59 EDT; "LPS! Event starting in 30m" at 12:28 EDT.
		EventSchedule koth = EventSchedule.parse("at 00:30, 02:30, 04:30, 06:30, 10:30, 12:30, 14:30, 16:30, 18:30, 22:30");
		assertEquals(Instant.parse("2026-09-30T16:30:00Z"), koth.next(Instant.parse("2026-09-30T16:00:00Z"), NY));
		EventSchedule lps = EventSchedule.parse("at 08:00, 13:00, 17:00");
		assertEquals(Instant.parse("2026-09-30T17:00:00Z"), lps.next(Instant.parse("2026-09-30T16:28:06Z"), NY));
	}

	@Test void dstChangesNeitherRepeatNorSkipBackwards() {
		EventSchedule s = EventSchedule.parse("every 3h from 02:15");
		// 2027-03-14 02:15 does not exist in New York (spring forward): moved to 03:15 EDT.
		Instant before = Instant.parse("2027-03-14T06:00:00Z"); // 01:00 EST
		Instant n = s.next(before, NY);
		assertEquals(Instant.parse("2027-03-14T07:15:00Z"), n);
		Instant after = s.next(n, NY);
		assertTrue(after.isAfter(n));
		assertEquals(Instant.parse("2027-03-14T09:15:00Z"), after); // 05:15 EDT
	}
}
