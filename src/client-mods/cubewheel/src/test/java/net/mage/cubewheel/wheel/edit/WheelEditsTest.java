package net.mage.cubewheel.wheel.edit;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import net.mage.cubewheel.config.ConfigNormalizer;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.config.DefaultConfig;
import net.mage.cubewheel.config.WheelNode;
import net.mage.cubewheel.wheel.edit.Row.Kind;
import net.mage.cubewheel.wheel.edit.WheelEdits.Result;
import org.junit.jupiter.api.Test;

class WheelEditsTest {
	private static final Gson GSON = new Gson();

	// Top level of DefaultConfig.wheel(): 0 Sushi (arc), 1 Homes (live ring), 2 Teleporter (arc), 3 Jobs, 4 Shop (arc),
	// 5 Sell (arc), 6 Vaults (live ring + extras), 7 Fly, 8 Isles (arc), 9 Party quests, 10 Daily reward,
	// 11 Boss (live slice), 12 TPA (live slice), 13 More (ring).
	private static final Path SUSHI = Path.top(0);
	private static final Path HOMES = Path.top(1);
	private static final Path JOBS = Path.top(3);
	private static final Path VAULTS = Path.top(6);
	private static final Path BOSS = Path.top(11);
	private static final Path MORE = Path.top(13);

	private static List<WheelNode> wheel() { return DefaultConfig.wheel(); }

	private static String dump(List<WheelNode> w) { return GSON.toJson(w); }

	private static WheelNode node(List<WheelNode> w, Path p) {
		WheelNode n = null;
		for (Path.Step s : p.steps()) {
			List<WheelNode> list = n == null ? w : s.arc() ? n.arc : n.children;
			n = list.get(s.index());
		}
		return n;
	}

	/** The wheel is what saving would keep: running it through the real normaliser changes nothing. */
	private static void assertSaveStable(List<WheelNode> w) {
		CubeWheelConfig c = DefaultConfig.create();
		c.wheel = GSON.fromJson(dump(w), new com.google.gson.reflect.TypeToken<List<WheelNode>>() {}.getType());
		ConfigNormalizer.normalize(c, new ArrayList<>());
		assertEquals(dump(w), dump(c.wheel));
	}

	private static List<WheelNode> ok(Result r) {
		assertNull(r.error(), r.error());
		return r.wheel();
	}

	@Test void rowsAreDepthFirstWithArcEntriesBeforeChildren() {
		List<Row> rows = WheelEdits.rows(wheel());
		assertEquals(SUSHI, rows.get(0).path());
		assertEquals(Kind.LEAF, rows.get(0).kind());
		assertEquals(0, rows.get(0).depth());
		assertEquals(SUSHI.arcEntry(0), rows.get(1).path());
		assertEquals(Kind.ARC_ENTRY, rows.get(1).kind());
		assertEquals(1, rows.get(1).depth());
		assertEquals("Spawners", rows.get(1).node().label);
		assertEquals(HOMES, rows.get(2).path());
		assertEquals(Kind.LIVE_RING, rows.get(2).kind());
	}

	@Test void aLiveRingListsItsExtrasAndARingItsChildren() {
		List<Row> rows = WheelEdits.rows(wheel());
		Row party = rows.stream().filter(r -> r.path().equals(VAULTS.child(0))).findFirst().orElseThrow();
		assertEquals("Party vault", party.node().label);
		assertEquals(Kind.LEAF, party.kind());
		assertEquals(1, party.depth());
		Row shops = rows.stream().filter(r -> r.path().equals(MORE.child(0))).findFirst().orElseThrow();
		assertEquals(Kind.RING, shops.kind());
		Row deep = rows.stream().filter(r -> r.path().equals(MORE.child(1).child(0).child(0))).findFirst().orElseThrow();
		assertEquals("Pond", deep.node().label);
		assertEquals(3, deep.depth());
	}

	@Test void liveSlicesAreRecognisedAndEveryNodeGetsARow() {
		List<Row> rows = WheelEdits.rows(wheel());
		assertEquals(Kind.LIVE_SLICE, rows.stream().filter(r -> r.path().equals(BOSS)).findFirst().orElseThrow().kind());
		long ringsAndLeaves = rows.stream().filter(r -> r.depth() == 0).count();
		assertEquals(wheel().size(), ringsAndLeaves);
	}

