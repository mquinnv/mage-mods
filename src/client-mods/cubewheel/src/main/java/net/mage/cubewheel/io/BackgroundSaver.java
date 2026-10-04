package net.mage.cubewheel.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saves one file off the client thread: {@link #saveLater} hands the content (worked out on the writer thread from a
 * snapshot the caller already copied) to a single background thread, so a save never stalls a frame. Saves coalesce:
 * while one is waiting, a newer one replaces it, so a burst of changes is written once. Every write goes to a
 * temporary file that then replaces the real one, so a crash mid-write leaves the previous file whole. Each save is
 * numbered; an older one never overwrites a newer one, whichever thread gets there last. {@link #saveNow} and
 * {@link #flush} write on the calling thread (quitting, disconnecting). No Minecraft/Fabric imports.
 */
public final class BackgroundSaver {
	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");

	/** CubeWheel's one file-writing thread; a daemon, so it never keeps the game from quitting. */
	public static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "CubeWheel IO");
		t.setDaemon(true);
		return t;
	});

	private record Pending(long seq, Supplier<String> content) {}

	private final Path file;
	private final String what;
	private final Executor executor;
	private final AtomicLong seq = new AtomicLong();
	/** The newest save not yet started; null when none waits. */
	private final AtomicReference<Pending> pending = new AtomicReference<>();
	/** Held while writing: one write at a time, so the temporary file is never shared. */
	private final Object writeLock = new Object();
	/** The number of the newest save on disk (guarded by {@link #writeLock}). */
	private long written;

	/** {@code what} names the file in warnings ("tracker"). */
	public BackgroundSaver(Path file, String what, Executor executor) {
		this.file = file;
		this.what = what;
		this.executor = executor;
	}

	public BackgroundSaver(Path file, String what) {
		this(file, what, IO);
	}

	/**
	 * Saves {@code content} on the background thread. It runs there, so it must only read data the caller has
	 * already copied (no live, mutable state). If a save is already waiting, this one replaces it and nothing more is
	 * queued.
	 */
	public void saveLater(Supplier<String> content) {
		Pending p = new Pending(seq.incrementAndGet(), content);
		if (pending.getAndSet(p) == null) executor.execute(this::drain);
	}

	/** Writes {@code content} now, on this thread; a save still waiting (older) is dropped. False if it failed. */
	public boolean saveNow(String content) {
		Pending p = new Pending(seq.incrementAndGet(), () -> content);
		pending.getAndUpdate(q -> q != null && q.seq() < p.seq() ? null : q);
		return write(p);
	}

	/**
	 * Writes the save still waiting, if any, now on this thread (quit, disconnect); if the background thread is
	 * writing one, waits for it. Either way the newest save made so far is on disk when this returns.
	 */
	public void flush() {
		drain();
	}

	/** True while a save is waiting to be written. */
	public boolean pending() {
		return pending.get() != null;
	}

	/** Takes the waiting save and writes it, holding the lock throughout so {@link #flush} can wait for it. */
	private void drain() {
		synchronized (writeLock) {
			Pending p = pending.getAndSet(null);
			if (p != null) write(p);
		}
	}

	private boolean write(Pending p) {
		synchronized (writeLock) {
			if (p.seq() <= written) return true; // a newer save is already on disk
			try {
				replace(file, p.content().get());
				written = p.seq();
				return true;
			} catch (IOException | RuntimeException e) {
				LOG.warn("[cubewheel] could not save {} to {}: {}", what, file, e.toString());
				return false;
			}
		}
	}

	/** Writes {@code content} to {@code file} through a temporary file beside it, so the file is never half written. */
	public static void replace(Path file, String content) throws IOException {
		Path parent = file.getParent();
		if (parent != null) Files.createDirectories(parent);
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, content, StandardCharsets.UTF_8);
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
