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
		assertEquals(List.of(), offenders, "Jade API or compat/jade/ referenced outside compat/jade/");
	}

	private static boolean mentionsJade(Path p) {
		try {
			// The Jade API itself, or this compat package (whose classes load Jade): fabric.mod.json, not Java, is
			// the only legitimate way in.
			String src = Files.readString(p);
			return src.contains("snownee.jade") || src.contains("net.mage.cubewheel.compat.jade");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