	@Test void addLeafInsertsAfterTheSiblingAndSelectsIt() {
		List<WheelNode> w = wheel();
		Result r = WheelEdits.addLeaf(w, JOBS, WheelNode.leaf("Warp", "minecraft:compass", "warp spawn"));
		assertTrue(r.ok());
		assertEquals(Path.top(4), r.selected());
		assertEquals("Warp", r.wheel().get(4).label);
		assertEquals("/warp spawn", r.wheel().get(4).command, "saved the way saving writes it");
		assertEquals(w.size() + 1, r.wheel().size());
		assertSaveStable(r.wheel());
	}

	@Test void addLeafWithNoPathGoesToTheEndOfTheTopLevel() {
		Result r = WheelEdits.addLeaf(wheel(), null, WheelNode.leaf("Warp", null, "/warp"));
		assertEquals(Path.top(wheel().size()), r.selected());
		assertEquals("Warp", r.wheel().get(wheel().size()).label);
	}

	@Test void addLeafNextToAnArcEntryJoinsTheArc() {
		Result r = WheelEdits.addLeaf(wheel(), SUSHI.arcEntry(0), WheelNode.leaf("Pond", null, "/warp pond"));
		assertEquals(SUSHI.arcEntry(1), r.selected());
		assertEquals(2, node(r.wheel(), SUSHI).arc.size());
		assertSaveStable(r.wheel());
	}

	@Test void addLeafIntoAnArcRefusesARingOrANoncommand() {
		Result r = WheelEdits.addLeaf(wheel(), SUSHI.arcEntry(0), WheelNode.ring("R", null));
		assertNotNull(r.error());
	}

	@Test void addLeafRefusesAnEntryThatSavingWouldDrop() {
		assertNotNull(WheelEdits.addLeaf(wheel(), null, WheelNode.leaf("", null, "/x")).error());
		assertNotNull(WheelEdits.addLeaf(wheel(), null, WheelNode.leaf("X", null, "  ")).error());
	}

	@Test void addLeafInsideARingSiblingStaysInThatRing() {
		Path first = MORE.child(0);
		Result r = WheelEdits.addLeaf(wheel(), first, WheelNode.leaf("Warp", null, "/warp"));
		assertEquals(MORE.child(1), r.selected());
		assertEquals("Warp", node(r.wheel(), MORE.child(1)).label);
		assertSaveStable(r.wheel());
	}

	@Test void addRingMakesAnEmptyRingSiblingThatSavingKeeps() {
		Result r = WheelEdits.addRing(wheel(), MORE, "Extras", "minecraft:chest");
		assertEquals(Path.top(14), r.selected());
		WheelNode ring = r.wheel().get(14);
		assertEquals(Kind.RING, Row.kindOf(ring, false));
		assertTrue(ring.children.isEmpty());
		assertSaveStable(r.wheel());
		assertNotNull(WheelEdits.addRing(wheel(), MORE, " ", null).error());
	}

	@Test void addRingRefusedNextToAnArcEntry() {
		assertNotNull(WheelEdits.addRing(wheel(), SUSHI.arcEntry(0), "R", null).error());
	}

	@Test void addToArcAppendsToACommandLeaf() {
		Result r = WheelEdits.addToArc(wheel(), JOBS, WheelNode.leaf("Top", null, "jobs top"));
		assertEquals(JOBS.arcEntry(0), r.selected());
		assertEquals("/jobs top", node(r.wheel(), JOBS.arcEntry(0)).command);
		Result more = WheelEdits.addToArc(r.wheel(), JOBS, WheelNode.leaf("Info", null, "/jobs info"));
		assertEquals(JOBS.arcEntry(1), more.selected());
		assertSaveStable(more.wheel());
	}

