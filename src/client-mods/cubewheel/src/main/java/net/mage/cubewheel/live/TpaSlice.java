package net.mage.cubewheel.live;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.mage.cubewheel.wheel.SliceViews;

/**
 * The "TPA" slice: while someone's teleport request is pending it reads "Accept VetaPhoenix79" (green) and sends
 * /tpaccept; otherwise it is an inert "TPA" whose arc holds /tpa entries for friends. Requests are read from
 * ManaCube's (EssentialsX) chat lines and end when accepted, denied, timed out or past their timeout. Nothing is
 * ever sent automatically. Pure: no Minecraft/Fabric imports.
 */
public final class TpaSlice {
	public static final String IDLE = "TPA";
	/** EssentialsX's default request lifetime, used until a "will timeout after N seconds" line says otherwise. */
	public static final long DEFAULT_TIMEOUT_MS = 120_000;

	private static final Pattern FORMATTING = Pattern.compile("§.");
	/** "VetaPhoenix79 has requested to teleport to you." / "… has requested that you teleport to them." */
	private static final Pattern REQUEST = Pattern.compile(
			"^\\s*(?:\\[[^]]*]\\s*)*(\\S+) has requested (?:to teleport to you|that you teleport to them)\\.?\\s*$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern TIMEOUT = Pattern.compile("(?i)this request will time ?out after\\s*(\\d+)\\s*seconds?");
	/** The pending request is over: accepted, denied or timed out (EssentialsX wording). */
	private static final Pattern DONE = Pattern.compile(
			"(?i)^\\s*(?:teleport request (?:accepted|denied)|teleport request has timed out|you do not have a pending request)");

	private String from;
	private long at;
	private long timeoutMs = DEFAULT_TIMEOUT_MS;

	/** Feeds one chat line; returns true if it changed the pending request. */
	public boolean onChat(String raw, long now) {
		if (raw == null) return false;
		String text = FORMATTING.matcher(raw).replaceAll("").trim();
		Matcher r = REQUEST.matcher(text);
		if (r.matches()) {
			from = r.group(1);
			at = now;
			timeoutMs = DEFAULT_TIMEOUT_MS;
			return true;
		}
		Matcher t = TIMEOUT.matcher(text);
		if (t.find() && from != null) {
			timeoutMs = Long.parseLong(t.group(1)) * 1000;
			return true;
		}
		if (DONE.matcher(text).find() && from != null) {
			from = null;
			return true;
		}
		return false;
	}

	/** Who asked, while the request is still pending; null otherwise. */
	public String pending(long now) {
		if (from == null || now - at > timeoutMs || now < at - 60_000) return null;
		return from;
	}

	/** The slice right now. */
	public SliceViews.View view(long now) {
		String who = pending(now);
		if (who == null) return new SliceViews.View(IDLE, null, null, true);
		return new SliceViews.View("Accept " + who, SliceViews.GREEN, "/tpaccept", false);
	}
}
