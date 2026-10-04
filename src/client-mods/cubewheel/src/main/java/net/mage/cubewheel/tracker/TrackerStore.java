package net.mage.cubewheel.tracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.mage.cubewheel.io.BackgroundSaver;
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
import java.util.Collections;
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
	private final BackgroundSaver saver;
	private Map<String, Trackable> items = new LinkedHashMap<>();
	private Set<String> pins = new LinkedHashSet<>();
	/** Not persisted: pending "completed" notices, drained by the adapter. */
	private final List<String> completions = new ArrayList<>();
	private Set<String> hidden = new LinkedHashSet<>();
	private Map<String, ObjectiveInfo> objectives = new LinkedHashMap<>();
	private Map<String, Estimate> estimates = new LinkedHashMap<>();
	private final Map<String, Accuracy> accuracy = new HashMap<>();
	/** Not persisted: when each entry last made progress this session (local count or a higher server value). */
	private final Map<String, Long> progressAt = new HashMap<>();
	private BiConsumer<String, Accuracy> snapBackListener;
	private BiConsumer<String, Long> progressListener;

	// Rule caches, rebuilt after any change to items or objectives.
	private boolean rulesDirty = true;
	private List<String> activeTokens;
	private Map<String, CounterRule> active = Map.of();
	private final Map<String, Optional<CounterRule>> displayRules = new HashMap<>();
	/**
	 * {@link WorldScope#of} per entry id, for the HUD panels that ask every frame: reused while the entry's name, its
	 * stored objective (by identity: a new read stores a new one) and the world names are the same.
	 */
	private record ScopeEntry(String name, ObjectiveInfo info, WorldScope.Scope scope) {}

	private final Map<String, ScopeEntry> scopes = new HashMap<>();
	/** The world names {@link #scopes} were worked out for. */
	private List<String> scopeWorlds = List.of();

	public TrackerStore(Path file) {
		this.file = file;
		this.saver = new BackgroundSaver(file, "tracker");
	}

	/** Called with (id, accuracy) whenever an authoritative read replaces an estimate. */
	public void setSnapBackListener(BiConsumer<String, Accuracy> listener) {
		this.snapBackListener = listener;
	}

	/**
	 * Called with (counter key, units) after every local count ({@link #addEstimate}, units &gt; 0) and every take-back
	 * ({@link #reverseEstimate}, units &lt; 0), the store already updated; never for menu or sidebar reads. This is the
	 * one place all local counting passes through (blocks, kills, fish, shears, milk, quests completed in chat).
	 */
	public void setProgressListener(BiConsumer<String, Long> listener) {
		this.progressListener = listener;
	}

	private void progressed(String key, long units) {
		if (progressListener == null) return;
		try {
			progressListener.accept(key, units);
		} catch (RuntimeException e) {
			LOG.warn("[cubewheel] progress listener failed: {}", e.toString());
		}
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
		if (old != null && p.current() > old.current()) progressAt.put(id, now);
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
			if (current > t.current()) progressAt.put(t.id(), now);
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
		// A menu read is the truth for its objectives' percents: counts made since are dropped even if unchanged.
		boolean dropped = estimates.keySet().removeIf(k -> !k.equals(id) && id.equals(baseId(k)));
		if (Objects.equals(objectives.get(id), info)) return dropped;
		objectives.put(id, info);
		rulesDirty = true;
		return true;
	}

	public Optional<ObjectiveInfo> objective(String id) {
		return Optional.ofNullable(objectives.get(id));
	}

	/**
	 * {@link WorldScope#of}({@code t}'s name, its stored objective, {@code worldNames}), cached per entry until its name
	 * or objective or the world names change.
	 */
	public WorldScope.Scope scope(Trackable t, Collection<String> worldNames) {
		if (!scopeWorlds.equals(worldNames == null ? List.of() : worldNames)) {
			scopes.clear();
			scopeWorlds = worldNames == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(worldNames));
		}
		ObjectiveInfo info = objectives.get(t.id());
		ScopeEntry e = scopes.get(t.id());
		if (e != null && e.info() == info && Objects.equals(e.name(), t.name())) return e.scope();
		WorldScope.Scope s = WorldScope.of(t.name(), info, worldNames);
		scopes.put(t.id(), new ScopeEntry(t.name(), info, s));
		return s;
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
			ObjectiveInfo info = objectiveOf(t);
			if (info == null) continue;
			if (info.subs().size() > 1 && !info.handIn()) {
				// A quest with several objectives ("Slay 2,500 Tangleroot Monsters" + "Slay 10 Golden Knights"):
				// each unfinished one counts on its own, under subKey(id, i).
				for (int i = 0; i < info.subs().size(); i++) {
					ObjectiveInfo.Sub sub = info.subs().get(i);
					if (sub.percent() != null && sub.percent() >= 100) continue;
					String key = subKey(t.id(), i);
					ObjectiveParser.parse(new ObjectiveInfo(List.of(sub), false, false), tokens).ifPresent(r -> out.put(key, r));
				}
				continue;
			}
			ObjectiveParser.parse(info, tokens).ifPresent(r -> out.put(t.id(), r));
		}
		active = Collections.unmodifiableMap(out); // entry order: credits and the popup are the same every run
		activeTokens = tokens;
		return active;
	}

	/** The counter key of objective {@code index} of a multi-objective entry. */
	public static String subKey(String id, int index) {
		return id + SUB + index;
	}

	/** The objective index of a {@link #subKey} ("…#sub1" -> 1); -1 for an entry id. */
	static int subIndex(String key) {
		int i = key == null ? -1 : key.lastIndexOf(SUB);
		if (i < 0) return -1;
		try {
			return Integer.parseInt(key.substring(i + SUB.length()));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** The entry a counter key belongs to ("pquests:King of the Jungle#sub0" -> "pquests:King of the Jungle"). */
	static String baseId(String key) {
		int i = key == null ? -1 : key.lastIndexOf(SUB);
		return i < 0 ? key : key.substring(0, i);
	}

	private static final String SUB = "#sub";

	/** Objective units counted locally for {@code key} (an entry id or a {@link #subKey}) since its last read; 0 if none. */
	public long counted(String key) {
		Estimate e = estimates.get(key);
		return e == null ? 0 : e.count();
	}

	/** The rule's target for display and unit conversion (independent of world tokens). */
	private CounterRule displayRule(String id) {
		if (rulesDirty) {
			displayRules.clear();
			activeTokens = null;
			rulesDirty = false;
		}
		return displayRules.computeIfAbsent(id, k -> ObjectiveParser.parse(objectiveOf(items.get(k)), List.of())).orElse(null);
	}

	/**
	 * The stored objective of {@code t}, else, for a prestige rank, the one its name carries ("Rank [✪8] ·
	 * Catch 1,000 Fish", see {@link ContainerScanner#displayName}). Entries read before objectives were
	 * stored, and kept fresh since only by sidebar values ({@link #applyLiveValue}), have none on file
	 * until /prestige is opened again; the name holds the same OBJECTIVE line, minus its special-worlds
	 * note (the next menu read stores the full objective).
	 */
	private ObjectiveInfo objectiveOf(Trackable t) {
		if (t == null) return null;
		ObjectiveInfo stored = objectives.get(t.id());
		if (stored != null) return stored;
		return "prestige".equals(t.source()) ? objectiveFromName(t.name()) : null;
	}

	/** "Rank [✪8] · Catch 1,000 Fish" -> objective "Catch 1,000 Fish"; null without a " · " part. */
	static ObjectiveInfo objectiveFromName(String name) {
		if (name == null) return null;
		int dot = name.indexOf(" · ");
		if (dot < 0) return null;
		String objective = name.substring(dot + 3).trim();
		return objective.isEmpty() ? null : new ObjectiveInfo(List.of(new ObjectiveInfo.Sub(objective, null)), false, false);
	}

	/**
	 * Adds {@code units} objective units to the estimate of {@code id}, starting one (baseline = current)
	 * if needed. No-op (false) for unknown or complete items and non-positive units.
	 */
	public boolean addEstimate(String id, long units, long now) {
		String base = baseId(id);
		Trackable t = items.get(base);
		if (t == null || t.complete() || units <= 0) return false;
		Estimate old = estimates.get(id);
		progressAt.put(base, now);
		estimates.put(id, old == null
				? new Estimate(units, t.current(), now, now)
				: new Estimate(old.count() + units, old.baseline(), old.since(), now));
		progressed(id, units);
		return true;
	}

	/** Takes back {@code units} (e.g. the server rejected a break); floors at 0, which removes the estimate. */
	public boolean reverseEstimate(String id, long units) {
		Estimate old = estimates.get(id);
		if (old == null || units <= 0) return false;
		long left = old.count() - units;
		if (left <= 0) estimates.remove(id);
		else estimates.put(id, new Estimate(left, old.baseline(), old.since(), old.lastAt()));
		progressed(id, -Math.min(units, old.count()));
		return true;
	}

	/** How recently {@code id} made progress this session (see {@link Activity}). */
	public Activity activity(String id, long now) {
		Long at = progressAt.get(id);
		return at == null ? Activity.NONE : Activity.of(at, now);
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

	/** Entry {@code id} as displayed, its local estimate included; empty if unknown. */
	public Optional<TrackerRow> row(String id) {
		Trackable t = id == null ? null : items.get(id);
		return t == null ? Optional.empty() : Optional.of(row(t, true));
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
		return hudSections(maxLines, withEstimates, at, worldNames, mode, null);
	}

	/**
	 * As {@link #hudSections(int, boolean, WorldInfo, Collection, WorldScope.Mode)} without the items {@code skip}
	 * accepts (pinned or not), e.g. job entries while the Jobs panel shows them; they take no line of the cap.
	 */
	public List<HudSection> hudSections(int maxLines, boolean withEstimates, WorldInfo at, Collection<String> worldNames,
			WorldScope.Mode mode, Predicate<Trackable> skip) {
		boolean byWorld = mode != null && mode != WorldScope.Mode.OFF && at != null && at.known();
		Map<HudSection.Kind, List<TrackerRow>> groups = new java.util.EnumMap<>(HudSection.Kind.class);
		for (HudSection.Kind k : HudSection.Kind.values()) groups.put(k, new ArrayList<>());
		for (Trackable t : items.values()) {
			if (hidden.contains(t.id()) || (skip != null && skip.test(t))) continue;
			TrackerRow r = row(t, withEstimates);
			if (r.complete()) continue; // finished work never takes HUD space, pinned or not
			if (pins.contains(t.id())) {
				groups.get(HudSection.Kind.PINNED).add(r);
				continue;
			}
			HudSection.Kind kind = HudSection.Kind.ANYWHERE;
			if (byWorld) {
				WorldScope.Relevance rel = WorldScope.relevance(scope(t, worldNames), at);
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
		estimates.keySet().removeIf(k -> !items.containsKey(baseId(k)));
		hidden.retainAll(items.keySet());
		scopes.keySet().retainAll(items.keySet());
		rulesDirty = true;
		return before - items.size();
	}

	/** Removes unpinned items last seen more than {@code ageMs} ago (with their objective and estimate). */
	public int forgetOlderThan(long now, long ageMs) {
		int before = items.size();
		items.values().removeIf(t -> !pins.contains(t.id()) && now - t.seenAt() > ageMs);
		objectives.keySet().retainAll(items.keySet());
		estimates.keySet().removeIf(k -> !items.containsKey(baseId(k)));
		hidden.retainAll(items.keySet());
		scopes.keySet().retainAll(items.keySet());
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
					Trackable t = id == null ? null : newItems.get(baseId(id));
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
		scopes.clear();
		rulesDirty = true;
	}

	/**
	 * Saves now, on this thread. Best effort (the tracker is re-scannable): returns false and logs a warning if the file
	 * could not be written. The file is replaced whole (see {@link BackgroundSaver}), never left half written.
	 */
	public boolean save() {
		return saver.saveNow(GSON.toJson(snapshot()));
	}

	/**
	 * Saves on CubeWheel's background thread, so a frame never waits for the disk: the store is copied here, turned
	 * into JSON and written there. Saves made while one waits are merged into one. Use {@link #save} or
	 * {@link #flushSaves} when the game is quitting.
	 */
	public void saveInBackground() {
		Snapshot snap = snapshot();
		saver.saveLater(() -> GSON.toJson(snap));
	}

	/** Writes a background save still waiting, now on this thread (disconnect, quit). */
	public void flushSaves() {
		saver.flush();
	}

	/**
	 * A copy of what is persisted. The collections are new; their elements (records) are immutable, so the copy may be
	 * serialized on another thread while the store changes.
	 */
	private Snapshot snapshot() {
		Snapshot snap = new Snapshot();
		snap.items = new ArrayList<>(items.values());
		snap.pins = new ArrayList<>(pins);
		snap.hidden = new ArrayList<>(hidden);
		snap.objectives = new LinkedHashMap<>(objectives);
		snap.estimates = new LinkedHashMap<>(estimates);
		return snap;
	}
}
