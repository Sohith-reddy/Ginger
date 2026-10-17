package dev.ginger.ops;

import dev.ginger.TestRepo;
import dev.ginger.objects.Commit;
import dev.ginger.objects.ObjectId;
import dev.ginger.objects.ObjectType;
import dev.ginger.objects.Snapshots;
import dev.ginger.objects.Tree;
import dev.ginger.repository.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitterTest {

    @TempDir
    Path tmp;

    private TestRepo t;

    @BeforeEach
    void setUp() throws IOException {
        t = TestRepo.init(tmp);
    }

    private Commit commit(ObjectId id) throws IOException {
        return Snapshots.readCommit(t.repo().objects(), id);
    }

    @Test
    void firstCommitHasNoParentAndAdvancesBranch() throws IOException {
        ObjectId id = t.write("a.txt", "a").add("a.txt").commit("Initial commit");

        Commit commit = commit(id);
        assertEquals(List.of(), commit.parents());
        assertEquals("Initial commit\n", commit.message());
        assertEquals(id, t.repo().refs().branch("main").orElseThrow());
    }

    @Test
    void secondCommitReferencesFirstAndSnapshotsAreCorrect() throws IOException {
        ObjectId first = t.write("a.txt", "one").add("a.txt").commit("first");
        ObjectId second = t.write("a.txt", "two").write("dir/b.txt", "b").add(".").commit("second");

        Repository reopened = Repository.open(tmp);
        Commit commit = Snapshots.readCommit(reopened.objects(), second);
        assertEquals(List.of(first), commit.parents());

        Map<String, ObjectId> firstTree = Snapshots.readCommitTree(reopened.objects(), first);
        Map<String, ObjectId> secondTree = Snapshots.readCommitTree(reopened.objects(), second);
        assertEquals(List.of("a.txt"), List.copyOf(firstTree.keySet()));
        assertEquals(List.of("a.txt", "dir/b.txt"), List.copyOf(secondTree.keySet()));
        assertEquals("two", blob(secondTree.get("a.txt")));
        assertEquals("one", blob(firstTree.get("a.txt")));
    }

    @Test
    void nothingToCommitIsRejected() throws IOException {
        assertTrue(Committer.commit(t.repo(), "empty", "a", Instant.now()).isEmpty());

        t.write("a.txt", "a").add("a.txt").commit("first");
        assertTrue(Committer.commit(t.repo(), "again", "a", Instant.now()).isEmpty());
    }

    @Test
    void emptyMessageIsRejected() throws IOException {
        t.write("a.txt", "a").add("a.txt");
        assertThrows(IllegalArgumentException.class, () -> Committer.commit(t.repo(), "  ", "a", Instant.now()));
        assertTrue(t.repo().refs().branch("main").isEmpty());
    }

    @Test
    void commitIdsAreDeterministic() throws IOException {
        Commit a = new Commit(ObjectId.of("a".repeat(64)), List.of(), "X", Instant.parse("2026-01-01T00:00:00Z"), "m");
        Commit b = new Commit(ObjectId.of("a".repeat(64)), List.of(), "X", Instant.parse("2026-01-01T00:00:00Z"), "m");
        assertEquals(a.toObject(), b.toObject());
        assertEquals(a, Commit.fromObject(a.toObject()));
    }

    @Test
    void treesAreSortedAndNested() throws IOException {
        ObjectId id = t.write("z.txt", "z").write("a/b.txt", "b").write("m.txt", "m").add(".").commit("c");

        Tree root = Tree.fromObject(t.repo().objects().read(commit(id).tree(), ObjectType.TREE));
        assertEquals(List.of("a", "m.txt", "z.txt"), root.entries().stream().map(e -> e.name()).toList());
        assertEquals(ObjectType.TREE, root.entries().get(0).type());
    }

    @Test
    void existingCommitsAreUntouchedByLaterWork() throws IOException {
        ObjectId first = t.write("a.txt", "a").add("a.txt").commit("first");
        Path file = t.repo().objects().pathFor(first);
        byte[] before = Files.readAllBytes(file);

        t.write("a.txt", "changed").add("a.txt").commit("second");

        assertEquals(new String(before, StandardCharsets.UTF_8),
                new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    private String blob(ObjectId id) throws IOException {
        return new String(t.repo().objects().read(id).content(), StandardCharsets.UTF_8);
    }
}
