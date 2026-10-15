package net.mage.cubewheel.skills;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the mod knows about each account's mcMMO skills, from three feeds: boss bars (level, XP, XP needed),
 * level-up chat lines (level only, XP unknown) and the {@code /mcmmo} menu (levels and caps), plus the sidebar's
 * power level. Persisted as one JSON object keyed by lower-case account name, skills keyed by lower-case enum name
 * (the same per-account layout as {@code cubewheel-vaults.json}). Pure; the adapter decides when to {@link #save()}.
 * Mutate only through this class's methods; {@link Account} exposes read accessors.
 */
public final class SkillStore {
	/** {@code xp < 0} means unknown (menu or level-up only); {@code xpAt} is 0 when never read from a bar. */
	public record State(int level, long xp, long needed, long xpAt, long levelAt, int cap) {}

	/** One account's skills, power level and menu-read time. */
	public static final class Account {
		private long power;
		private long powerAt;
		private long menuAt;
		private final Map<Skill, State> skills = new EnumMap<>(Skill.class);
		private final Map<Skill, ArrayDeque<long[]>> gains = new EnumMap<>(Skill.class);

		public long power() { return power; }
		public long powerAt() { return powerAt; }
		public long menuAt() { return menuAt; }
		public Map<Skill, State> skills() { return Collections.unmodifiableMap(skills); }
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MAX_GAINS = 64;

	private final Path file;
	private Map<String, Account> accounts = new LinkedHashMap<>();
	private boolean dirty;

	public SkillStore(Path file) {
		this.file = file;
	}

	/** The account for {@code name} (case-insensitive), created on demand. */
	public Account account(String name) {
		return accounts.computeIfAbsent(key(name), k -> new Account());
	}

	/**
	 * Applies a boss-bar reading and returns the XP gained since the previous reading of that skill: the difference
	 * on the same level, the rest of the old level plus the new XP across one roll-over, else 0 (first reading,
	 * unknown previous XP, or a lower reading, which is a reset).
	 */
	public long bar(String name, SkillBarParser.Reading r, long now) {
		Account a = account(name);
		State prev = a.skills.get(r.skill());
		long gain = 0;
		if (prev != null && prev.xp() >= 0) {
			if (r.level() == prev.level() && r.xp() > prev.xp()) {
				gain = r.xp() - prev.xp();
			} else if (r.level() == prev.level() + 1) {
				gain = Math.max(0, r.xp() + (prev.needed() - prev.xp()));
			}
		}
		boolean rose = prev == null || r.level() > prev.level();
		int cap = prev != null && prev.cap() > 0 ? prev.cap() : r.skill().defaultCap;
		a.skills.put(r.skill(), new State(r.level(), r.xp(), r.needed(), now, rose ? now : prev.levelAt(), cap));
		if (gain > 0) {
			ArrayDeque<long[]> q = a.gains.computeIfAbsent(r.skill(), k -> new ArrayDeque<>());
			q.addLast(new long[] {now, gain});
			while (q.size() > MAX_GAINS) q.removeFirst();
		}
		dirty = true;
		return gain;
	}

	/** A level-up line: sets the level and {@code levelAt}, marks XP unknown; never lowers or repeats a level. */
	public void levelUp(String name, Skill s, int level, long now) {
		Account a = account(name);
		State prev = a.skills.get(s);
		if (prev != null && level <= prev.level()) return;
		int cap = prev != null && prev.cap() > 0 ? prev.cap() : s.defaultCap;
		a.skills.put(s, new State(level, -1, XpCurve.needed(level), prev == null ? 0 : prev.xpAt(), now, cap));
		dirty = true;
	}

	/** The {@code /mcmmo} menu: always sets the cap; the level only when missing or lower. */
	public void menu(String name, List<SkillsMenu.Entry> entries, long now) {
		Account a = account(name);
		for (SkillsMenu.Entry e : entries) {
			State prev = a.skills.get(e.skill());
			if (prev == null) {
				a.skills.put(e.skill(), new State(e.level(), -1, XpCurve.needed(e.level()), 0, now, e.cap()));
			} else if (e.level() > prev.level()) {
				a.skills.put(e.skill(), new State(e.level(), -1, XpCurve.needed(e.level()), prev.xpAt(), now, e.cap()));
			} else {
				a.skills.put(e.skill(), new State(prev.level(), prev.xp(), prev.needed(), prev.xpAt(), prev.levelAt(), e.cap()));
			}
		}
		a.menuAt = now;
		dirty = true;
	}

	public void power(String name, long power, long now) {
		Account a = account(name);
		a.power = power;
		a.powerAt = now;
		dirty = true;
	}

	/** XP gained per skill by bar readings at or after {@code now - windowMs}; skills with none are omitted. */
	public Map<Skill, Long> recentGains(String name, long now, long windowMs) {
		Map<Skill, Long> out = new EnumMap<>(Skill.class);
		for (Map.Entry<Skill, ArrayDeque<long[]>> e : account(name).gains.entrySet()) {
			long sum = 0;
			for (long[] g : e.getValue()) {
				if (g[0] >= now - windowMs) sum += g[1];
			}
			if (sum > 0) out.put(e.getKey(), sum);
		}
		return out;
	}

	public boolean dirty() { return dirty; }

	public void clearDirty() { dirty = false; }

	/** Reads the file. Missing: no-op, true. Unreadable or malformed: store left empty, false. */
	public boolean load() {
		if (!Files.isRegularFile(file)) return true;
		Map<String, Account> loaded = new LinkedHashMap<>();
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : root.entrySet()) {
				loaded.put(key(e.getKey()), read(e.getValue().getAsJsonObject()));
			}
		} catch (IOException | RuntimeException ex) {
			accounts = new LinkedHashMap<>();
			return false;
		}
		accounts = loaded;
		dirty = false;
		return true;
	}

	/** Writes the file atomically (temp file in the same directory, then move), synchronously. */
	public void save() throws IOException {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, Account> e : accounts.entrySet()) {
			root.add(e.getKey(), write(e.getValue()));
		}
		Path dir = file.toAbsolutePath().getParent();
		Files.createDirectories(dir);
		Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
		try {
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	private static Account read(JsonObject o) {
		Account a = new Account();
		a.power = num(o, "power");
		a.powerAt = num(o, "powerAt");
		a.menuAt = num(o, "menuAt");
		Map<String, Skill> byKey = new HashMap<>();
		for (Skill s : Skill.values()) byKey.put(s.name().toLowerCase(Locale.ROOT), s);
		JsonElement skills = o.get("skills");
		if (skills != null && skills.isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : skills.getAsJsonObject().entrySet()) {
				Skill s = byKey.get(e.getKey());
				if (s == null || !e.getValue().isJsonObject()) continue;
				JsonObject j = e.getValue().getAsJsonObject();
				a.skills.put(s, new State((int) num(j, "level"), num(j, "xp"), num(j, "needed"),
						num(j, "xpAt"), num(j, "levelAt"), (int) num(j, "cap")));
			}
		}
		return a;
	}

	private static JsonObject write(Account a) {
		JsonObject o = new JsonObject();
		o.addProperty("power", a.power);
		o.addProperty("powerAt", a.powerAt);
		o.addProperty("menuAt", a.menuAt);
		JsonObject skills = new JsonObject();
		for (Map.Entry<Skill, State> e : a.skills.entrySet()) {
			State s = e.getValue();
			JsonObject j = new JsonObject();
			j.addProperty("level", s.level());
			j.addProperty("xp", s.xp());
			j.addProperty("needed", s.needed());
			j.addProperty("xpAt", s.xpAt());
			j.addProperty("levelAt", s.levelAt());
			j.addProperty("cap", s.cap());
			skills.add(e.getKey().name().toLowerCase(Locale.ROOT), j);
		}
		o.add("skills", skills);
		return o;
	}

	private static long num(JsonObject o, String field) {
		JsonElement e = o.get(field);
		return e != null && e.isJsonPrimitive() ? e.getAsLong() : 0;
	}
}
