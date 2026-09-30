package net.mage.cubewheel.tracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.mage.cubewheel.tracker.local.Accuracy;
import net.mage.cubewheel.tracker.local.CounterRule;
import net.mage.cubewheel.tracker.local.Estimate;
import net.mage.cubewheel.tracker.local.EstimateView;
import net.mage.cubewheel.tracker.local.ObjectiveInfo;
import net.mage.cubewheel.tracker.local.ObjectiveParser;
import net.mage.cubewheel.tracker.local.WorldInfo;
import net.mage.cubewheel.tracker.local.WorldScope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracked progress items, pins and HUD-hidden ids, persisted as JSON, plus two side maps keyed by item id: the
 * objective text of each item and the local estimate counted since its last authoritative read.
 * Every authoritative read ({@link #update}, {@link #applyLiveValue}) drops that item's estimate.
 * Pure: no Minecraft/Fabric imports.
 */
public final class TrackerStore {
	static final class Snapshot {
		List<Trackable> items = new ArrayList<>();
		List<String> pins = new ArrayList<>();
		/** Entries never shown on the HUD (still listed in the picker). */
		List<String> hidden = new ArrayList<>();
		Map<String, ObjectiveInfo> objectives = new LinkedHashMap<>();
		Map<String, Estimate> estimates = new LinkedHashMap<>();
	}

	/** Minimum last-seen advance that counts as a change on its own (TrackerFormat.age shows "now" below this). */
	public static final long SEEN_REFRESH_MS = 60_000;

	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Comparator<Trackable> BY_FRACTION_THEN_NAME =
		Comparator.comparingDouble(Trackable::fraction).reversed().thenComparing(Trackable::name);
	private static final Comparator<TrackerRow> ROWS_BY_FRACTION_THEN_NAME =
		Comparator.comparingDouble(TrackerRow::fraction).reversed().thenComparing(r -> r.item().name());

	private final Path file;
	private Map<String, Trackable> items = new LinkedHashMap<>();
	private Set<String> pins = new LinkedHashSet<>();
	/** Not persisted: pending "completed" notices, drained by the adapter. */
	private final List<String> completions = new ArrayList<>();
	private Set<String> hidden = new LinkedHashSet<>();
	private Map<String, ObjectiveInfo> objectives = new LinkedHashMap<>();
	private Map<String, Estimate> estimates = new LinkedHashMap<>();
	private final Map<String, Accuracy> accuracy = new HashMap<>();
	private BiConsumer<String, Accuracy> snapBackListener;

	// Rule caches, rebuilt after any change to items or objectives.
	private boolean rulesDirty = true;
	private List<String> activeTokens;
	private Map<String, CounterRule> active = Map.of();
	private final Map<String, Optional<CounterRule>> displayRules = new HashMap<>();

	public TrackerStore(Path file) {
		this.file = file;
	}

	/** Called with (id, accuracy) whenever an authoritative read replaces an estimate. */
	public void setSnapBackListener(BiConsumer<String, Accuracy> listener) {
		this.snapBackListener = listener;
	}

	/**
	 * Records progress; returns true if something worth persisting changed: a new item, a different
	 * current/max, a last-seen time at least {@link #SEEN_REFRESH_MS} newer (the age display's
	 * granularity), or a local estimate that this authoritative read replaced. Otherwise the stored entry,
	 * including its timestamp, is left untouched.
	 */
	public boolean update(String source, String name, ProgressExtractor.Progress p, long now) {
		String id = Trackable.idOf(source, name);
		Trackable old = items.get(id);
		boolean snapped = snapBack(id, p.current());
		// A true reading that says "done" releases the pin: a finished quest shouldn't sit on the HUD.
		boolean unpinned = new Trackable(id, source, name, p.current(), p.max(), now).complete() && pins.remove(id);
		if (unpinned && (old == null || !old.complete())) completions.add(name);
		if (old != null && old.current() == p.current() && old.max() == p.max()
				&& now - old.seenAt() < SEEN_REFRESH_MS) {
			return snapped || unpinned;
		}
		items.put(id, new Trackable(id, source, name, p.current(), p.max(), now));
		rulesDirty = true;
		return true;
	}

	/** Names of pinned entries a menu read just confirmed complete (each reported once), for a chat notice. */
	public List<String> drainCompletions() {
		List<String> out = List.copyOf(completions);
		completions.clear();
		return out;
	}

	/**
	 * A live value (e.g. from the sidebar) for every incomplete item whose name matches {@code names}:
	 * current becomes max(current, value), the item counts as seen at {@code now} and any local estimate
	 * is dropped (this is a true reading). Returns how many items were touched.
	 */
	public int applyLiveValue(Pattern names, double value, long now) {
		if (names == null || !Double.isFinite(value)) return 0;
		int touched = 0;
		for (Map.Entry<String, Trackable> e : items.entrySet()) {
			Trackable t = e.getValue();
			if (t.complete() || t.name() == null || !names.matcher(t.name()).find()) continue;
			double current = Math.max(t.current(), value);
			snapBack(t.id(), current);
			Trackable updated = new Trackable(t.id(), t.source(), t.name(), current, t.max(), now);
			e.setValue(updated);
			// A true reading that reaches the target completes it, exactly like a menu read would.
			if (updated.complete() && pins.remove(t.id())) completions.add(t.name());
			touched++;
		}
		if (touched > 0) rulesDirty = true;
		return touched;
	}

	/** Drops the estimate of {@code id}, recording how it compared with {@code actualCurrent}. */
	private boolean snapBack(String id, double actualCurrent) {
		Estimate est = estimates.remove(id);
		if (est == null) return false;
		Trackable t = items.get(id);
		double perCount = EstimateView.unitsPerCount(t, displayRule(id));
		Accuracy acc = new Accuracy(est.count(), (actualCurrent - est.baseline()) / perCount);
		accuracy.put(id, acc);
		if (snapBackListener != null) {
			try {
				snapBackListener.accept(id, acc);
			} catch (RuntimeException e) {
				LOG.warn("[cubewheel] snap-back listener failed: {}", e.toString());
			}
		}
		return true;
	}

	/** Stores the objective text of {@code id}; returns true if it changed. */
	public boolean setObjective(String id, ObjectiveInfo info) {
		if (id == null || info == null) return false;
		if (Objects.equals(objectives.get(id), info)) return false;
		objectives.put(id, info);
		rulesDirty = true;
		return true;
	}

	public Optional<ObjectiveInfo> objective(String id) {
		return Optional.ofNullable(objectives.get(id));
	}

	/**
	 * Incomplete items whose objective parses to a counting rule, by id. {@code worldTokens} are the
	 * configured world names ("Wolfhaven Resources" is then scoped to Wolfhaven). Cached until the store
	 * or the tokens change.
	 */
	public Map<String, CounterRule> activeRules(Collection<String> worldTokens) {
		List<String> tokens = worldTokens == null ? List.of() : List.copyOf(worldTokens);
		if (rulesDirty) {
			displayRules.clear();
			activeTokens = null;
			rulesDirty = false;
		}
		if (tokens.equals(activeTokens)) return active;
		Map<String, CounterRule> out = new LinkedHashMap<>();
		for (Trackable t : items.values()) {
			if (t.complete()) continue;
			ObjectiveInfo info = objectives.get(t.id());
			if (info == null) continue;
			ObjectiveParser.parse(info, tokens).ifPresent(r -> out.put(t.id(), r));
		}
		active = Map.copyOf(out);
		activeTokens = tokens;
		return active;
	}

	/** The rule's target for display and unit conversion (independent of world tokens). */
	private CounterRule displayRule(String id) {
		if (rulesDirty) {
			displayRules.clear();
			activeTokens = null;
			rulesDirty = false;
		}
		return displayRules.computeIfAbsent(id, k -> ObjectiveParser.parse(objectives.get(k), List.of())).orElse(null);
	}

	/**
	 * Adds {@code units} objective units to the estimate of {@code id}, starting one (baseline = current)
	 * if needed. No-op (false) for unknown or complete items and non-positive units.
	 */
	public boolean addEstimate(String id, long units, long now) {
		Trackable t = items.get(id);
		if (t == null || t.complete() || units <= 0) return false;
		Estimate old = estimates.get(id);
		estimates.put(id, old == null
				? new Estimate(units, t.current(), now, now)
				: new Estimate(old.count() + units, old.baseline(), old.since(), now));
		return true;
	}

	/** Takes back {@code units} (e.g. the server rejected a break); floors at 0, which removes the estimate. */
	public boolean reverseEstimate(String id, long units) {
		Estimate old = estimates.get(id);
		if (old == null || units <= 0) return false;
		long left = old.count() - units;
		if (left <= 0) estimates.remove(id);
		else estimates.put(id, new Estimate(left, old.baseline(), old.since(), old.lastAt()));
		return true;
	}

	public Optional<Estimate> estimate(String id) {
		return Optional.ofNullable(estimates.get(id));
	}

	/** The last snap-back of {@code id} in this session. */
	public Optional<Accuracy> lastAccuracy(String id) {
		return Optional.ofNullable(accuracy.get(id));
	}

	/** Sorted by fraction descending, then name. */
	public List<Trackable> all() {
		List<Trackable> out = new ArrayList<>(items.values());
		out.sort(BY_FRACTION_THEN_NAME);
		return out;
	}

	/** Every item as displayed, sorted by (estimated) fraction descending, then name. */
	public List<TrackerRow> rows(boolean withEstimates) {
		List<TrackerRow> out = new ArrayList<>(items.size());
		for (Trackable t : items.values()) out.add(row(t, withEstimates));
		out.sort(ROWS_BY_FRACTION_THEN_NAME);
		return out;
	}

	private TrackerRow row(Trackable t, boolean withEstimates) {
		Estimate est = withEstimates ? estimates.get(t.id()) : null;
		return EstimateView.row(t, est, displayRule(t.id()));
	}

	public boolean isPinned(String id) {
		return pins.contains(id);
	}

	public void togglePin(String id) {
		if (!pins.remove(id)) pins.add(id);
	}

	public boolean isHidden(String id) {
		return hidden.contains(id);
	}

	/** Hides {@code id} from the HUD, or shows it again. */
	public void toggleHidden(String id) {
		if (id != null && !hidden.remove(id)) hidden.add(id);
	}

	/**
	 * Incomplete items only: pinned ones first, then the rest, each group closest to done first. Hidden and
	 * complete items never appear (pinned or not). No world ordering; capped at {@code maxLines}. Same order
	 * as {@link #hudRows(int, boolean)} without estimates.
	 */
	public List<Trackable> hudEntries(int maxLines) {
		return hudRows(maxLines, false).stream().map(TrackerRow::item).toList();
	}

	/**
	 * Like {@link #hudEntries} but as displayed rows; with {@code withEstimates} the sorting uses the
	 * estimated fraction, so an entry moves up live while you work on it.
	 */
	public List<TrackerRow> hudRows(int maxLines, boolean withEstimates) {
		return hudRows(maxLines, withEstimates, null, List.of(), WorldScope.Mode.OFF);
	}

	/**
	 * As {@link #hudRows(int, boolean)}, ordered for the world the player is in ({@code tracker.worldFilter}):
	 * after the pinned entries (kept in their usual order), unpinned entries naming the current world come
	 * first and those naming other worlds last; {@link WorldScope.Mode#HIDE} drops the latter. Entries
	 * naming no world, an unknown {@code at} and {@link WorldScope.Mode#OFF} leave the usual order.
	 * {@code worldNames} are the configured world names to look for in names and objectives.
	 */
	public List<TrackerRow> hudRows(int maxLines, boolean withEstimates, WorldInfo at, Collection<String> worldNames,
			WorldScope.Mode mode) {
		List<TrackerRow> out = new ArrayList<>();
		for (HudSection s : hudSections(maxLines, withEstimates, at, worldNames, mode)) out.addAll(s.rows());
		return out;
	}

	/** One labelled group of HUD lines; the HUD draws a divider before each group. */
	public record HudSection(Kind kind, List<TrackerRow> rows) {
		public enum Kind { PINNED, THIS_WORLD, ANYWHERE, OTHER_WORLDS }
	}

	/**
	 * The HUD rows of {@link #hudRows(int, boolean, WorldInfo, Collection, WorldScope.Mode)} split into groups
	 * in display order — pinned, this world, anywhere, other worlds — leaving out empty ones. Without a known
	 * world (or with the filter off) everything unpinned is one "anywhere" group. {@code maxLines} caps entries.
	 */
	public List<HudSection> hudSections(int maxLines, boolean withEstimates, WorldInfo at, Collection<String> worldNames,
			WorldScope.Mode mode) {
		boolean byWorld = mode != null && mode != WorldScope.Mode.OFF && at != null && at.known();
		Map<HudSection.Kind, List<TrackerRow>> groups = new java.util.EnumMap<>(HudSection.Kind.class);
		for (HudSection.Kind k : HudSection.Kind.values()) groups.put(k, new ArrayList<>());
		for (Trackable t : items.values()) {
			if (hidden.contains(t.id())) continue;
			TrackerRow r = row(t, withEstimates);
			if (r.complete()) continue; // finished work never takes HUD space, pinned or not
			if (pins.contains(t.id())) {
				groups.get(HudSection.Kind.PINNED).add(r);
				continue;
			}
			HudSection.Kind kind = HudSection.Kind.ANYWHERE;
			if (byWorld) {
				WorldScope.Relevance rel = WorldScope.relevance(WorldScope.of(t.name(), objectives.get(t.id()), worldNames), at);
				if (rel == WorldScope.Relevance.OTHER && mode == WorldScope.Mode.HIDE) continue;
				if (rel == WorldScope.Relevance.CURRENT) kind = HudSection.Kind.THIS_WORLD;
				else if (rel == WorldScope.Relevance.OTHER) kind = HudSection.Kind.OTHER_WORLDS;
			}
			groups.get(kind).add(r);
		}
		List<HudSection> out = new ArrayList<>();
		int left = Math.max(0, maxLines);
		for (HudSection.Kind k : HudSection.Kind.values()) {
			List<TrackerRow> rows = groups.get(k);
			rows.sort(ROWS_BY_FRACTION_THEN_NAME);
			if (rows.isEmpty() || left == 0) continue;
			List<TrackerRow> kept = rows.size() > left ? new ArrayList<>(rows.subList(0, left)) : rows;
			left -= kept.size();
			out.add(new HudSection(k, kept));
		}
		return out;
	}

	/** Removes unpinned items matching {@code which} (with their objective, estimate and hidden mark); returns how many. */
	public int forgetUnpinned(Predicate<Trackable> which) {
		if (which == null) return 0;
		int before = items.size();
		items.values().removeIf(t -> !pins.contains(t.id()) && which.test(t));
		if (items.size() == before) return 0;
		objectives.keySet().retainAll(items.keySet());
		estimates.keySet().retainAll(items.keySet());
		hidden.retainAll(items.keySet());
		rulesDirty = true;
		return before - items.size();
	}

	/** Removes unpinned items last seen more than {@code ageMs} ago (with their objective and estimate). */
	public int forgetOlderThan(long now, long ageMs) {
		int before = items.size();
		items.values().removeIf(t -> !pins.contains(t.id()) && now - t.seenAt() > ageMs);
		objectives.keySet().retainAll(items.keySet());
		estimates.keySet().retainAll(items.keySet());
		hidden.retainAll(items.keySet());
		rulesDirty = true;
		return before - items.size();
	}

	/** Loads the store; a missing, unreadable or corrupt file yields an empty store. */
	public void load() {
		Snapshot snap = null;
		try {
			snap = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Snapshot.class);
		} catch (IOException | RuntimeException ignored) {
			// missing or corrupt: fall through to empty
		}
		Map<String, Trackable> newItems = new LinkedHashMap<>();
		Set<String> newPins = new LinkedHashSet<>();
		Set<String> newHidden = new LinkedHashSet<>();
		Map<String, ObjectiveInfo> newObjectives = new LinkedHashMap<>();
		Map<String, Estimate> newEstimates = new LinkedHashMap<>();
		if (snap != null) {
			if (snap.items != null) {
				for (Trackable t : snap.items) {
					if (t == null || t.id() == null || t.name() == null) continue;
					newItems.put(t.id(), t);
				}
			}
			if (snap.pins != null) {
				for (String pin : snap.pins) {
					if (pin != null) newPins.add(pin);
				}
			}
			if (snap.hidden != null) {
				for (String id : snap.hidden) {
					if (id != null && newItems.containsKey(id)) newHidden.add(id);
				}
			}
			if (snap.objectives != null) {
				snap.objectives.forEach((id, info) -> {
					if (id != null && info != null && newItems.containsKey(id)) newObjectives.put(id, info);
				});
			}
			if (snap.estimates != null) {
				snap.estimates.forEach((id, est) -> {
					Trackable t = id == null ? null : newItems.get(id);
					if (est != null && est.count() > 0 && t != null && !t.complete()) newEstimates.put(id, est);
				});
			}
		}
		items = newItems;
		pins = newPins;
		hidden = newHidden;
		objectives = newObjectives;
		estimates = newEstimates;
		accuracy.clear();
		rulesDirty = true;
	}

	/** Best effort (the tracker is re-scannable): returns false and logs a warning if the file could not be written. */
	public boolean save() {
		Snapshot snap = new Snapshot();
		snap.items = new ArrayList<>(items.values());
		snap.pins = new ArrayList<>(pins);
		snap.hidden = new ArrayList<>(hidden);
		snap.objectives = new LinkedHashMap<>(objectives);
		snap.estimates = new LinkedHashMap<>(estimates);
		try {
			Path parent = file.getParent();
			if (parent != null) Files.createDirectories(parent);
			Files.writeString(file, GSON.toJson(snap), StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			LOG.warn("[cubewheel] could not save tracker to {}: {}", file, e.toString());
			return false;
		}
	}
}
