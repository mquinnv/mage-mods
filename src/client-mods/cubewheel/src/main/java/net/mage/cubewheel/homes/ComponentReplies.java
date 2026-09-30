package net.mage.cubewheel.homes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Minecraft adapter: turns a chat Component into the plain data HomesParser understands. */
public final class ComponentReplies {
	private ComponentReplies() {}

	/**
	 * Plain text via getString(); click commands from every styled segment of the whole tree
	 * (siblings and nested children, with inherited styles), deduplicated in first-seen order.
	 * Commands are normalised to start with "/" since servers send both forms.
	 */
	public static HomesParser.Reply toReply(Component message) {
		Set<String> commands = new LinkedHashSet<>();
		message.visit((Style style, String text) -> {
			String cmd = switch (style.getClickEvent()) {
				case ClickEvent.RunCommand run -> run.command();
				case ClickEvent.SuggestCommand suggest -> suggest.command();
				case null, default -> null;
			};
			if (cmd != null && !cmd.isBlank()) {
				String c = cmd.trim();
				commands.add(c.startsWith("/") ? c : "/" + c);
			}
			return Optional.empty();
		}, Style.EMPTY);
		return new HomesParser.Reply(message.getString(), new ArrayList<>(commands));
	}
}
