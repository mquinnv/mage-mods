package com.mage.cubewheel.tracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import com.mage.cubewheel.tracker.local.ObjectiveExtractor;

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
		return scan(source, items, store, now, null);
	}

	/**
	 * Like {@link #scan(String, List, TrackerStore, long)}; {@code seen} (optional) receives the id of every
	 * entry this menu showed, changed or not (a refresh run counts what it confirmed).
	 */
	public static int scan(String source, List<ItemView> items, TrackerStore store, long now, Consumer<String> seen) {
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
			ProgressExtractor.Progress progress = p.get();
			// "COMPLETED" wins over a lagging number (a quest can read 99% and be done).
			if (isMarkedComplete(lore)) progress = new ProgressExtractor.Progress(progress.max(), progress.max());
			String display = displayName(name, lore);
			boolean changed = store.update(source, display, progress, now);
			// The objective text feeds local counting (tracker.local); a read is authoritative, so any
			// estimate for this entry was just dropped by update().
			changed |= store.setObjective(Trackable.idOf(source, display), ObjectiveExtractor.extract(name, lore));
			if (seen != null) seen.accept(Trackable.idOf(source, display));
			if (changed) updated++;
		}
		return updated;
	}

	/** True when a lore line is exactly a completion marker such as "COMPLETED" or "QUEST COMPLETED". */
	static boolean isMarkedComplete(List<String> lore) {
		for (String line : lore) {
			String t = line.trim().toUpperCase(Locale.ROOT);
			if (t.equals("COMPLETED") || t.equals("QUEST COMPLETED")) return true;
		}
		return false;
	}

	/**
	 * Items whose lore has an "OBJECTIVE" heading (prestige ranks: "Rank [✪4]") are named after the
	 * objective too, without its bracketed counter: "Rank [✪4] · Reach 2,500 Skill Level".
	 */
	static String displayName(String name, List<String> lore) {
		for (int i = 0; i < lore.size() - 1; i++) {
			if (!lore.get(i).trim().equalsIgnoreCase("OBJECTIVE")) continue;
			String objective = lore.get(i + 1).replaceAll("\\s*\\[[^\\]]*]\\s*$", "").trim();
			if (!objective.isEmpty()) return name + " · " + objective;
		}
		return name;
	}

	/** Removes legacy {@code §x} formatting codes. */
	static String strip(String s) {
		return s == null ? "" : s.replaceAll("§.", "");
	}
}
