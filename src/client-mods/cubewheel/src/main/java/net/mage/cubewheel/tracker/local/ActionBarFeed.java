package net.mage.cubewheel.tracker.local;

import java.util.Objects;

/**
 * Turns the action-bar observations of up to three sources into one stream of messages, each message
 * once. Event sources (the SetActionBarText packet hook and the Hud.setOverlayMessage hook) see every
 * message being set; a message both see is fed once. The per-tick poll of the Hud's overlay text and its
 * remaining-display ticks is the fallback that works even when neither hook fires: a message is new when
 * the text changed or the remaining ticks went up (the same text set again, "+1  Cute Firefly" twice),
 * and is skipped when an event source already fed it since the previous poll. The poll cannot tell apart
 * identical messages set on consecutive ticks (the remaining ticks look unchanged). Pure: no
 * Minecraft/Fabric imports.
 */
public final class ActionBarFeed {
	/** An event from another source with the same text at most this long after the previous one is the same message. */
	public static final long SAME_MESSAGE_MS = 250;

	/** Where a message was seen; {@code label} names it for the game log. */
	public enum Source {
		PACKET("packet hook (ClientPacketListener.setActionBarText)"),
		HUD("Hud.setOverlayMessage hook"),
		POLL("per-tick poll of the Hud overlay text");

		public final String label;

		Source(String label) {
			this.label = label;
		}
	}

	private String lastText;
	private Source lastSource;
	private long lastAt;
	/** The text an event source reported since the previous poll (fed or deduped), else null. */
	private String eventSincePoll;
	private String polledText;
	private int polledTime;

	/**
	 * An event source ({@link Source#PACKET} or {@link Source#HUD}) saw {@code text} being set at
	 * {@code now} ms. True when it is a new message to feed; false for null or when another event source
	 * already fed the same text within {@link #SAME_MESSAGE_MS}. The same source reporting the same text
	 * twice is two messages.
	 */
	public boolean event(Source source, String text, long now) {
		if (source == Source.POLL) throw new IllegalArgumentException("use poll()");
		if (text == null) return false;
		eventSincePoll = text;
		boolean dup = text.equals(lastText) && lastSource != null && lastSource != Source.POLL
				&& lastSource != source && now - lastAt >= 0 && now - lastAt <= SAME_MESSAGE_MS;
		if (dup) return false;
		accept(source, text, now);
		return true;
	}

	/**
	 * Once per client tick: the Hud's overlay text and its remaining display ticks. True when this shows a
	 * message no event source reported since the previous poll: the text changed, or the remaining ticks
	 * went up (set again), while it is still shown ({@code remainingTicks > 0}).
	 */
	public boolean poll(String text, int remainingTicks, long now) {
		boolean changed = !Objects.equals(text, polledText) || remainingTicks > polledTime;
		String covered = eventSincePoll;
		polledText = text;
		polledTime = remainingTicks;
		eventSincePoll = null;
		if (!changed || text == null || remainingTicks <= 0) return false;
		if (text.equals(covered)) return false; // a hook already fed it
		accept(Source.POLL, text, now);
		return true;
	}

	private void accept(Source source, String text, long now) {
		lastText = text;
		lastSource = source;
		lastAt = now;
	}
}
