package com.mage.cubewheel.config;

import java.util.List;
import java.util.Map;

public final class CubeWheelConfig {
	public boolean enabled = true;
	public List<String> serverHosts;
	public int vaultCount = 3;
	public int listThreshold = 8;
	public Tracker tracker = new Tracker();
	public List<WheelNode> wheel;

	public static final class Tracker {
		public double nearThreshold = 0.8;
		public int hudMaxLines = 8;
		public boolean hudVisible = true;
		public Map<String, String> sources;
		/** Sent one at a time by the "Refresh trackers" key; each should open a progress menu. */
		public List<String> refreshCommands;
		/** Sidebar key ("Skills") -> regex on trackable names whose current value follows it live. */
		public Map<String, String> sidebarLinks;
		/**
		 * Regex on the sidebar title: menu scanning, refresh runs and local counting only run while it
		 * matches (ManaCube hosts other gamemodes on the same address). "" switches this check off.
		 */
		public String survivalSidebarPattern = DefaultConfig.SURVIVAL_SIDEBAR;
		/** Local counting: live "~" estimates between menu reads. */
		public Local local = new Local();
	}

	public static final class Local {
		/** Master switch; when false nothing is counted and stored estimates are not shown (not deleted). */
		public boolean enabled = true;
		public boolean blocks = true;
		public boolean kills = true;
		public boolean fish = true;
		/** World names recognised at the start of an objective's noun ("Wolfhaven Resources"). */
		public List<String> worlds;
		/** Worlds that count as "special worlds (/worlds)". */
		public List<String> specialWorlds;
	}
}
