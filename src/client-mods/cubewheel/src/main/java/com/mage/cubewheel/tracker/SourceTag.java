package com.mage.cubewheel.tracker;

/**
 * A small coloured marker drawn before each HUD line so jobs, prestige and party quests are told apart at a
 * glance. Pure: no Minecraft/Fabric imports.
 */
public record SourceTag(String glyph, int argb) {
	private static final SourceTag JOBS = new SourceTag("⚒", 0xFFFFAA00);
	private static final SourceTag PRESTIGE = new SourceTag("✦", 0xFFFF55FF);
	private static final SourceTag PARTY_QUESTS = new SourceTag("⚑", 0xFF55FFFF);
	private static final SourceTag CHALLENGES = new SourceTag("★", 0xFFFFFF55);
	private static final SourceTag OTHER = new SourceTag("•", 0xFFAAAAAA);

	public static SourceTag of(String source) {
		if (source == null) return OTHER;
		return switch (source) {
			case "jobs" -> JOBS;
			case "prestige" -> PRESTIGE;
			case "pquests" -> PARTY_QUESTS;
			case "challenges" -> CHALLENGES;
			default -> OTHER;
		};
	}
}
