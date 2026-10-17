package dev.ginger.ops;

import dev.ginger.TestRepo;
import dev.ginger.merge.MergeState;
import dev.ginger.objects.Commit;
import dev.ginger.objects.ObjectId;
import dev.ginger.objects.Snapshots;
import dev.ginger.refs.Head;
import dev.ginger.status.StatusCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergerTest {

    @TempDir
    Path tmp;

    private TestRepo t;
    private ObjectId base;

    @BeforeEach
    void setUp() throws IOException {
        t = TestRepo.init(tmp);
        base = t.write("f.txt", "1\n2\n3\n4\n5\n6\n7\n8\n").write("keep.txt", "k").add(".").commit("base");
        t.repo().refs().updateBranch("feature", base);
    }

    private void onFeature(IOAction action) throws IOException {
        Checkout.moveTo(t.repo(), t.repo().refs().branch("feature").orElseThrow(), Head.onBranch("feature"), false);
        action.run();
        Checkout.moveTo(t.repo(), t.repo().refs().branch("main").orElseThrow(), Head.onBranch("main"), false);
    }

    private Merger.Result merge() throws IOException {
        return Merger.merge(t.repo(), "feature", "Test", Instant.parse("2026-02-01T00:00:00Z"));
    }

    interface IOAction {
        void run() throws IOException;
    }

    @Test
    void nonOverlappingChangesMergeIntoTwoParentCommit() throws IOException {
        ObjectId ours = t.write("f.txt", "ONE\n2\n3\n4\n5\n6\n7\n8\n").add("f.txt").commit("ours");
        onFeature(() -> t.write("f.txt", "1\n2\n3\n4\n5\n6\n7\nEIGHT\n").write("new.txt", "n").add(".").commit("theirs"));
        ObjectId theirs = t.repo().refs().branch("feature").orElseThrow();

        Merger.Result result = merge();

        assertEquals(Merger.Outcome.MERGED, result.outcome());
        Commit commit = Snapshots.readCommit(t.repo().objects(), result.commit());
        assertEquals(List.of(ours, theirs), commit.parents());
        assertEquals(result.commit(), t.repo().refs().branch("main").orElseThrow());
        assertEquals("ONE\n2\n3\n4\n5\n6\n7\nEIGHT\n", t.read("f.txt"));
        assertEquals("n", t.read("new.txt"));
        assertTrue(StatusCalculator.compute(t.repo()).isClean());
    }

    @Test
    void modifyModifyConflictBlocksCommitUntilResolved() throws IOException {
        ObjectId ours = t.write("f.txt", "1\nOURS\n3\n4\n5\n6\n7\n8\n").add("f.txt").commit("ours");
        onFeature(() -> t.write("f.txt", "1\nTHEIRS\n3\n4\n5\n6\n7\n8\n").add("f.txt").commit("theirs"));

        Merger.Result result = merge();

        assertEquals(Merger.Outcome.CONFLICTED, result.outcome());
        assertEquals(List.of(new Merger.Conflict("f.txt", "content")), result.conflicts());
        assertTrue(t.read("f.txt").contains("<<<<<<< HEAD\nOURS\n=======\nTHEIRS\n>>>>>>> feature\n"));
        assertEquals(ours, t.repo().refs().branch("main").orElseThrow());
        assertEquals(List.of("f.txt"), StatusCalculator.compute(t.repo()).conflicts());
        assertThrows(IOException.class, () -> t.commit("too early"));

        t.write("f.txt", "1\nBOTH\n3\n4\n5\n6\n7\n8\n").add("f.txt");
        ObjectId merged = t.commit("resolved");

        assertEquals(2, Snapshots.readCommit(t.repo().objects(), merged).parents().size());
        assertTrue(MergeState.read(t.repo()).isEmpty());
        assertTrue(StatusCalculator.compute(t.repo()).isClean());
    }

    @Test
    void addAddConflict() throws IOException {
        t.write("same.txt", "ours\n").add("same.txt").commit("ours");
        onFeature(() -> t.write("same.txt", "theirs\n").add("same.txt").commit("theirs"));

        Merger.Result result = merge();

        assertEquals(List.of(new Merger.Conflict("same.txt", "add/add")), result.conflicts());
        assertEquals("<<<<<<< HEAD\nours\n=======\ntheirs\n>>>>>>> feature\n", t.read("same.txt"));
    }

    @Test
    void deleteModifyConflicts() throws IOException {
        t.delete("keep.txt").add("keep.txt").commit("ours deletes");
        onFeature(() -> t.write("keep.txt", "changed").add("keep.txt").commit("theirs modifies"));

        Merger.Result result = merge();

        assertEquals(List.of(new Merger.Conflict("keep.txt", "delete/modify")), result.conflicts());
        assertEquals("changed", t.read("keep.txt"));

        t.delete("keep.txt").add("keep.txt");
        t.commit("keep deletion");
        assertFalse(t.exists("keep.txt"));
    }

    @Test
    void binaryConflictKeepsOurVersion() throws IOException {
        Files.write(tmp.resolve("img.bin"), new byte[]{0, 1});
        t.add("img.bin").commit("add binary");
        t.repo().refs().updateBranch("feature", t.repo().refs().branch("main").orElseThrow());
        Files.write(tmp.resolve("img.bin"), new byte[]{0, 2});
        t.add("img.bin").commit("ours");
        onFeature(() -> {
            Files.write(tmp.resolve("img.bin"), new byte[]{0, 3});
            t.add("img.bin").commit("theirs");
        });

        Merger.Result result = merge();

        assertEquals(List.of(new Merger.Conflict("img.bin", "binary")), result.conflicts());
        assertArrayEquals(new byte[]{0, 2}, Files.readAllBytes(tmp.resolve("img.bin")));
    }

    @Test
    void fastForwardAndUpToDate() throws IOException {
        onFeature(() -> t.write("new.txt", "n").add("new.txt").commit("ahead"));
        ObjectId feature = t.repo().refs().branch("feature").orElseThrow();

        assertEquals(Merger.Outcome.FAST_FORWARD, merge().outcome());
        assertEquals(feature, t.repo().refs().branch("main").orElseThrow());
        assertEquals("n", t.read("new.txt"));
        assertEquals(Merger.Outcome.UP_TO_DATE, merge().outcome());
    }

    @Test
    void refusesWithLocalChanges() throws IOException {
        t.write("x.txt", "x").add("x.txt").commit("ours");
        onFeature(() -> t.write("y.txt", "y").add("y.txt").commit("theirs"));
        t.write("x.txt", "dirty");

        assertThrows(IOException.class, this::merge);
        assertTrue(MergeState.read(t.repo()).isEmpty());
    }

    @Test
    void untrackedFileInTheWayStopsMergeBeforeChanges() throws IOException {
        ObjectId ours = t.write("x.txt", "x").add("x.txt").commit("ours");
        onFeature(() -> t.write("y.txt", "tracked").add("y.txt").commit("theirs"));
        t.write("y.txt", "untracked");

        assertThrows(IOException.class, this::merge);
        assertEquals("untracked", t.read("y.txt"));
        assertEquals(ours, t.repo().refs().branch("main").orElseThrow());
        assertTrue(MergeState.read(t.repo()).isEmpty());
    }

    @Test
    void abortRestoresPreMergeState() throws IOException {
        ObjectId ours = t.write("f.txt", "1\nOURS\n3\n4\n5\n6\n7\n8\n").add("f.txt").commit("ours");
        onFeature(() -> t.write("f.txt", "1\nTHEIRS\n3\n4\n5\n6\n7\n8\n").write("new.txt", "n").add(".").commit("theirs"));
        merge();

        Merger.abort(t.repo());

        assertEquals("1\nOURS\n3\n4\n5\n6\n7\n8\n", t.read("f.txt"));
        assertFalse(t.exists("new.txt"));
        assertEquals(ours, t.repo().refs().branch("main").orElseThrow());
        assertTrue(StatusCalculator.compute(t.repo()).isClean());
    }

    @Test
    void unknownBranchAndBranchSwitchDuringMerge() throws IOException {
        assertThrows(IOException.class, () -> Merger.merge(t.repo(), "missing", "x", Instant.now()));

        t.write("f.txt", "ours\n").add("f.txt").commit("ours");
        onFeature(() -> t.write("f.txt", "theirs\n").add("f.txt").commit("theirs"));
        merge();

        assertThrows(IOException.class, () -> Checkout.moveTo(t.repo(), base, Head.detachedAt(base), true));
        assertThrows(IOException.class, this::merge);
    }
}
