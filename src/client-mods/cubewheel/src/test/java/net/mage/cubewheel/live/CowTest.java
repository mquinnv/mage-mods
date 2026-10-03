package net.mage.cubewheel.live;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import net.mage.cubewheel.config.WheelNode;
import net.mage.cubewheel.live.CowParser.Tier;
import net.mage.cubewheel.wheel.SliceViews;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CowTest {
	private static final long H = 3_600_000L;
	private static final long D = 24 * H;
	private static final CowStore.Periods P = CowStore.Periods.of(24, 7, 30);

	@TempDir Path dir;

	@Test void parsesRealClaimBroadcasts() {
		CowParser.Claim w = CowParser.claim("[/CASHCOW] Relaxz claimed weekly ⺹ Elite Key").orElseThrow();
		assertEquals("Relaxz", w.player());
		assertEquals(Tier.WEEKLY, w.tier());
		assertEquals("⺹ Elite Key", w.reward());
		assertEquals(Tier.MONTHLY, CowParser.claim("[/CASHCOW] Relaxz claimed monthly ⺾ Ancient Key").orElseThrow().tier());
		assertEquals(Tier.DAILY, CowParser.claim("§6[/CASHCOW] §fQualan claimed daily ⺸ Key").orElseThrow().tier());
		CowParser.Claim their = CowParser.claim("[/CASHCOW] Relaxz claimed their ⻁ Promo Key").orElseThrow();
		assertNull(their.tier());
		assertEquals("⻁ Promo Key", their.reward());
		assertTrue(CowParser.claim("Relaxz: claimed weekly Elite Key").isEmpty());
		assertTrue(CowParser.claim("[/CASHCOW] something else happened").isEmpty());
	}

	@Test void parsesYourOwnKeyMessage() {
		// Claiming the daily reward broadcasts nothing; only this private line was seen (2026-10-03 10:04:17).
		CowParser.Claim d = CowParser.received("You received 2 Daily Crate Key(s). TYPE /CRATES TO USE").orElseThrow();
		assertNull(d.player());
		assertEquals(Tier.DAILY, d.tier());
		assertEquals("2 Daily Crate Key(s)", d.reward());
		assertEquals(Tier.WEEKLY, CowParser.received("§aYou received 1 Weekly Crate Key(s).").orElseThrow().tier());
		assertTrue(CowParser.received("You received a Terrastriker from a Golden Crate!").isEmpty());
		assertTrue(CowParser.received("§r KingBee: You received 2 Daily Crate Key(s)").isEmpty());
	}

	@Test void aClaimAfterTheMenuSaidReadyIsNoLongerReady() {
		CowStore s = new CowStore(dir.resolve("cow.json"));
		long t = 1_000 * D;
		s.menu("Qualan", new CowParser.MenuState(Tier.DAILY, 0), t);
		assertTrue(s.status("Qualan", P, t).ready());
		s.claimed("Qualan", CowParser.received("You received 2 Daily Crate Key(s).").orElseThrow(), t + 2_000);
		assertFalse(s.status("Qualan", P, t + 3_000).ready());
		assertEquals(t + 2_000 + D, s.nextAvailable("Qualan", Tier.DAILY, P));
	}

	@Test void parsesMenuCooldownLore() {
		assertEquals(new CowParser.MenuState(Tier.DAILY, 13 * H + 2 * 60_000),
				CowParser.menuItem("§aDaily Reward", List.of("Rewards: 1x Key", "§7Available in 13h 2m")).orElseThrow());
		assertEquals(new CowParser.MenuState(Tier.WEEKLY, 2 * D + 3 * H),
				CowParser.menuItem("Weekly Reward", List.of("Claimed! Come back in 2d 3h")).orElseThrow());
		assertEquals(new CowParser.MenuState(Tier.MONTHLY, 0),
				CowParser.menuItem("Monthly", List.of("Click to claim!")).orElseThrow());
		assertEquals(Tier.DAILY, CowParser.menuItem("Reward", List.of("Daily", "Claim in: 5m 10s")).orElseThrow().tier());
		assertTrue(CowParser.menuItem("Daily Reward", List.of("A nice key")).isEmpty()); // no time stated
		assertTrue(CowParser.menuItem("Close", List.of("Available in 1h")).isEmpty()); // no tier
		assertEquals(3_723_000L, CowParser.duration("1 hour 2 minutes 3 seconds").getAsLong());
	}

	@Test void availabilityFromClaimsAndMenu() {
		CowStore s = new CowStore(dir.resolve("cow.json"));
		long t = 1_000_000_000L;
		assertFalse(s.status("Qualan", P, t).ready()); // nothing known: not "ready" (2026-09-30: it claimed ready with 1h+ to go)
		assertEquals("Daily reward", s.status("Qualan", P, t).label());
		s.claimed("Qualan", new CowParser.Claim("Qualan", Tier.DAILY, "Key"), t);
		s.claimed("Qualan", new CowParser.Claim("Qualan", Tier.WEEKLY, "Key"), t);
		assertFalse(s.status("qualan", P, t + H).ready()); // monthly still unknown: ignored, daily/weekly are waiting
		s.claimed("QUALAN", new CowParser.Claim("Qualan", Tier.MONTHLY, "Key"), t);
		CowStore.Status st = s.status("Qualan", P, t + H);
		assertFalse(st.ready());
		assertEquals(Tier.DAILY, st.soonest());
		assertEquals("Daily: 23h", st.label());
		assertTrue(s.status("Qualan", P, t + 24 * H).ready()); // daily is back
		// The menu's exact time wins over the estimate...
		s.menu("Qualan", new CowParser.MenuState(Tier.DAILY, 2 * H), t + H);
		assertEquals(t + 3 * H, s.nextAvailable("Qualan", Tier.DAILY, P));
		assertEquals("Daily: 1h", s.status("Qualan", P, t + 90 * 60_000).label());
		// ...until the next claim.
		s.claimed("Qualan", new CowParser.Claim("Qualan", Tier.DAILY, "Key"), t + 4 * H);
		assertEquals(t + 28 * H, s.nextAvailable("Qualan", Tier.DAILY, P));
		// "their" claims are kept but do not change the badge.
		assertTrue(s.claimed("Qualan", new CowParser.Claim("Qualan", null, "Promo Key"), t + 5 * H));
		assertEquals(t + 28 * H, s.nextAvailable("Qualan", Tier.DAILY, P));
		// Other accounts are separate.
		assertFalse(s.status("Alt", P, t + H).ready()); // nothing known for this account
	}

	@Test void persistsPerAccount() {
		Path f = dir.resolve("cubewheel-cow.json");
		CowStore s = new CowStore(f);
		s.claimed("Qualan", new CowParser.Claim("Qualan", Tier.WEEKLY, "Key"), 5_000L);
		s.menu("Qualan", new CowParser.MenuState(Tier.DAILY, 60_000), 5_000L);
		assertTrue(s.save());
		CowStore t = new CowStore(f);
		t.load();
		assertEquals(5_000L + 7 * D, t.nextAvailable("qualan", Tier.WEEKLY, P));
		assertEquals(65_000L, t.nextAvailable("Qualan", Tier.DAILY, P));
	}

	@Test void menuSaysDailyWaitsAnHourWhileOtherTiersAreUnknown() {
		// The real case: only the /cow menu's daily time is known (about 1h15m), weekly/monthly never seen.
		CowStore s = new CowStore(dir.resolve("cow2.json"));
		long t = 1_790_822_688_004L;
		s.menu("Qualan", new CowParser.MenuState(Tier.DAILY, 4_512_000L), t);
		CowStore.Status st = s.status("Qualan", P, t + 60_000);
		assertFalse(st.ready());
		assertEquals("Daily: 1h", st.label());
		assertTrue(s.status("Qualan", P, t + 4_512_000L).ready());
	}

	@Test void sliceLabelAndColour() {
		WheelNode cow = WheelNode.leaf("Daily reward", null, "/cow");
		SliceViews.View ready = CowStore.slice(cow, new CowStore.Status(true, Tier.DAILY, 0));
		assertEquals("Daily reward: ready", ready.label());
		assertEquals(SliceViews.GREEN, ready.colour());
		assertEquals("/cow", SliceViews.command(cow, ready)); // still just opens /cow
		SliceViews.View wait = CowStore.slice(cow, new CowStore.Status(false, Tier.DAILY, 13 * H + 5 * 60_000));
		assertEquals("Daily: 13h", wait.label());
		assertNull(wait.colour());
		assertEquals("/cow", SliceViews.command(cow, wait));
		assertNull(CowStore.slice(WheelNode.leaf("Fly", null, "/fly"), new CowStore.Status(true, Tier.DAILY, 0)));
	}

	@Test void compactDurations() {
		assertEquals("<1m", LiveFormat.compact(59_000));
		assertEquals("2m", LiveFormat.compact(150_000));
		assertEquals("13h", LiveFormat.compact(13 * H + 59 * 60_000));
		assertEquals("47h", LiveFormat.compact(47 * H));
		assertEquals("6d", LiveFormat.compact(6 * D + 5 * H));
		assertEquals("<1m", LiveFormat.compact(-5));
	}
}
