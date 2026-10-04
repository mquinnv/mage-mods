package net.mage.cubewheel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientActionsTest {
	@BeforeEach @AfterEach void reset() { ClientActions.clear(); }

	@Test void recognisesActionsWithAndWithoutSlashAnyCaseAndSpaces() {
		assertTrue(ClientActions.is("cubewheel:arrange"));
		assertTrue(ClientActions.is("/cubewheel:arrange"));
		assertTrue(ClientActions.is("  /CubeWheel:Arrange "));
		assertEquals("arrange", ClientActions.name("/CubeWheel:Arrange "));
		assertEquals("arrange", ClientActions.name("cubewheel:arrange"));
	}

	@Test void serverCommandsAreNotActions() {
		assertFalse(ClientActions.is("/warp crops"));
		assertFalse(ClientActions.is("cubewheel"));
		assertFalse(ClientActions.is("cubewheel arrange"));
		assertFalse(ClientActions.is("//cubewheel:arrange"));
		assertFalse(ClientActions.is(null));
		assertFalse(ClientActions.is(""));
		assertNull(ClientActions.name("/warp crops"));
		assertNull(ClientActions.name(null));
	}

	@Test void runRunsTheRegisteredActionOnce() {
		AtomicInteger arrange = new AtomicInteger();
		AtomicInteger other = new AtomicInteger();
		ClientActions.register("arrange", arrange::incrementAndGet);
		ClientActions.register("other", other::incrementAndGet);
		assertTrue(ClientActions.run("/CubeWheel:ARRANGE"));
		assertEquals(1, arrange.get());
		assertEquals(0, other.get());
	}

	@Test void unknownActionsAndPlainCommandsReturnFalseAndDoNothing() {
		AtomicInteger n = new AtomicInteger();
		ClientActions.register("arrange", n::incrementAndGet);
		assertFalse(ClientActions.run("cubewheel:nope"));
		assertFalse(ClientActions.run("/warp crops"));
		assertFalse(ClientActions.run(null));
		assertEquals(0, n.get());
	}
}
