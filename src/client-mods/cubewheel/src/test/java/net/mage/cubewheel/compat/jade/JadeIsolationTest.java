package net.mage.cubewheel.compat.jade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Jade is an optional dependency: CubeWheel must load without it. Only classes under {@code compat/jade/} (reached
 * solely through the {@code "jade"} entrypoint) may reference Jade, so no other class can drag it onto the classpath.
 */
class JadeIsolationTest {
	private static final Path MAIN = Path.of("src/main/java");
	private static final Path COMPAT = MAIN.resolve("net/mage/cubewheel/compat/jade");

	@Test
	void noJadeReferencesOutsideCompat() throws IOException {
		List<String> offenders;
		try (Stream<Path> files = Files.walk(MAIN)) {
			offenders = files.filter(p -> p.toString().endsWith(".java"))
					.filter(p -> !p.startsWith(COMPAT))
					.filter(JadeIsolationTest::mentionsJade)
					.map(Path::toString)
					.sorted()
					.toList();
		}
		assertEquals(List.of(), offenders, "Jade API referenced outside compat/jade/");
	}

	private static boolean mentionsJade(Path p) {
		try {
			return Files.readString(p).contains("snownee.jade");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
