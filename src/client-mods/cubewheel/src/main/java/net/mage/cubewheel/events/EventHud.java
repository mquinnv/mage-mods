package net.mage.cubewheel.events;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.ConfigStore;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.hud.Durations;
import net.mage.cubewheel.hud.HudLayout;
import net.mage.cubewheel.hud.Panel;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Minecraft adapter for the event timer: once a second (END_CLIENT_TICK) it refreshes the upcoming list and
 * shows due "starts soon" alerts as local chat lines; the "Events" panel reads that list each frame. Only
 * reads the clock and the config; never sends anything. Active only in ManaCube Survival with
 * {@code events.enabled}.
 */
public final class EventHud {
	private static final int INTERVAL_TICKS = 20;
	/** Under this many ms left an event is shown yellow. */
	private static final long SOON_MS = 5 * 60_000L;
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

	private static CubeWheelConfig builtFor;
	private static EventTimer timer = new EventTimer(List.of());
	private static List<EventTimer.Occurrence> upcoming = List.of();
	private static int ticks;
	private static boolean failureLogged;

	private EventHud() {}

	/** END_CLIENT_TICK; catches and logs its own failures. */
	public static void tick(Minecraft mc) {
		if (++ticks < INTERVAL_TICKS) return;
		ticks = 0;
		try {
			CubeWheelConfig cfg = CubeWheelClient.config().current();
			if (mc.player == null || !cfg.events.enabled || !ServerGate.survival(cfg)) {
				upcoming = List.of();
				return;
			}
			if (cfg != builtFor) rebuild(cfg); // a reload makes a new config object
			Instant now = Instant.now();
			upcoming = timer.upcoming(now, cfg.events.show);
			for (EventTimer.Occurrence o : timer.alerts(now, cfg.events.alertMinutes * 60_000L)) {
				long mins = Math.max(1, (o.start().toEpochMilli() - now.toEpochMilli() + 59_999) / 60_000);
				String at = CLOCK.format(o.start().atZone(ZoneId.systemDefault()));
				mc.player.sendSystemMessage(Component.literal("[CubeWheel] " + o.name() + " starts in " + mins + " min (" + at + ")")
						.withStyle(ChatFormatting.GOLD));
			}
		} catch (RuntimeException e) {
			if (!failureLogged) CubeWheelClient.LOG.error("[cubewheel] event timer failed", e);
			failureLogged = true;
		}
	}

	private static void rebuild(CubeWheelConfig cfg) {
		List<EventTimer.Def> defs = new ArrayList<>();
		ZoneId fallback = ConfigStore.zone(cfg.events.timezone);
		for (CubeWheelConfig.EventDef d : cfg.events.schedule) {
			if (!d.enabled) continue;
			ZoneId zone = d.timezone == null || d.timezone.isBlank() ? fallback : ConfigStore.zone(d.timezone);
			if (zone == null) continue;
			try {
				defs.add(new EventTimer.Def(d.name, EventSchedule.parse(d.when), zone));
			} catch (IllegalArgumentException e) {
				// normalize() already dropped (and reported) invalid entries; skip if one slipped through.
			}
		}
		timer = timer.rebuilt(defs); // keeps the alerted starts, so a reload does not repeat an alert
		builtFor = cfg;
	}

	/** The "Events" panel: "KOTH · 12:34", soonest first (yellow within 5 minutes). */
	public static Optional<Panel> panel(long now) {
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		if (!cfg.events.enabled || !cfg.events.hudVisible || upcoming.isEmpty() || !ServerGate.survival(cfg)) return Optional.empty();
		List<Panel.Line> lines = new ArrayList<>(upcoming.size());
		for (EventTimer.Occurrence o : upcoming) {
			long left = o.start().toEpochMilli() - now;
			lines.add(new Panel.Line(o.name() + " · " + Durations.countdown(left), left <= SOON_MS ? Panel.YELLOW : Panel.WHITE));
		}
		CubeWheelConfig.Position p = cfg.events.position;
		return Optional.of(new Panel("Events", lines, HudLayout.Corner.parse(p.corner), p.x, p.y));
	}
}
