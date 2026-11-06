package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.mage.cubewheel.tracker.ContainerScanner.ItemView;

/**
 * The Skyblock menus of Michael's first Skyblock capture (2026-11-06), every one opened from /jobs: container
 * items only (slot, id, name, lore), copied from the capture's {@code kind:"container"} lines. No chat.
 */
final class SkyblockCapture {
	/** The /jobs hub: Island Team Quests, Task Board, Prestige, Boss Challenges, Season Challenges, Go Back. */
	static final String HUB = "hub";
	/** The hub re-sent with its Island Team Quests slot empty for a moment (a click being processed). */
	static final String HUB_WITHOUT_QUESTS = "hub-without-quests";
	static final String ISLAND_QUESTS = "island-quests";
	static final String TASK_BOARD = "task-board";
	static final String PRESTIGE_LIST = "prestige-list";
	static final String PRESTIGE_RANKS = "prestige-ranks";
	static final String BOSS_CHALLENGES = "boss-challenges";
	static final String SEASON_CHALLENGES = "season-challenges";

	private SkyblockCapture() {}

	static List<ItemView> load(String name) {
		try (InputStream in = SkyblockCapture.class.getResourceAsStream("/skyblock-capture/" + name + ".json")) {
			assertNotNull(in, name);
			JsonArray array = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
			List<ItemView> items = new ArrayList<>(array.size());
			for (JsonElement e : array) {
				JsonObject o = e.getAsJsonObject();
				List<String> lore = new ArrayList<>();
				for (JsonElement l : o.getAsJsonArray("lore")) lore.add(l.getAsString());
				items.add(new ItemView(o.get("slot").getAsInt(), o.get("id").getAsString(), o.get("name").getAsString(),
						List.copyOf(lore)));
			}
			return List.copyOf(items);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
