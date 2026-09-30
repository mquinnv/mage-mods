package net.mage.cubewheel.sva;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The parsed {@code GET /api/svas/survival} response: SVAs de-duplicated by itemType (the API lists a few
 * twice), sorted by name, plus a name index for tooltips. Immutable once built. Pure.
 */
public final class SvaCatalog {
	private final List<Sva> all;
	private final Map<String, Sva> byType;
	private final Map<String, List<Sva>> byName;

	private SvaCatalog(List<Sva> all) {
		this.all = List.copyOf(all);
		Map<String, Sva> types = new HashMap<>();
		Map<String, List<Sva>> names = new HashMap<>();
		for (Sva s : this.all) {
			types.put(s.itemType(), s);
			names.computeIfAbsent(LegacyText.normalize(s.plainName()), k -> new ArrayList<>(1)).add(s);
		}
		this.byType = Map.copyOf(types);
		Map<String, List<Sva>> frozen = new HashMap<>();
		names.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
		this.byName = Map.copyOf(frozen);
	}

	/** Parses the API body; throws IllegalArgumentException if it is not a JSON array. Bad entries are skipped. */
	public static SvaCatalog parse(String json) {
		if (json == null) throw new IllegalArgumentException("no body");
		JsonElement root;
		try {
			root = JsonParser.parseString(json);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("not JSON: " + e.getMessage());
		}
		if (!root.isJsonArray()) throw new IllegalArgumentException("expected a JSON array");
		Map<String, Sva> out = new LinkedHashMap<>();
		for (JsonElement el : root.getAsJsonArray()) {
			if (el == null || !el.isJsonObject()) continue;
			JsonObject o = el.getAsJsonObject();
			String type = str(o, "itemType");
			String name = str(o, "displayName");
			if (type == null || type.isBlank() || name == null || out.containsKey(type)) continue;
			out.put(type, Sva.of(type, integer(o, "version", 0), str(o, "material"), name, str(o, "slot"),
					integer(o, "circulation", 0), blankToNull(str(o, "itemModel")), strings(o, "lore"),
					enchants(o), integer(o, "leatherColor", -1)));
		}
		List<Sva> list = new ArrayList<>(out.values());
		list.sort(Comparator.comparing(Sva::sortKey));
		return new SvaCatalog(list);
	}

	public List<Sva> all() {
		return all;
	}

	/** The SVA with this itemType, or null. */
	public Sva byType(String itemType) {
		return itemType == null ? null : byType.get(itemType);
	}

	/** Candidates for an in-game item; empty when its name is not an SVA name. */
	public record Match(List<Sva> svas) {
		public boolean isEmpty() {
			return svas.isEmpty();
		}

		public List<String> types() {
			return svas.stream().map(Sva::itemType).toList();
		}
	}

	private static final Match NONE = new Match(List.of());

	/**
	 * Finds the SVA(s) an in-game item is. The name (codes stripped, case-insensitive) must equal an SVA's
	 * name; when several SVAs share it, the vanilla item id, then the item model, then the lore narrow it
	 * down (each step only if it leaves at least one). {@code plainLore} is only called in that case.
	 *
	 * @param itemPath  the stack's item id path ("diamond_sword") or null
	 * @param itemModel the stack's item_model component ("manalabs:…") or null
	 */
	public Match match(String hoverName, String itemPath, String itemModel, Supplier<List<String>> plainLore) {
		if (hoverName == null) return NONE;
		List<Sva> c = byName.get(LegacyText.normalize(hoverName));
		if (c == null) return NONE;
		if (c.size() == 1) return new Match(c);
		if (itemPath != null) c = narrow(c, s -> s.itemId().equals(itemPath));
		if (c.size() > 1 && itemModel != null) c = narrow(c, s -> itemModel.equals(s.itemModel()));
		if (c.size() > 1) c = byLore(c, plainLore.get());
		return new Match(c);
	}

	private static List<Sva> narrow(List<Sva> in, java.util.function.Predicate<Sva> keep) {
		List<Sva> out = in.stream().filter(keep).toList();
		return out.isEmpty() ? in : out;
	}

	/** Keeps the candidates whose non-blank lore lines best overlap the item's lore. */
	private static List<Sva> byLore(List<Sva> in, List<String> itemLore) {
		if (itemLore == null || itemLore.isEmpty()) return in;
		Set<String> have = new HashSet<>();
		for (String l : itemLore) have.add(LegacyText.normalize(l));
		int best = -1;
		List<Sva> out = new ArrayList<>();
		for (Sva s : in) {
			int score = 0;
			for (String l : s.plainLore()) {
				String k = LegacyText.normalize(l);
				if (!k.isEmpty() && have.contains(k)) score++;
			}
			if (score > best) {
				best = score;
				out.clear();
			}
			if (score == best) out.add(s);
		}
		return best <= 0 ? in : List.copyOf(out);
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || !e.isJsonPrimitive() ? null : e.getAsString();
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static int integer(JsonObject o, String key, int def) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return def;
		try {
			return e.getAsInt();
		} catch (NumberFormatException ex) {
			return def;
		}
	}

	private static List<String> strings(JsonObject o, String key) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonArray()) return List.of();
		List<String> out = new ArrayList<>();
		JsonArray a = e.getAsJsonArray();
		for (JsonElement x : a) {
			if (x != null && x.isJsonPrimitive()) out.add(x.getAsString());
		}
		return out;
	}

	private static Map<String, Integer> enchants(JsonObject o) {
		JsonElement e = o.get("enchants");
		if (e == null || !e.isJsonObject()) return Map.of();
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
			JsonElement v = en.getValue();
			if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) out.put(en.getKey(), v.getAsInt());
		}
		return out;
	}
}
