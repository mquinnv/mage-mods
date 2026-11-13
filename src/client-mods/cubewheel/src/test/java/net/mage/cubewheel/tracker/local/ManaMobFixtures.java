package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.mage.cubewheel.capture.CaptureLog;

/**
 * The {@code model_shape} lines of the 2026-11-12 mana-mob capture ({@code /manamobs/<name>.json}), one snap list
 * per line. Capture keys map onto {@link CaptureLog.Part} ({@code entity} is the id; a missing key is null), and
 * {@link ModelMobs.Snap#from} sets {@code owned = false}, since capture does not record it.
 */
final class ManaMobFixtures {
	static final String WOLF = "mana_wolf";
	static final String VIPER = "viper";
	/** Capture 2026-11-13, Wolfhaven: one bone per cloud; line 1 has the recycled slime, line 2 two Mana Slimes. */
	static final String MANA_SLIME = "mana_slime";

	private ManaMobFixtures() {}

	static List<List<ModelMobs.Snap>> load(String name) {
		try (InputStream in = ManaMobFixtures.class.getResourceAsStream("/manamobs/" + name + ".json")) {
			assertNotNull(in, name);
			List<List<ModelMobs.Snap>> lines = new ArrayList<>();
			for (JsonElement line : JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
					.getAsJsonArray()) {
				List<ModelMobs.Snap> snaps = new ArrayList<>();
				for (JsonElement p : line.getAsJsonObject().getAsJsonArray("parts")) {
					snaps.add(ModelMobs.Snap.from(part(p.getAsJsonObject())));
				}
				lines.add(List.copyOf(snaps));
			}
			return List.copyOf(lines);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static CaptureLog.Part part(JsonObject o) {
		List<Integer> passengers = new ArrayList<>();
		if (has(o, "passengers")) for (JsonElement e : o.getAsJsonArray("passengers")) passengers.add(e.getAsInt());
		return new CaptureLog.Part(o.get("entity").getAsInt(), string(o, "type"),
				has(o, "vehicle") ? o.get("vehicle").getAsInt() : null, List.copyOf(passengers),
				has(o, "invisible") && o.get("invisible").getAsBoolean(), has(o, "mob") && o.get("mob").getAsBoolean(),
				string(o, "customName"), string(o, "text"), o.get("x").getAsDouble(), o.get("y").getAsDouble(),
				o.get("z").getAsDouble());
	}

	/** {@code snaps} with every id (own, vehicle, passengers) offset by {@code idOffset} and moved by dx/dz. */
	static List<ModelMobs.Snap> copy(List<ModelMobs.Snap> snaps, int idOffset, double dx, double dz) {
		return snaps.stream().map(s -> new ModelMobs.Snap(s.id() + idOffset, s.type(),
				s.vehicle() == null ? null : s.vehicle() + idOffset,
				s.passengers().stream().map(p -> p + idOffset).toList(), s.invisible(), s.mob(), s.owned(),
				s.customName(), s.text(), s.x() + dx, s.y(), s.z() + dz)).toList();
	}

	static List<ModelMobs.Snap> without(List<ModelMobs.Snap> snaps, Integer... ids) {
		Set<Integer> drop = Set.of(ids);
		return snaps.stream().filter(s -> !drop.contains(s.id())).toList();
	}

	static ModelMobs.Snap byId(List<ModelMobs.Snap> snaps, int id) {
		return snaps.stream().filter(s -> s.id() == id).findFirst().orElseThrow();
	}

	private static boolean has(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull();
	}

	private static String string(JsonObject o, String key) {
		return has(o, key) ? o.get(key).getAsString() : null;
	}
}
