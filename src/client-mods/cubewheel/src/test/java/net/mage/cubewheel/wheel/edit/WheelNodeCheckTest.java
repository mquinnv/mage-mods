package net.mage.cubewheel.wheel.edit;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;
import net.mage.cubewheel.config.ConfigNormalizer;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.config.DefaultConfig;
import net.mage.cubewheel.config.WheelNode;
import org.junit.jupiter.api.Test;

class WheelNodeCheckTest {
	private static final Gson GSON = new Gson();
	private static final java.lang.reflect.Type LIST = new TypeToken<List<WheelNode>>() {}.getType();

	private static WheelNode copy(WheelNode n) { return GSON.fromJson(GSON.toJson(n), WheelNode.class); }

	/** Runs {@code wheel} through the real normaliser on a copy; true when it changed (dropped or rewrote anything). */
	private static boolean savingChanges(List<WheelNode> wheel) {
		String before = GSON.toJson(wheel, LIST);
		CubeWheelConfig c = DefaultConfig.create();
		c.wheel = GSON.fromJson(before, LIST);
		ConfigNormalizer.normalize(c, new ArrayList<>());
		return !before.equals(GSON.toJson(c.wheel, LIST));
	}

	/** Whether saving changes {@code node} sitting at the top level, or as the only entry of a command leaf's arc. */
	private static boolean savingChanges(WheelNode node, boolean inArc) {
		List<WheelNode> w = new ArrayList<>();
		if (inArc) w.add(WheelNode.leaf("Host", null, "/host"));
		if (inArc) w.get(0).arc = new ArrayList<>(List.of(copy(node)));
		else w.add(copy(node));
		return savingChanges(w);
	}

	private static WheelNode with(WheelNode n, java.util.function.Consumer<WheelNode> f) {
		f.accept(n);
		return n;
	}

	private static List<WheelNode> kids(WheelNode... n) { return new ArrayList<>(List.of(n)); }

	private static WheelNode good() { return WheelNode.leaf("Jobs", "minecraft:stone", "/jobs"); }

	private List<WheelNode> table() {
		List<WheelNode> t = new ArrayList<>();
		// Good ones.
		t.add(good());
		t.add(WheelNode.leaf("Settings", null, "cubewheel:settings"));
		t.add(WheelNode.ring("Ring", null, good()));
		t.add(WheelNode.ring("Empty ring", null));
		t.add(WheelNode.dynamic("Homes", null, "homes"));
		t.add(WheelNode.dynamic("Vaults", null, "vaults", good()));
		t.add(WheelNode.slice("Boss", null, "boss"));
		t.add(with(good(), n -> n.arc = kids(WheelNode.leaf("A", null, "/a"))));
		t.add(with(WheelNode.ring("Ring", null, good()), n -> n.asArc = true));
		t.add(with(good(), n -> n.outer = WheelNode.leaf("Out", null, "/out")));
		// Dropped.
		t.add(WheelNode.leaf(null, null, "/x"));
		t.add(WheelNode.leaf("  ", null, "/x"));
		t.add(WheelNode.leaf("L", null, null));
		t.add(WheelNode.leaf("L", null, "   "));
		t.add(with(WheelNode.ring("R", null, good()), n -> n.command = "/x"));
		t.add(with(WheelNode.dynamic("H", null, "homes"), n -> n.command = "/x"));
		t.add(with(WheelNode.slice("B", null, "tpa"), n -> n.command = "/x"));
		t.add(WheelNode.slice("B", null, "nope"));
		t.add(WheelNode.dynamic("U", null, "unknown"));
		// Rewritten.
		t.add(WheelNode.leaf("L", null, "jobs"));
		t.add(WheelNode.leaf("L", null, "  /jobs "));
		t.add(WheelNode.leaf("L", null, " cubewheel:settings"));
		t.add(with(WheelNode.ring("R", null, good()), n -> n.command = "  "));
		t.add(with(WheelNode.slice("B", null, "boss"), n -> n.children = kids(good())));
		t.add(with(WheelNode.slice("B", null, "boss"), n -> n.command = " "));
		t.add(with(WheelNode.dynamic("H", null, "homes"), n -> n.children = null));
		t.add(with(good(), n -> n.arc = new ArrayList<>()));
		t.add(with(good(), n -> n.outer = WheelNode.leaf("", null, "/out")));
		t.add(with(good(), n -> n.outer = with(WheelNode.leaf("O1", null, "/1"), o1 ->
			o1.outer = with(WheelNode.leaf("O2", null, "/2"), o2 ->
				o2.outer = with(WheelNode.leaf("O3", null, "/3"), o3 ->
					o3.outer = with(WheelNode.leaf("O4", null, "/4"), o4 ->
						o4.outer = WheelNode.leaf("O5", null, "/5")))))));
		t.add(with(WheelNode.ring("R", null), n -> n.children.add(null)));
		t.add(with(good(), n -> { n.arc = new ArrayList<>(); n.arc.add(null); }));
		return t;
	}

