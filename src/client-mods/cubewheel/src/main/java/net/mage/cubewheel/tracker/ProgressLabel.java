package net.mage.cubewheel.tracker;

import java.util.Collection;
import java.util.Optional;
import net.mage.cubewheel.tracker.local.ObjectiveInfo;

/**
 * How the progress popup names a counter key and shows its count, the way the Jobs and Quests panels show that
 * entry: "Mana Wolves" with "~10/74", or for one objective of a multi-objective quest its row ("Golden Knights",
 * "~6/10"). {@code done} is true once the (estimated) count reached its target; the count then carries no "✓?",
 * the popup marks it instead. {@code current} and {@code target} are the count's numbers (-1 when there are none),
 * for the popup's pie and milestones. Pure: no Minecraft/Fabric imports.
 */
public record ProgressLabel(String name, String count, boolean done, long current, long target) {
	/** A label without numbers. */
	public ProgressLabel(String name, String count, boolean done) {
		this(name, count, done, -1, -1);
	}

	/** The label of counter {@code key} (an entry id or a {@link TrackerStore#subKey}); empty if it is unknown. */
	public static Optional<ProgressLabel> of(TrackerStore store, String key, Collection<String> worldNames) {
		if (store == null || key == null) return Optional.empty();
		String id = TrackerStore.baseId(key);
		int sub = TrackerStore.subIndex(key);
		if (sub >= 0) return objective(store, id, sub, store.counted(key), worldNames);
		return store.row(id).map(r -> {
			Trackable t = r.item();
			String title = EntryLabel.of(t.name(), store.objective(id).orElse(null)).title();
			long target = r.shownMax() > 0 ? Math.round(r.shownMax()) : -1;
			long current = target > 0 ? Math.round(Math.max(0, r.shownCurrent())) : -1;
			return new ProgressLabel(TrackerPanelModel.title(t.source(), title, worldNames), CompactJob.amount(r),
					r.complete() || r.atCap(), current, target);
		});
	}

	/** Objective {@code index} of entry {@code id}, as its "↳" row on the Quests panel. */
	private static Optional<ProgressLabel> objective(TrackerStore store, String id, int index, long counted,
			Collection<String> worldNames) {
		ObjectiveInfo info = store.objective(id).orElse(null);
		if (info == null || info.subs() == null || index >= info.subs().size()) return Optional.empty();
		ObjectiveInfo.Sub sub = info.subs().get(index);
		long[] amounts = TrackerPanelModel.detailAmounts(sub, counted);
		String name = CompactJob.cut(TrackerPanelModel.detailName(sub, worldNames), TrackerPanelModel.MAX_DETAIL);
		return Optional.of(new ProgressLabel(name, TrackerPanelModel.detailRight(amounts, counted),
				amounts != null && amounts[0] >= amounts[1], amounts == null ? -1 : amounts[0], amounts == null ? -1 : amounts[1]));
	}
}
