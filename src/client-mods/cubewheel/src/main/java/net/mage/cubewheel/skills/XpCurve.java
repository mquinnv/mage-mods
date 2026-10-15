package net.mage.cubewheel.skills;

/**
 * The XP an mcMMO level needs on ManaCube Survival: {@code 1500 + 250 * level}. Fitted to 14,461 captured skill
 * boss bars (levels 56..617, zero mismatches), e.g. level 243 -> 62,250 and level 617 -> 155,750. Pure.
 */
public final class XpCurve {
	private XpCurve() {}

	/** XP to go from {@code level} to {@code level + 1}. */
	public static long needed(int level) {
		return 1500 + 250L * level;
	}
}
