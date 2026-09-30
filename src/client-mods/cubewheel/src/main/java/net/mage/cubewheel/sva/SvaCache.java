package net.mage.cubewheel.sva;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Raw API bodies on disk in {@code config/cubewheel-cache/}: {@code svas-survival.json}, {@code owned-<uuid>.json}
 * (the player's own only) and {@code index.json} with the fetch times. Missing or unreadable files read as empty;
 * write failures are logged, never thrown. Pure: no Minecraft imports.
 */
public final class SvaCache {
	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	private static final Gson GSON = new Gson();

	public record Entry(String body, long fetchedAt) {}

	private static final class Index {
		long catalog;
		Map<String, Long> owned = new HashMap<>();
	}

	private final Path dir;

	public SvaCache(Path dir) {
		this.dir = dir;
	}

	public synchronized Optional<Entry> readCatalog() {
		Index idx = index();
		return read("svas-survival.json", idx.catalog);
	}

	public synchronized Optional<Entry> readOwned(String uuid) {
		String key = SvaApi.dashed(uuid);
		Long at = index().owned.get(key);
		return at == null ? Optional.empty() : read("owned-" + key + ".json", at);
	}

	public synchronized void writeCatalog(String body, long now) {
		Index idx = index();
		if (write("svas-survival.json", body)) {
			idx.catalog = now;
			writeIndex(idx);
		}
	}

	public synchronized void writeOwned(String uuid, String body, long now) {
		String key = SvaApi.dashed(uuid);
		Index idx = index();
		if (write("owned-" + key + ".json", body)) {
			idx.owned.put(key, now);
			writeIndex(idx);
		}
	}

	private Optional<Entry> read(String name, long fetchedAt) {
		if (fetchedAt <= 0) return Optional.empty();
		try {
			Path f = dir.resolve(name);
			if (!Files.isRegularFile(f)) return Optional.empty();
			return Optional.of(new Entry(Files.readString(f, StandardCharsets.UTF_8), fetchedAt));
		} catch (IOException e) {
			LOG.warn("[cubewheel] SVA cache {} unreadable: {}", name, e.toString());
			return Optional.empty();
		}
	}

	private Index index() {
		Path f = dir.resolve("index.json");
		try {
			if (!Files.isRegularFile(f)) return new Index();
			Index idx = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Index.class);
			if (idx == null) return new Index();
			if (idx.owned == null) idx.owned = new HashMap<>();
			return idx;
		} catch (IOException | JsonParseException | IllegalStateException e) {
			return new Index();
		}
	}

	private void writeIndex(Index idx) {
		write("index.json", GSON.toJson(idx));
	}

	/** Atomic-ish write (temp file + move); false and a log line on failure. */
	private boolean write(String name, String body) {
		try {
			Files.createDirectories(dir);
			Path tmp = dir.resolve(name + ".tmp");
			Files.writeString(tmp, body, StandardCharsets.UTF_8);
			try {
				Files.move(tmp, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicFailed) {
				Files.move(tmp, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException e) {
			LOG.warn("[cubewheel] could not write SVA cache {}: {}", name, e.toString());
			return false;
		}
	}
}
