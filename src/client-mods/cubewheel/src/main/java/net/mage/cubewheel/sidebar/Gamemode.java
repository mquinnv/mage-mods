package net.mage.cubewheel.sidebar;

/**
 * Which ManaCube gamemode the sidebar title says the player is in. Pure: no Minecraft/Fabric imports.
 */
public enum Gamemode {
	SURVIVAL("survival"), SKYBLOCK("skyblock"), OTHER("other");

	private final String id;

	Gamemode(String id) {
		this.id = id;
	}

	/** Stable lower-case name, safe to use as a key. */
	public String id() {
		return id;
	}

	/**
	 * Survival is checked first, so a title matching both is Survival. A blank survival pattern keeps its
	 * old meaning (gate off, always Survival). Michael 2026-11-06: a blank Skyblock pattern never detects
	 * Skyblock, unlike {@link SidebarGate#matches}, so an empty pattern cannot claim every server.
	 */
	public static Gamemode detect(String sidebarTitle, String survivalPattern, String skyblockPattern) {
		if (SidebarGate.matches(sidebarTitle, survivalPattern)) return SURVIVAL;
		if (skyblockPattern != null && !skyblockPattern.isBlank() && SidebarGate.matches(sidebarTitle, skyblockPattern)) return SKYBLOCK;
		return OTHER;
	}
}
