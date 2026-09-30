package com.mage.cubewheel.sva;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * URLs and response parsing for ManaCube's public SVA API and Mojang's name look-up. Pure.
 *
 * <ul>
 *   <li>{@code GET https://api.manacube.com/api/svas/survival} → the catalog (see {@link SvaCatalog})</li>
 *   <li>{@code GET https://api.manacube.com/api/svas/survival/<uuid with dashes>} → owned SVAs
 *       {@code [{id, itemType, originalOwner, owner, obtainTime, customModelData}]}; an undashed UUID is a 400,
 *       an unknown player an empty array. They join the catalog on {@code itemType}.</li>
 *   <li>{@code GET https://api.mojang.com/users/profiles/minecraft/<name>} → {@code {"id": "<32 hex>", "name"}};
 *       unknown names are a 404 (or 204).</li>
 * </ul>
 */
public final class SvaApi {
	private SvaApi() {}

	public static final String BASE = "https://api.manacube.com/api/svas/survival";
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final Pattern HEX32 = Pattern.compile("[0-9a-f]{32}");

	public static String catalogUrl() {
		return BASE;
	}

	/** Owned-SVAs URL for a UUID with or without dashes; throws IllegalArgumentException if it is not one. */
	public static String ownedUrl(String uuid) {
		return BASE + "/" + dashed(uuid);
	}

	public static String mojangUrl(String name) {
		if (!validName(name)) throw new IllegalArgumentException("not a Minecraft name: " + name);
		return "https://api.mojang.com/users/profiles/minecraft/" + name;
	}

	public static boolean validName(String name) {
		return name != null && NAME.matcher(name).matches();
	}

	/** "7f0e9af0-4a31-4e69-8377-14bfd7ed5a90" from either form, lower-case. */
	public static String dashed(String uuid) {
		String hex = uuid == null ? "" : uuid.replace("-", "").toLowerCase(Locale.ROOT);
		if (!HEX32.matcher(hex).matches()) throw new IllegalArgumentException("not a UUID: " + uuid);
		return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
				+ hex.substring(16, 20) + "-" + hex.substring(20);
	}

	/** itemType → how many the player owns. Throws IllegalArgumentException if the body is not a JSON array. */
	public static Map<String, Integer> parseOwned(String json) {
		JsonElement root;
		try {
			root = json == null ? null : JsonParser.parseString(json);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("not JSON: " + e.getMessage());
		}
		if (root == null || !root.isJsonArray()) throw new IllegalArgumentException("expected a JSON array");
		Map<String, Integer> out = new LinkedHashMap<>();
		for (JsonElement el : root.getAsJsonArray()) {
			if (el == null || !el.isJsonObject()) continue;
			JsonElement t = el.getAsJsonObject().get("itemType");
			if (t == null || !t.isJsonPrimitive() || t.getAsString().isBlank()) continue;
			out.merge(t.getAsString(), 1, Integer::sum);
		}
		return Map.copyOf(out);
	}

	/** The dashed UUID from a Mojang profile body; empty for an error body or anything unexpected. */
	public static Optional<String> parseMojang(String json) {
		try {
			JsonElement root = json == null || json.isBlank() ? null : JsonParser.parseString(json);
			if (root == null || !root.isJsonObject()) return Optional.empty();
			JsonElement id = root.getAsJsonObject().get("id");
			if (id == null || !id.isJsonPrimitive()) return Optional.empty();
			return Optional.of(dashed(id.getAsString()));
		} catch (JsonParseException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
