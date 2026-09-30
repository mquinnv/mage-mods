package com.mage.cubewheel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** The only path by which CubeWheel sends commands; refuses when the server gate is closed. */
public final class CommandSender {
	private CommandSender() {}

	/** Sends a command (leading "/" optional). Returns false, and logs, when refused. */
	public static boolean send(String commandWithSlash) {
		if (commandWithSlash == null || commandWithSlash.isBlank()) return false;
		String cmd = commandWithSlash.trim();
		if (cmd.startsWith("/")) cmd = cmd.substring(1);
		if (!ServerGate.active(CubeWheelClient.config().current())) {
			CubeWheelClient.LOG.warn("[cubewheel] refused to send /{}: not on a gated server", cmd);
			return false;
		}
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			CubeWheelClient.LOG.warn("[cubewheel] refused to send /{}: no connection", cmd);
			return false;
		}
		connection.sendCommand(cmd);
		return true;
	}
}
