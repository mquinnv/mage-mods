package net.mage.cubewheel.cooldown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.SentCommands;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandCooldownsTest {
	private static final String HEALED = "§aYou have been healed.";
	/** Invented wording: no refusal has been captured yet; the matcher is generic (command word + a duration). */
	private static final String REFUSED = "§cYou must wait 2m 30s before using /heal again";
	private static final long FIVE_MIN = 300_000;

	private static CommandCooldowns heal(Path file) {
		CommandCooldowns c = new CommandCooldowns(file);
		c.configure(Map.of("/heal", "5m"));
		return c;
	}

	@Test void aSuccessWithinThreeSecondsStartsThePlaceholderCountdown() {
		CommandCooldowns c = heal(null);
		c.sent("/heal", 1_000);
		assertTrue(c.rows(1_000).isEmpty()); // nothing until the server confirms
		assertFalse(c.chat(HEALED, 1_400)); // nothing learned
		assertEquals(List.of(new CommandCooldowns.Row("/heal", "Heal", 1_400 + FIVE_MIN, "minecraft:golden_apple")), c.rows(1_400));
		// The server sometimes repeats the line within a second: it changes nothing.
		assertFalse(c.chat(HEALED, 1_900));
		assertEquals(1_400 + FIVE_MIN, c.rows(1_900).get(0).endsAt());
		assertTrue(c.rows(1_400 + FIVE_MIN).isEmpty());
	}

	@Test void noReplyWithinThreeSecondsStartsNothing() {
		CommandCooldowns c = heal(null);
		c.sent("/heal", 0);
		assertTrue(c.rows(2_999).isEmpty());
		assertFalse(c.chat(HEALED, 3_001)); // too late to belong to that press
		assertTrue(c.rows(3_001).isEmpty());
	}

	@Test void aRefusalSetsTheRemainingTimeAndLearnsTheLengthFromTheLastSuccess(@TempDir Path dir) {
		Path f = dir.resolve("cubewheel-cooldowns.json");
		CommandCooldowns c = heal(f);
		c.sent("/heal", 0);
		c.chat(HEALED, 0);
		c.sent("/heal", FIVE_MIN);
		assertTrue(c.chat(REFUSED, FIVE_MIN)); // learned: 5 min since the success + 2m 30s left = 7m 30s
		assertEquals(List.of(new CommandCooldowns.Row("/heal", "Heal", FIVE_MIN + 150_000, "minecraft:golden_apple")), c.rows(FIVE_MIN));
		assertEquals(450_000, c.cooldownMs("/heal"));
		c.save();
		CommandCooldowns d = heal(f);
		d.load();
		assertEquals(450_000, d.cooldownMs("/heal"));
		assertEquals(Map.of("/heal", 450_000L), d.learned());
		// The learned length is what the next success counts down.
		d.sent("/heal", 1_000_000);
		d.chat(HEALED, 1_000_000);
		assertEquals(1_450_000, d.rows(1_000_000).get(0).endsAt());
	}

	@Test void aRefusalWithoutAKnownSuccessOnlySetsTheRemainingTime() {
		CommandCooldowns c = heal(null);
		c.sent("/heal", 0);
		assertFalse(c.chat("You cannot heal for another 45 seconds!", 100));
		assertEquals(45_100, c.rows(100).get(0).endsAt());
		assertEquals(FIVE_MIN, c.cooldownMs("/heal")); // still the placeholder
	}

	@Test void playerChatIsIgnored() {
		CommandCooldowns c = heal(null);
		c.sent("/heal", 0);
		assertFalse(c.chat("§r [Rank] Bob: heal me in 5 minutes", 100));
		assertFalse(c.chat("[Rank] Bob: heal me in 5 minutes", 200));
		assertFalse(c.chat("[Bob -> me] you have been healed.", 300));
		assertTrue(c.rows(300).isEmpty());
		c.chat(REFUSED, 400); // the press is still pending: a server line counts
		assertEquals(150_400, c.rows(400).get(0).endsAt());
	}

	@Test void aSecondSuccessWhileTheCountdownRunsShortensTheLearnedLength() {
		CommandCooldowns c = heal(null);
		c.sent("/heal", 0);
		c.chat(HEALED, 0);
		c.sent("/heal", 180_000);
		assertTrue(c.chat(HEALED, 180_000));
		assertEquals(180_000, c.cooldownMs("/heal"));
		assertEquals(360_000, c.rows(180_000).get(0).endsAt());
	}

	@Test void anUnconfiguredCommandStartsNothing() {
		CommandCooldowns c = heal(null);
		c.sent("/fly", 0);
		assertFalse(c.chat("Fly mode enabled", 100));
		assertFalse(c.chat("You must wait 10s before using /fly again", 200));
		assertTrue(c.rows(200).isEmpty());
		assertEquals(0, c.cooldownMs("/fly"));
	}

	@Test void aTypedCommandIsSeenThroughSentCommands() {
		CommandCooldowns c = heal(null);
		SentCommands.Listener l = c::sent;
		SentCommands.listen(l);
		try {
			SentCommands.note("heal", 0); // the chat screen sends it without the slash
			c.chat(HEALED, 100);
			assertEquals(100 + FIVE_MIN, c.rows(100).get(0).endsAt());
		} finally {
			SentCommands.forget(l);
		}
	}

	@Test void commandsAreMatchedByTheirFirstWordInAnyCase() {
		CommandCooldowns c = new CommandCooldowns(null);
		c.configure(Map.of("Heal", "90s", "/bad", "soon", "/fly", ""));
		assertEquals(90_000, c.cooldownMs("/HEAL"));
		assertEquals(0, c.cooldownMs("/bad")); // an unparseable or blank duration is not a placeholder
		assertEquals(0, c.cooldownMs("/fly"));
		c.sent("/HEAL  now ", 0);
		c.chat(HEALED, 0);
		assertEquals(90_000, c.rows(0).get(0).endsAt());
		assertEquals("/heal", CommandCooldowns.key(" /Heal me "));
		assertEquals("/warp", CommandCooldowns.key("warp crops"));
	}

	@Test void unknownCommandsGetAPaperIconAndATitleCasedLabel() {
		CommandCooldowns c = new CommandCooldowns(null);
		c.configure(Map.of("/fly", "10s"));
		c.sent("/fly", 0);
		assertFalse(c.chat("Fly mode enabled", 0)); // no success line is known for /fly: only a refusal can start it
		assertTrue(c.rows(0).isEmpty());
		c.chat("You must wait 10 seconds before using /fly again", 0);
		assertEquals(List.of(new CommandCooldowns.Row("/fly", "Fly", 10_000, "minecraft:paper")), c.rows(0));
	}

	@Test void theLearnedFileHoldsWholeSecondsPerCommand(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("cubewheel-cooldowns.json");
		CommandCooldowns c = heal(f);
		c.sent("/heal", 0);
		c.chat(HEALED, 0);
		c.sent("/heal", 100_400);
		c.chat(HEALED, 100_400); // 100.4 s -> 100 s
		c.save();
		assertTrue(Files.readString(f).contains("\"/heal\": 100"), Files.readString(f));
		Files.writeString(f, "{\"/heal\": 0, \"/fly\": 30, \"/warp\": 999999}");
		CommandCooldowns d = heal(f);
		d.load();
		assertEquals(FIVE_MIN, d.cooldownMs("/heal")); // implausible values are not loaded
		assertEquals(30_000, d.cooldownMs("/fly"));
		assertEquals(0, d.cooldownMs("/warp"));
		Files.writeString(f, "not json");
		heal(f).load(); // logged, no throw
	}
}