	@Test void problemAgreesWithTheNormaliserOnATopLevelNode() {
		for (WheelNode n : table()) {
			boolean changes = savingChanges(n, false);
			String problem = WheelNodeCheck.problem(n, false);
			assertEquals(changes, problem != null, GSON.toJson(n) + " -> " + problem);
		}
	}

	@Test void problemAgreesWithTheNormaliserOnAnArcEntry() {
		for (WheelNode n : table()) {
			boolean changes = savingChanges(n, true);
			String problem = WheelNodeCheck.problem(n, true);
			assertEquals(changes, problem != null, GSON.toJson(n) + " -> " + problem);
		}
		// Things that are fine at the top level but not in an arc.
		assertNotNull(WheelNodeCheck.problem(WheelNode.ring("R", null, good()), true));
		assertNotNull(WheelNodeCheck.problem(WheelNode.slice("B", null, "boss"), true));
		assertNotNull(WheelNodeCheck.problem(with(good(), n -> n.arc = kids(good())), true));
		assertNull(WheelNodeCheck.problem(good(), true));
	}

	@Test void messagesSayWhatSavingDoes() {
		assertEquals("No label: this entry would be dropped when saved.", WheelNodeCheck.problem(WheelNode.leaf("", null, "/x"), false));
		assertEquals("The command would be saved as \"/jobs\".", WheelNodeCheck.problem(WheelNode.leaf("L", null, "jobs"), false));
	}

	@Test void aWholeTreeChangesWhenSavedExactlyWhenSomeRowHasAProblem() {
		assertFalse(savingChanges(DefaultConfig.wheel()));
		for (Row row : WheelEdits.rows(DefaultConfig.wheel())) {
			assertNull(WheelNodeCheck.problem(row.node(), row.kind() == Row.Kind.ARC_ENTRY), row.path().toString());
		}
		for (WheelNode bad : table()) {
			List<WheelNode> w = DefaultConfig.wheel();
			w.add(copy(bad));
			boolean anyProblem = WheelEdits.rows(w).stream()
				.anyMatch(r -> WheelNodeCheck.problem(r.node(), r.kind() == Row.Kind.ARC_ENTRY) != null);
			assertEquals(savingChanges(w), anyProblem, GSON.toJson(bad));
		}
	}

	@Test void normalizedCommandMatchesWhatSavingWrites() {
		assertEquals("/warp x", WheelNodeCheck.normalizedCommand(" warp x "));
		assertEquals("/warp x", WheelNodeCheck.normalizedCommand("/warp x"));
		assertEquals("cubewheel:settings", WheelNodeCheck.normalizedCommand("cubewheel:settings"));
		assertNull(WheelNodeCheck.normalizedCommand("  "));
		assertNull(WheelNodeCheck.normalizedCommand(null));
	}
}
