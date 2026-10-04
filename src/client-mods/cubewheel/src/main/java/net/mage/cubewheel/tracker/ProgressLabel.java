package net.mage.cubewheel.tracker;

import java.util.Collection;
import java.util.Optional;
import net.mage.cubewheel.tracker.local.ObjectiveInfo;

/**
 * How the progress popup names a counter key and shows its count, the way the Jobs and Tracker panels show that
 * entry: "Mana Wolves" with "~10/74", or for one objective of a multi-objective quest its row ("Golden Knights",
 * "~6/10"). {@code done} is true once the (estimated) count reached its target; the count then carries no "✓?",
 * the popup marks it instead. Pure: no Minecraft/Fabric imports.
 */
public record ProgressLabel(String name, String count, boolean done) {
	/** The label of counter {@code key} (an entry id or a {@link TrackerStore#subKey}); empty if it is unknown. */
	public static Optional<ProgressLabel> of(TrackerStore store, String key, Collection<String> worldNames) {
		if (store == null || key == null) return Optional.empty();
		String id = TrackerStore.baseId(key);
		int sub = TrackerStore.subIndex(key);
		if (sub >= 0) return objective(store, id, sub, store.counted(key), worldNames);
		return store.row(id).map(r -> {
			Trackable t = r.item();
			String title = EntryLabel.of(t.name(), store.objective(id).orElse(null)).title();
			return new ProgressLabel(TrackerPanelModel.title(t.source(), title, worldNames), CompactJob.amount(r),
					r.complete() || r.atCap());
		});
	}

	/** Objective {@code index} of entry {@code id}, as its "↳" row on the Tracker panel. */
	private static Optional<ProgressLabel> objective(TrackerStore store, String id, int index, long counted,
			Collection<String> worldNames) {
		ObjectiveInfo info = store.objective(id).orElse(null);
		if (info == null || info.subs() == null || index >= info.subs().size()) return Optional.empty();
		ObjectiveInfo.Sub sub = info.subs().get(index);
		long[] amounts = TrackerPanelModel.detailAmounts(sub, counted);
		String name = CompactJob.cut(TrackerPanelModel.detailName(sub, worldNames), TrackerPanelModel.MAX_DETAIL);
		return Optional.of(new ProgressLabel(name, TrackerPanelModel.detailRight(amounts, counted),
				amounts != null && amounts[0] >= amounts[1]));
	}
}
