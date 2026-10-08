package net.mage.cubewheel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SentCommandsTest {
	@Test void listenersSeeEveryCommandWithASlashOnce() {
		List<String> seen = new ArrayList<>();
		SentCommands.Listener l = (c, now) -> seen.add(c + "@" + now);
		SentCommands.Listener thrower = (c, now) -> { throw new IllegalStateException("boom"); };
		SentCommands.listen(thrower); // a failing listener is logged and does not stop the others
		SentCommands.listen(l);
		try {
			SentCommands.note(" heal ", 1_000);
			SentCommands.note("/heal", 1_050); // CommandSender and the COMMAND event both see a wheel-sent command
			SentCommands.note("/heal", 1_500); // a real second press
			SentCommands.note("/fly", 1_510);
			SentCommands.note("  ", 1_600); // blank: nothing
			SentCommands.note(null, 1_700);
			assertEquals(List.of("/heal@1000", "/heal@1500", "/fly@1510"), seen);
		} finally {
			SentCommands.forget(l);
			SentCommands.forget(thrower);
		}
		SentCommands.note("/heal", 9_000);
		assertEquals(3, seen.size()); // forgotten
	}
}
