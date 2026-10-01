package net.mage.cubewheel.live;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.mage.cubewheel.config.WheelNode;
import net.mage.cubewheel.live.CowParser.Tier;
import net.mage.cubewheel.wheel.SliceViews;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-account /cow reward state, persisted as JSON ({@code config/cubewheel-cow.json}): when you last claimed
 * each tier, and the exact next-available time the /cow menu stated. Pure: no Minecraft/Fabric imports.
 *
 * <p>A tier is available at the menu's stated time if it was read after your last claim, else your last
 * claim plus the tier's period (ManaCube's real reset rule is unknown, so the periods are configurable),
 * else it is unknown and ignored: only a tier known to be available makes the badge "ready".
 */
public final class CowStore {
	/** Periods per tier in ms. */
	public record Periods(long daily, long weekly, long monthly) {
		public static Periods of(int dailyHours, int weeklyDays, int monthlyDays) {
			return new Periods(dailyHours * 3_600_000L, weeklyDays * 86_400_000L, monthlyDays * 86_400_000L);
		}

		long of(Tier t) {
			return switch (t) {
				case DAILY -> daily;
				case WEEKLY -> weekly;
				case MONTHLY -> monthly;
			};
		}
	}

	/** One account's state; epoch ms, keyed by tier id ("daily"). Public fields for Gson. */
	public static final class Account {
		public Map<String, Long> claimed = new LinkedHashMap<>();
		/** Next-available time stated by the /cow menu. */
		public Map<String, Long> nextAt = new LinkedHashMap<>();
		/** When that menu was read; a later claim makes it stale. */
		public Map<String, Long> readAt = new LinkedHashMap<>();
		/** Last "claimed their <key>" broadcast (tier unknown, not used for the badge). */
		public Long otherClaimed;
		public String otherReward;
	}

	/** The badge: {@code ready} when any tier is (or may be) available, else the soonest tier and its wait. */
	public record Status(boolean ready, Tier soonest, long waitMs) {
		public String label() {
			if (ready) return "Daily reward: ready";
			return soonest == null ? "Daily reward" : "Daily: " + LiveFormat.compact(waitMs);
		}
	}

	/** The "Daily reward" (/cow) slice: green "Daily reward: ready", else "Daily: 13h"; null for other nodes. */
	public static SliceViews.View slice(WheelNode node, Status status) {
		if (status == null || !SliceViews.sends(node, "/cow")) return null;
		return new SliceViews.View(status.label(), status.ready() ? SliceViews.GREEN : null, null, false);
	}

	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MAP = new TypeToken<LinkedHashMap<String, Account>>() {}.getType();

	private final Path file;
	private Map<String, Account> accounts = new LinkedHashMap<>();

	public CowStore(Path file) {
		this.file = file;
	}

	private Account account(String player) {
		return accounts.computeIfAbsent(player.trim().toLowerCase(Locale.ROOT), k -> new Account());
	}

	/** Records a claim broadcast for {@code player} (already checked to be you). Returns true if changed. */
	public boolean claimed(String player, CowParser.Claim claim, long now) {
		if (player == null || player.isBlank() || claim == null) return false;
		Account a = account(player);
		if (claim.tier() == null) {
			a.otherClaimed = now;
			a.otherReward = claim.reward();
			return true;
		}
		String t = claim.tier().id();
		a.claimed.put(t, now);
		a.nextAt.remove(t);
		a.readAt.remove(t);
		return true;
	}

	/** Records what the /cow menu said about one tier. Returns true if changed. */
	public boolean menu(String player, CowParser.MenuState state, long now) {
		if (player == null || player.isBlank() || state == null) return false;
		Account a = account(player);
		String t = state.tier().id();
		long next = now + Math.max(0, state.availableInMs());
		Long old = a.nextAt.get(t);
		// Re-reads of the same menu land within seconds of each other; don't rewrite the file for those.
		if (old != null && Math.abs(old - next) < 60_000 && a.readAt.containsKey(t)) return false;
		a.nextAt.put(t, next);
		a.readAt.put(t, now);
		return true;
	}

	/** When {@code tier} is next available for {@code player}; null = unknown. */
	public Long nextAvailable(String player, Tier tier, Periods periods) {
		if (player == null) return null;
		Account a = accounts.get(player.trim().toLowerCase(Locale.ROOT));
		if (a == null) return null;
		String t = tier.id();
		Long claimed = a.claimed.get(t);
		Long next = a.nextAt.get(t);
		Long read = a.readAt.get(t);
		if (next != null && (claimed == null || (read != null && read >= claimed))) return next;
		return claimed == null ? null : claimed + periods.of(tier);
	}

	public Status status(String player, Periods periods, long now) {
		Tier soonest = null;
		long wait = Long.MAX_VALUE;
		for (Tier t : Tier.values()) {
			Long at = nextAvailable(player, t, periods);
			if (at == null) continue; // never seen: says nothing (it used to count as ready, wrongly)
			if (at <= now) return new Status(true, t, 0);
			if (at - now < wait) {
				wait = at - now;
				soonest = t;
			}
		}
		return new Status(false, soonest, wait);
	}

	/** Loads the file; a missing or corrupt file loads empty. */
	public void load() {
		accounts = new LinkedHashMap<>();
		if (!Files.exists(file)) return;
		try {
			Map<String, Account> m = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP);
			if (m == null) return;
			for (Map.Entry<String, Account> e : m.entrySet()) {
				if (e.getKey() == null || e.getValue() == null) continue;
				Account a = e.getValue();
				if (a.claimed == null) a.claimed = new LinkedHashMap<>();
				if (a.nextAt == null) a.nextAt = new LinkedHashMap<>();
				if (a.readAt == null) a.readAt = new LinkedHashMap<>();
				accounts.put(e.getKey().toLowerCase(Locale.ROOT), a);
			}
		} catch (IOException | JsonParseException | IllegalStateException e) {
			LOG.warn("[cubewheel] could not read /cow state from {}: {}", file, e.toString());
		}
	}

	/** Writes the file; returns false (and logs) on IO errors. */
	public boolean save() {
		try {
			Path parent = file.getParent();
			if (parent != null) Files.createDirectories(parent);
			Files.writeString(file, GSON.toJson(accounts, MAP), StandardCharsets.UTF_8);
			return true;
		} catch (IOException | RuntimeException e) {
			LOG.warn("[cubewheel] could not save /cow state to {}: {}", file, e.toString());
			return false;
		}
	}
}
