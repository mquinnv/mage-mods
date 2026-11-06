package net.mage.cubewheel.sidebar;

import java.util.Objects;

/**
 * Which ManaCube gamemode the sidebar title says the player is in. Pure: no Minecraft/Fabric imports.
 */
public enum Gamemode {
	SURVIVAL("survival"), SKYBLOCK("skyblock"), OTHER("other");

	private final String id;

	Gamemode(String id) {
		this.id = id;
	}

	/** Name for messages and titles ("Survival"). */
	public String displayName() {
		return id.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + id.substring(1);
	}

	/** Stable lower-case name, safe to use as a key. */
	public String id() {
		return id;
	}

	/**
	 * Survival is checked first, so a title matching both is Survival. A blank survival pattern keeps its
	 * old meaning (gate off, so any title is Survival), except that the Skyblock pattern is then tested first:
	 * otherwise the blank pattern would claim Skyblock too (Michael 2026-11-06, the README tells players to
	 * blank it). A blank Skyblock pattern never detects Skyblock, unlike {@link SidebarGate#matches}, so an
	 * empty pattern cannot claim every server.
	 */
	public static Gamemode detect(String sidebarTitle, String survivalPattern, String skyblockPattern) {
		// Asked many times a frame. SidebarGate keeps one entry per call, and the two patterns would evict each
		// other from it, so remember the whole question here.
		Answer a = last;
		if (a != null && Objects.equals(a.title(), sidebarTitle) && Objects.equals(a.survival(), survivalPattern)
				&& Objects.equals(a.skyblock(), skyblockPattern)) return a.mode();
		Gamemode mode = detectUncached(sidebarTitle, survivalPattern, skyblockPattern);
		last = new Answer(sidebarTitle, survivalPattern, skyblockPattern, mode);
		return mode;
	}

	private record Answer(String title, String survival, String skyblock, Gamemode mode) {}

	private static volatile Answer last;

	private static Gamemode detectUncached(String sidebarTitle, String survivalPattern, String skyblockPattern) {
		boolean skyblock = skyblockPattern != null && !skyblockPattern.isBlank()
				&& SidebarGate.matches(sidebarTitle, skyblockPattern);
		if (survivalPattern == null || survivalPattern.isBlank()) return skyblock ? SKYBLOCK : SURVIVAL;
		if (SidebarGate.matches(sidebarTitle, survivalPattern)) return SURVIVAL;
		return skyblock ? SKYBLOCK : OTHER;
	}
}
