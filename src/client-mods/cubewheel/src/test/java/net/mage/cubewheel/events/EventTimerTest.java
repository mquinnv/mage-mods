package net.mage.cubewheel.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventTimerTest {
	private static final ZoneId NY = ZoneId.of("America/New_York");

	private static EventTimer timer() {
		return new EventTimer(List.of(
				new EventTimer.Def("LPS", EventSchedule.parse("at 08:00, 13:00, 17:00"), NY),
				new EventTimer.Def("KOTH", EventSchedule.parse("at 00:30, 02:30, 04:30, 06:30, 10:30, 12:30, 14:30, 16:30, 18:30, 22:30"), NY),
				new EventTimer.Def("Golden Knight", EventSchedule.parse("every 3h from 00:15"), NY)));
	}

	@Test void upcomingIsEachEventsNextStartSoonestFirst() {
		Instant now = Instant.parse("2026-09-30T16:00:00Z"); // 12:00 EDT
		List<EventTimer.Occurrence> up = timer().upcoming(now, 3);
		assertEquals(3, up.size());
		assertEquals("Golden Knight", up.get(0).name()); // 12:15
		assertEquals(Instant.parse("2026-09-30T16:15:00Z"), up.get(0).start());
		assertEquals("KOTH", up.get(1).name()); // 12:30
		assertEquals("LPS", up.get(2).name()); // 13:00
	}

	@Test void upcomingIsCapped() {
		Instant now = Instant.parse("2026-09-30T16:00:00Z");
		assertEquals(1, timer().upcoming(now, 1).size());
		assertEquals(3, timer().upcoming(now, 10).size()); // one line per event
		assertTrue(new EventTimer(List.of()).upcoming(now, 3).isEmpty());
	}

	@Test void alertFiresOnceInsideTheLeadWindow() {
		EventTimer t = timer();
		long lead = 5 * 60_000L;
		assertTrue(t.alerts(Instant.parse("2026-09-30T16:09:00Z"), lead).isEmpty()); // GK 12:15 is 6 min away
		List<EventTimer.Occurrence> a = t.alerts(Instant.parse("2026-09-30T16:10:00Z"), lead);
		assertEquals(1, a.size());
		assertEquals("Golden Knight", a.get(0).name());
		assertTrue(t.alerts(Instant.parse("2026-09-30T16:11:00Z"), lead).isEmpty(), "only once per start");
		assertTrue(t.alerts(Instant.parse("2026-09-30T16:14:59Z"), lead).isEmpty());
		assertEquals("KOTH", t.alerts(Instant.parse("2026-09-30T16:25:30Z"), lead).get(0).name());
	}

	@Test void joiningInsideTheWindowStillAlerts() {
		EventTimer t = timer();
		List<EventTimer.Occurrence> a = t.alerts(Instant.parse("2026-09-30T16:14:00Z"), 5 * 60_000L);
		assertEquals(List.of("Golden Knight"), a.stream().map(EventTimer.Occurrence::name).toList());
	}

	@Test void zeroLeadNeverAlerts() {
		assertTrue(timer().alerts(Instant.parse("2026-09-30T16:14:00Z"), 0).isEmpty());
	}

	@Test void nextDaysStartsAlertAgain() {
		EventTimer t = new EventTimer(List.of(new EventTimer.Def("LPS", EventSchedule.parse("at 08:00"), NY)));
		assertEquals(1, t.alerts(Instant.parse("2026-09-30T11:58:00Z"), 300_000).size());
		assertEquals(1, t.alerts(Instant.parse("2026-10-01T11:58:00Z"), 300_000).size());
	}

	@Test void rebuildingKeepsAlertedStarts() {
		EventTimer t = timer();
		assertEquals(1, t.alerts(Instant.parse("2026-09-30T16:10:00Z"), 300_000).size());
		EventTimer r = t.rebuilt(List.of(new EventTimer.Def("Golden Knight", EventSchedule.parse("every 3h from 00:15"), NY)));
		assertTrue(r.alerts(Instant.parse("2026-09-30T16:11:00Z"), 300_000).isEmpty());
	}
}
