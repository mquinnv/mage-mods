package com.mage.cubewheel.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Loads, validates and saves cubewheel.json. Pure: no Minecraft/Fabric imports. */
public final class ConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Set<String> DYNAMIC_SOURCES = Set.of("homes", "vaults");

	private final Path file;
	private CubeWheelConfig current;

	public ConfigStore(Path file) {
		this.file = file;
	}

	public CubeWheelConfig current() {
		if (current == null) current = DefaultConfig.create();
		return current;
	}

	/** Returns null on success, otherwise an error message (previous config is kept). */
	public String reload() {
		if (!Files.exists(file)) {
			current = DefaultConfig.create();
			try {
				save();
			} catch (IOException | RuntimeException e) {
				return "cubewheel.json: could not write defaults: " + e.getMessage();
			}
			return null;
		}
		CubeWheelConfig parsed;
		try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonReader r = new JsonReader(in);
			r.setStrictness(Strictness.STRICT);
			parsed = GSON.fromJson(r, CubeWheelConfig.class);
			if (r.peek() != JsonToken.END_DOCUMENT) throw new JsonParseException("trailing content");
		} catch (JsonParseException | IOException | IllegalStateException e) {
			if (current == null) current = DefaultConfig.create();
			return "cubewheel.json: " + e.getMessage();
		}
		if (parsed == null) parsed = DefaultConfig.create();
		normalize(parsed);
		current = parsed;
		return null;
	}

	public void save() throws IOException {
		Path parent = file.getParent();
		if (parent != null) Files.createDirectories(parent);
		try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(current(), w);
		}
	}

	private static void normalize(CubeWheelConfig c) {
		if (c.serverHosts == null) c.serverHosts = DefaultConfig.serverHosts();
		if (c.tracker == null) c.tracker = new CubeWheelConfig.Tracker();
		if (c.tracker.sources == null) c.tracker.sources = DefaultConfig.trackerSources();
		c.wheel = c.wheel == null ? DefaultConfig.wheel() : normalizeNodes(c.wheel);
		c.vaultCount = Math.max(0, Math.min(54, c.vaultCount));
		c.listThreshold = Math.max(3, Math.min(16, c.listThreshold));
		c.tracker.nearThreshold = Math.max(0.0, Math.min(1.0, c.tracker.nearThreshold));
		c.tracker.hudMaxLines = Math.max(1, Math.min(20, c.tracker.hudMaxLines));
	}

	private static List<WheelNode> normalizeNodes(List<WheelNode> in) {
		List<WheelNode> out = new ArrayList<>();
		for (WheelNode n : in) {
			if (n == null || n.label == null || n.label.isBlank()) continue;
			boolean hasCommand = n.command != null && !n.command.isBlank();
			boolean hasDynamic = n.dynamic != null && DYNAMIC_SOURCES.contains(n.dynamic);
			if (hasDynamic) {
				if (hasCommand) continue;
				n.command = null;
				n.children = n.children == null ? new ArrayList<>() : normalizeNodes(n.children);
			} else {
				if (n.dynamic != null) continue; // unknown dynamic source
				if (hasCommand == (n.children != null)) continue; // need exactly one
				if (hasCommand) {
					String cmd = n.command.trim();
					n.command = cmd.startsWith("/") ? cmd : "/" + cmd;
				} else {
					n.command = null;
					n.children = normalizeNodes(n.children);
				}
			}
			out.add(n);
		}
		return out;
	}
}