	@Test void addToArcIsRefusedForEverythingButACommandLeaf() {
		WheelNode leaf = WheelNode.leaf("L", null, "/l");
		assertNotNull(WheelEdits.addToArc(wheel(), MORE, leaf).error(), "ring");
		assertNotNull(WheelEdits.addToArc(wheel(), HOMES, leaf).error(), "live ring");
		assertNotNull(WheelEdits.addToArc(wheel(), BOSS, leaf).error(), "live slice");
		assertNotNull(WheelEdits.addToArc(wheel(), SUSHI.arcEntry(0), leaf).error(), "arc entry");
		assertNotNull(WheelEdits.addToArc(wheel(), JOBS, WheelNode.ring("R", null)).error(), "ring into an arc");
		assertNotNull(WheelEdits.addToArc(wheel(), JOBS, WheelNode.slice("B", null, "boss")).error(), "slice into an arc");
		assertNotNull(WheelEdits.addToArc(wheel(), Path.top(99), leaf).error(), "missing");
	}

	@Test void addChildAppendsInsideARingOrALiveRing() {
		Result r = WheelEdits.addChild(wheel(), MORE, WheelNode.leaf("Warp", null, "/warp"));
		int n = node(wheel(), MORE).children.size();
		assertEquals(MORE.child(n), r.selected());
		Result live = WheelEdits.addChild(r.wheel(), HOMES, WheelNode.leaf("Extra", null, "/extra"));
		assertEquals(HOMES.child(0), live.selected());
		Result vaults = WheelEdits.addChild(live.wheel(), VAULTS, WheelNode.ring("Sub", null));
		assertEquals(VAULTS.child(2), vaults.selected());
		assertSaveStable(vaults.wheel());
	}

	@Test void addChildIsRefusedForLeavesSlicesAndArcEntries() {
		WheelNode leaf = WheelNode.leaf("L", null, "/l");
		assertNotNull(WheelEdits.addChild(wheel(), JOBS, leaf).error());
		assertNotNull(WheelEdits.addChild(wheel(), BOSS, leaf).error());
		assertNotNull(WheelEdits.addChild(wheel(), SUSHI.arcEntry(0), leaf).error());
	}

	@Test void editChangesOnlyTheGivenFields() {
		Result r = WheelEdits.edit(wheel(), JOBS, "Work", null, "jobs browse", null);
		WheelNode n = node(r.wheel(), JOBS);
		assertEquals("Work", n.label);
		assertEquals("minecraft:iron_pickaxe", n.icon);
		assertEquals("/jobs browse", n.command);
		assertEquals(JOBS, r.selected());
		Result icon = WheelEdits.edit(r.wheel(), JOBS, null, "minecraft:stone", null, null);
		assertEquals("Work", node(icon.wheel(), JOBS).label);
		assertEquals("minecraft:stone", node(icon.wheel(), JOBS).icon);
		assertSaveStable(icon.wheel());
	}

	@Test void editKeepsClientActionsWithoutASlash() {
		Result r = WheelEdits.edit(wheel(), JOBS, null, null, "cubewheel:settings", null);
		assertEquals("cubewheel:settings", node(r.wheel(), JOBS).command);
		assertSaveStable(r.wheel());
	}

	@Test void editCommandAppliesToLeavesAndArcEntriesOnly() {
		assertNull(WheelEdits.edit(wheel(), SUSHI.arcEntry(0), null, null, "/warp x", null).error());
		assertNotNull(WheelEdits.edit(wheel(), MORE, null, null, "/x", null).error());
		assertNotNull(WheelEdits.edit(wheel(), HOMES, null, null, "/x", null).error());
		assertNotNull(WheelEdits.edit(wheel(), BOSS, null, null, "/x", null).error());
	}

	@Test void editAsArcAppliesToRingsAndLiveRingsOnly() {
		Result r = WheelEdits.edit(wheel(), MORE, null, null, null, true);
		assertTrue(node(r.wheel(), MORE).asArc);
		Result off = WheelEdits.edit(wheel(), HOMES, null, null, null, false);
		assertFalse(node(off.wheel(), HOMES).asArc);
		assertNotNull(WheelEdits.edit(wheel(), JOBS, null, null, null, true).error());
		assertNotNull(WheelEdits.edit(wheel(), BOSS, null, null, null, true).error());
	}

	@Test void editRefusesAResultSavingWouldDrop() {
		assertNotNull(WheelEdits.edit(wheel(), JOBS, " ", null, null, null).error());
		assertNotNull(WheelEdits.edit(wheel(), JOBS, null, null, "  ", null).error());
		assertNotNull(WheelEdits.edit(wheel(), Path.top(99), "x", null, null, null).error());
	}

