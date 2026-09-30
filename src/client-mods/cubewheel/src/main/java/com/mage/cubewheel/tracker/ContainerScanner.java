package com.mage.cubewheel.tracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Turns the items of a server GUI into tracker updates. Pure: no Minecraft/Fabric imports. */
public final class ContainerScanner {
	/** One non-empty container slot: registry id, hover name and lore lines as plain strings. */
	public record ItemView(int slot, String id, String name, List<String> lore) {}

	private ContainerScanner() {}

	/**
	 * Updates {@code store} with every named item whose lore holds progress; returns how many entries
	 * actually changed (see {@link TrackerStore#update}), so 0 means there is nothing new to save.
	 */
	public static int scan(String source, List<ItemView> items, TrackerStore store, long now) {
		if (source == null || items == null || store == null) return 0;
		int updated = 0;
		for (ItemView item : items) {
			if (item == null || item.lore() == null) continue;
			String name = strip(item.name()).trim();
			if (name.isEmpty()) continue;
			List<String> lore = new ArrayList<>(item.lore().size());
			for (String line : item.lore()) lore.add(strip(line));
			Optional<ProgressExtractor.Progress> p = ProgressExtractor.extract(lore);
			if (p.isEmpty()) continue;
			if (store.update(source, name, p.get(), now)) updated++;
		}
		return updated;
	}

	/** Removes legacy {@code §x} formatting codes. */
	static String strip(String s) {
		return s == null ? "" : s.replaceAll("§.", "");
	}
}
