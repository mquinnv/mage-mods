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
		public int hudMaxLines = 6;
		public boolean hudVisible = true;
		public Map<String, String> sources;
		/** Sent one at a time by the "Refresh trackers" key; each should open a progress menu. */
		public List<String> refreshCommands;
		/** Sidebar key ("Skills") -> regex on trackable names whose current value follows it live. */
		public Map<String, String> sidebarLinks;
	}
}