	@Test void deleteRemovesTheNodeAndSelectsItsNeighbour() {
		Result r = WheelEdits.delete(wheel(), JOBS);
		assertEquals(wheel().size() - 1, r.wheel().size());
		assertEquals("Shop", r.wheel().get(3).label);
		assertEquals(JOBS, r.selected());
		Result last = WheelEdits.delete(wheel(), Path.top(wheel().size() - 1));
		assertEquals(Path.top(wheel().size() - 2), last.selected());
	}

	@Test void deletingTheLastArcEntryRemovesTheArcAndSelectsTheSlice() {
		Result r = WheelEdits.delete(wheel(), SUSHI.arcEntry(0));
		assertNull(node(r.wheel(), SUSHI).arc);
		assertEquals(SUSHI, r.selected());
		assertSaveStable(r.wheel());
	}

	@Test void deletingTheLastChildOfARingSelectsTheRing() {
		Path ring = Path.top(wheel().size());
		List<WheelNode> w = ok(WheelEdits.addRing(wheel(), MORE, "R", null));
		w = ok(WheelEdits.addChild(w, ring.withLast(new Path.Step(false, 14)), WheelNode.leaf("L", null, "/l")));
		Result r = WheelEdits.delete(w, Path.top(14).child(0));
		assertEquals(Path.top(14), r.selected());
		assertTrue(node(r.wheel(), Path.top(14)).children.isEmpty());
		assertSaveStable(r.wheel());
	}

	@Test void moveUpAndDownSwapAndFollowTheNode() {
		Result up = WheelEdits.moveUp(wheel(), JOBS);
		assertEquals(Path.top(2), up.selected());
		assertEquals("Jobs", up.wheel().get(2).label);
		assertEquals("Teleporter", up.wheel().get(3).label);
		Result down = WheelEdits.moveDown(wheel(), JOBS);
		assertEquals(Path.top(4), down.selected());
		assertEquals("Shop", down.wheel().get(3).label);
	}

	@Test void movingKeepsTheArcWithTheNode() {
		Result r = WheelEdits.moveDown(wheel(), SUSHI);
		assertEquals("Homes", r.wheel().get(0).label);
		assertEquals("Spawners", r.wheel().get(1).arc.get(0).label);
	}

	@Test void movesAtTheEndsAreRefused() {
		assertNotNull(WheelEdits.moveUp(wheel(), SUSHI).error());
		assertNotNull(WheelEdits.moveDown(wheel(), MORE).error());
		Path arc = JOBS;
		List<WheelNode> w = ok(WheelEdits.addToArc(wheel(), arc, WheelNode.leaf("A", null, "/a")));
		assertNotNull(WheelEdits.moveUp(w, JOBS.arcEntry(0)).error());
		assertNotNull(WheelEdits.moveDown(w, JOBS.arcEntry(0)).error());
	}

	@Test void arcEntriesAndChildrenReorderWithinTheirOwnList() {
		Path isles = Path.top(8);
		Result r = WheelEdits.moveDown(wheel(), isles.arcEntry(0));
		assertEquals(isles.arcEntry(1), r.selected());
		assertEquals("Wolfhaven", node(r.wheel(), isles.arcEntry(1)).label);
		Result c = WheelEdits.moveUp(wheel(), MORE.child(1));
		assertEquals(MORE.child(0), c.selected());
		assertEquals("Warps", node(c.wheel(), MORE.child(0)).label);
		assertSaveStable(c.wheel());
	}

	@Test void moveOutTakesAChildToBeItsRingsNextSibling() {
		Result r = WheelEdits.moveOut(wheel(), MORE.child(0));
		assertEquals(Path.top(14), r.selected());
		assertEquals("Shops", r.wheel().get(14).label);
		assertEquals(node(wheel(), MORE).children.size() - 1, r.wheel().get(13).children.size());
		Result deep = WheelEdits.moveOut(wheel(), MORE.child(1).child(0));
		assertEquals(MORE.child(2), deep.selected());
		assertEquals("Server warps", node(deep.wheel(), MORE.child(2)).label);
		assertSaveStable(deep.wheel());
	}

