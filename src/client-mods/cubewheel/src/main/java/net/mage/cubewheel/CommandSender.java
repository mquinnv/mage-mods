package net.mage.cubewheel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** The only path by which CubeWheel sends commands; refuses when the server gate is closed. */
public final class CommandSender {
	private CommandSender() {}

	/** Sends a command (leading "/" optional). Returns false, and logs, when refused. */
	public static boolean send(String commandWithSlash) {
		if (commandWithSlash == null || commandWithSlash.isBlank()) return false;
		if (ClientActions.is(commandWithSlash)) { // runs on any server; never sent, logged or noted to capture
			boolean ran = ClientActions.run(commandWithSlash);
			if (!ran) CubeWheelClient.LOG.warn("[cubewheel] unknown client action {}", commandWithSlash.trim());
			return ran;
		}
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
		// Fabric's COMMAND event normally notes this too (SentCommands passes the duplicate on once); noting it
		// here keeps capture's afterCommand and the command cooldowns right even if that event ever stops
		// covering commands sent by mods. Listener failures are logged there; the command was sent regardless.
		SentCommands.note(cmd, System.currentTimeMillis());
		return true;
	}
}
