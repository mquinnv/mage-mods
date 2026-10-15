package net.mage.cubewheel.skills;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns one account's {@link SkillStore.Account} into the plain rows the mcMMO skills panel draws. Pure: the HUD
 * adapter maps {@link Row}s onto its own line type, so nothing here knows about Minecraft or the {@code hud} package.
 * Reads only {@code account.skills()} and {@code account.power()}; a skill the store has no state for is never shown.
 */
public final class SkillsPanelModel {
	private SkillsPanelModel() {}

	/** Which skills the panel lists. */
	public enum Mode {
		/** Only skills that gained XP within {@link Options#recentMs()}, newest first. */
		RECENT,
		/** Every skill the store knows, highest level first. */
		ALL
	}

	/** How a row is coloured: bright when its XP was read recently, dim otherwise. */
	public enum Tone { FRESH, STALE }

	/**
	 * Panel settings.
	 *
	 * @param mode which skills to list
	 * @param recentMs how long after a bar reading a skill counts as recent (RECENT membership and FRESH tone)
	 * @param max most rows RECENT mode shows; ignored by ALL
	 * @param showChild whether ALL mode includes child skills (Smelting)
	 */
	public record Options(Mode mode, long recentMs, int max, boolean showChild) {}

	/**
	 * One skill row.
	 *
	 * @param skill the skill this row describes
	 * @param label left text, "Mining 617"
	 * @param right right-aligned text: "65,682 / 155,750", "MAX" when capped, "?" when XP is unknown
	 * @param fraction bar fill 0..1: XP over XP needed, 1.0 when capped, 0 when unknown
	 * @param tone FRESH when the XP was read within the recent window, else STALE
	 * @param accent true when the skill gained XP in the last two minutes (the caller's recent-gains map)
	 * @param capped true when the level has reached the skill's cap
	 * @param unknown true when only the level is known (menu or level-up), not the XP
	 */
	public record Row(Skill skill, String label, String right, double fraction, Tone tone, boolean accent,
			boolean capped, boolean unknown) {}

	/**
	 * The panel's heading line.
	 *
	 * @param power the account's total power level, 0 when never seen
	 * @param powerText {@code power} with thousands separators, or "" when the power level is unknown
	 */
	public record Header(long power, String powerText) {}

	/**
	 * What the panel shows.
	 *
	 * @param header the heading line
	 * @param rows the skill rows in display order; empty means the adapter hides the panel
	 */
	public record View(Header header, List<Row> rows) {}

	/**
	 * Builds the view for {@code account} at time {@code now}.
	 *
	 * @param recentGains the store's {@code recentGains(name, now, 120_000)}, computed by the caller; a skill with
	 *        an entry gets the accent
	 */
	public static View build(SkillStore.Account account, Map<Skill, Long> recentGains, Options o, long now) {
		List<Map.Entry<Skill, SkillStore.State>> picked = new ArrayList<>();
		for (Map.Entry<Skill, SkillStore.State> e : account.skills().entrySet()) {
			SkillStore.State st = e.getValue();
			if (o.mode() == Mode.RECENT) {
				if (st.xpAt() > 0 && now - st.xpAt() <= o.recentMs()) picked.add(e);
			} else if (o.showChild() || !e.getKey().child) {
				picked.add(e);
			}
		}
		if (o.mode() == Mode.RECENT) {
			picked.sort(Comparator.comparingLong((Map.Entry<Skill, SkillStore.State> e) -> e.getValue().xpAt()).reversed());
		} else {
			picked.sort(Comparator.comparingInt((Map.Entry<Skill, SkillStore.State> e) -> e.getValue().level()).reversed()
					.thenComparing(e -> e.getKey().label));
		}
		List<Row> rows = new ArrayList<>();
		for (Map.Entry<Skill, SkillStore.State> e : picked) {
			if (o.mode() == Mode.RECENT && rows.size() >= o.max()) break;
			rows.add(row(e.getKey(), e.getValue(), recentGains.containsKey(e.getKey()), o, now));
		}
		long power = account.power();
		return new View(new Header(power, power > 0 ? number(power) : ""), List.copyOf(rows));
	}

	private static Row row(Skill skill, SkillStore.State st, boolean accent, Options o, long now) {
		boolean capped = st.cap() > 0 && st.level() >= st.cap();
		boolean unknown = st.xp() < 0;
		String right;
		double fraction;
		if (capped) {
			right = "MAX";
			fraction = 1.0;
		} else if (unknown) {
			right = "?";
			fraction = 0;
		} else {
			right = number(st.xp()) + " / " + number(st.needed());
			fraction = st.needed() > 0 ? Math.max(0, Math.min(1, (double) st.xp() / st.needed())) : 0;
		}
		Tone tone = st.xpAt() > 0 && now - st.xpAt() <= o.recentMs() ? Tone.FRESH : Tone.STALE;
		return new Row(skill, skill.label + " " + st.level(), right, fraction, tone, accent, capped, unknown && !capped);
	}

	private static String number(long n) {
		return String.format(Locale.ROOT, "%,d", n);
	}
}