	@Test void moveOutTakesAnArcEntryToBeItsSlicesNextSibling() {
		Result r = WheelEdits.moveOut(wheel(), SUSHI.arcEntry(0));
		assertEquals(Path.top(1), r.selected());
		assertEquals("Spawners", r.wheel().get(1).label);
		assertNull(r.wheel().get(0).arc, "the emptied arc is gone");
		assertSaveStable(r.wheel());
	}

	@Test void moveOutIsRefusedAtTheTopLevelAndForMissingPaths() {
		assertNotNull(WheelEdits.moveOut(wheel(), JOBS).error());
		assertNotNull(WheelEdits.moveOut(wheel(), MORE.child(99)).error());
	}

	@Test void noEditEverMutatesItsInput() {
		List<WheelNode> w = wheel();
		String before = dump(w);
		WheelNode leaf = WheelNode.leaf("Warp", null, "warp");
		String leafBefore = dump(List.of(leaf));
		WheelEdits.addLeaf(w, JOBS, leaf);
		WheelEdits.addRing(w, JOBS, "R", null);
		WheelEdits.addToArc(w, JOBS, leaf);
		WheelEdits.addChild(w, MORE, leaf);
		WheelEdits.edit(w, JOBS, "X", "i", "/c", null);
		WheelEdits.edit(w, MORE, null, null, null, true);
		WheelEdits.delete(w, JOBS);
		WheelEdits.delete(w, SUSHI.arcEntry(0));
		WheelEdits.moveUp(w, JOBS);
		WheelEdits.moveDown(w, JOBS);
		WheelEdits.moveOut(w, MORE.child(0));
		WheelEdits.moveOut(w, SUSHI.arcEntry(0));
		assertEquals(before, dump(w));
		assertEquals(leafBefore, dump(List.of(leaf)), "the leaf passed in is copied, not adopted");
	}

	@Test void aRefusalReturnsTheWheelItWasGiven() {
		List<WheelNode> w = wheel();
		Result r = WheelEdits.moveUp(w, SUSHI);
		assertSame(w, r.wheel());
		assertNull(r.selected());
	}

	@Test void aRoundOfEditsLeavesSomethingSavingKeepsUnchanged() {
		List<WheelNode> w = wheel();
		w = ok(WheelEdits.addRing(w, MORE, "Extras", "minecraft:chest"));
		Path extras = Path.top(14);
		w = ok(WheelEdits.addChild(w, extras, WheelNode.leaf("Spawn", "minecraft:grass_block", "spawn")));
		w = ok(WheelEdits.addChild(w, extras, WheelNode.leaf("Settings", null, "cubewheel:settings")));
		w = ok(WheelEdits.addToArc(w, JOBS, WheelNode.leaf("Top", null, "jobs top")));
		w = ok(WheelEdits.addLeaf(w, JOBS.arcEntry(0), WheelNode.leaf("Info", null, "/jobs info")));
		w = ok(WheelEdits.moveUp(w, JOBS.arcEntry(1)));
		w = ok(WheelEdits.edit(w, extras, "Misc", null, null, true));
		w = ok(WheelEdits.moveOut(w, extras.child(0)));
		w = ok(WheelEdits.moveDown(w, SUSHI));
		w = ok(WheelEdits.delete(w, Path.top(12)));
		w = ok(WheelEdits.moveOut(w, Path.top(8).arcEntry(2)));
		assertSaveStable(w);
		for (Row row : WheelEdits.rows(w)) {
			assertNull(WheelNodeCheck.problem(row.node(), row.kind() == Kind.ARC_ENTRY), row.path().toString());
		}
	}

	@Test void aStalePathIsAnErrorNotACrash() {
		assertNotNull(WheelEdits.delete(wheel(), Path.top(99)).error());
		assertNotNull(WheelEdits.moveUp(wheel(), JOBS.child(0)).error());
		assertNotNull(WheelEdits.edit(wheel(), JOBS.arcEntry(0), "x", null, null, null).error());
		assertNotNull(WheelEdits.addLeaf(wheel(), Path.top(-1), WheelNode.leaf("x", null, "/x")).error());
	}
}
