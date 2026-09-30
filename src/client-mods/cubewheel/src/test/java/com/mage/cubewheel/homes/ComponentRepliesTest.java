package com.mage.cubewheel.homes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

class ComponentRepliesTest {
	private static MutableComponent run(String text, String command) {
		return Component.literal(text).withStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command)));
	}

	@Test
	void collectsClicksFromSiblingsAndNestedChildren() {
		MutableComponent nested = Component.literal("")
				.append(run("farm", "/home farm"))
				.append(Component.literal(", "))
				.append(Component.literal("mine").withStyle(
						Style.EMPTY.withClickEvent(new ClickEvent.SuggestCommand("/home mine"))));
		Component msg = Component.literal("Homes (3): ")
				.append(run("base", "/home base"))
				.append(Component.literal(", "))
				.append(nested);

		HomesParser.Reply r = ComponentReplies.toReply(msg);

		assertEquals("Homes (3): base, farm, mine", r.text());
		assertEquals(List.of("/home base", "/home farm", "/home mine"), r.clickCommands());
		assertEquals(Optional.of(List.of("base", "farm", "mine")), HomesParser.parse(r));
	}

	@Test
	void inheritedClickOnParentIsReportedOnce() {
		Component msg = Component.literal("")
				.withStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/home a")))
				.append(Component.literal("a"))
				.append(Component.literal(" (click)"));

		assertEquals(List.of("/home a"), ComponentReplies.toReply(msg).clickCommands());
	}

	@Test
	void slashIsAddedWhenCommandOmitsIt() {
		Component msg = Component.literal("x").append(run("a", "home a")).append(run("b", "home b"));

		assertEquals(List.of("/home a", "/home b"), ComponentReplies.toReply(msg).clickCommands());
	}

	@Test
	void plainOwnMessageHasNoClicksAndDoesNotParse() {
		HomesParser.Reply r = ComponentReplies.toReply(Component.literal("[CubeWheel] config reloaded"));

		assertTrue(r.clickCommands().isEmpty());
		assertEquals(Optional.empty(), HomesParser.parse(r));
	}
}
