package dev.ginger.ops;

import dev.ginger.TestRepo;
import dev.ginger.objects.Commit;
import dev.ginger.objects.ObjectId;
import dev.ginger.objects.ObjectType;
import dev.ginger.objects.StoredObject;
import dev.ginger.refs.Head;
import dev.ginger.refs.Refs;
import dev.ginger.status.StatusCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckoutTest {

    @TempDir
    Path tmp;

    private TestRepo t;
    private Refs refs;
    private ObjectId base;

    @BeforeEach
    void setUp() throws IOException {
        t = TestRepo.init(tmp);
        refs = t.repo().refs();
        base = t.write("shared.txt", "shared").write("a.txt", "base").add(".").commit("base");
        refs.updateBranch("feature", base);
    }

    private void switchTo(String branch, boolean force) throws IOException {
        Checkout.moveTo(t.repo(), refs.branch(branch).orElseThrow(), Head.onBranch(branch), force);
    }

    @Test
    void newBranchStartsAtCurrentCommit() throws IOException {
        assertEquals(refs.branch("main"), refs.branch("feature"));
    }

    @Test
    void switchingBetweenDivergedBranchesRebuildsWorkTree() throws IOException {
        switchTo("feature", false);
        t.write("a.txt", "feature").write("feature/only.txt", "f").add(".").commit("feature work");

        switchTo("main", false);
        t.write("main.txt", "m").add(".").commit("main work");
        assertEquals("base", t.read("a.txt"));
        assertFalse(t.exists("feature"));

        switchTo("feature", false);
        assertEquals(Head.onBranch("feature"), refs.head());
        assertEquals("feature", t.read("a.txt"));
        assertEquals("f", t.read("feature/only.txt"));
        assertEquals("shared", t.read("shared.txt"));
        assertFalse(t.exists("main.txt"));
        assertTrue(StatusCalculator.compute(t.repo()).isClean());
    }

    @Test
    void refusesToOverwriteLocalModifications() throws IOException {
        switchTo("feature", false);
        t.write("a.txt", "feature").add("a.txt").commit("feature work");
        switchTo("main", false);
        t.write("a.txt", "local");

        assertThrows(IOException.class, () -> switchTo("feature", false));
        assertEquals("local", t.read("a.txt"));
        assertEquals(Head.onBranch("main"), refs.head());

        switchTo("feature", true);
        assertEquals("feature", t.read("a.txt"));
    }

    @Test
    void refusesToOverwriteUntrackedFile() throws IOException {
        switchTo("feature", false);
        t.write("new.txt", "tracked").add("new.txt").commit("add new");
        switchTo("main", false);
        t.write("new.txt", "untracked");

        assertThrows(IOException.class, () -> switchTo("feature", false));
        assertEquals("untracked", t.read("new.txt"));
    }

    @Test
    void carriesUnrelatedLocalChanges() throws IOException {
        switchTo("feature", false);
        t.write("a.txt", "feature").add("a.txt").commit("feature work");
        switchTo("main", false);
        t.write("shared.txt", "local edit").write("untracked.txt", "u");

        switchTo("feature", false);

        assertEquals("local edit", t.read("shared.txt"));
        assertEquals("u", t.read("untracked.txt"));
        assertEquals(List.of("shared.txt"), StatusCalculator.compute(t.repo()).modified());
    }

    @Test
    void detachedCheckoutReconstructsOldTree() throws IOException {
        t.write("a.txt", "second").write("b.txt", "b").add(".").commit("second");

        Checkout.moveTo(t.repo(), base, Head.detachedAt(base), false);

        assertEquals(Head.detachedAt(base), refs.head());
        assertEquals("base", t.read("a.txt"));
        assertFalse(t.exists("b.txt"));
        assertTrue(StatusCalculator.compute(t.repo()).isClean());
    }

    @Test
    void invalidTargetLeavesEverythingUnchanged() throws IOException {
        byte[] headBefore = Files.readAllBytes(t.repo().headFile());
        byte[] indexBefore = Files.readAllBytes(t.repo().indexFile());
        ObjectId blob = t.repo().objects().write(StoredObject.blob(new byte[]{1}));

        assertThrows(IOException.class, () -> t.repo().objects().resolve("ffff"));
        assertThrows(IOException.class, () -> Checkout.moveTo(t.repo(), blob, Head.detachedAt(blob), true));

        assertEquals(new String(headBefore), new String(Files.readAllBytes(t.repo().headFile())));
        assertEquals(new String(indexBefore), new String(Files.readAllBytes(t.repo().indexFile())));
    }

    @Test
    void treeWithTraversalNameIsRejected() throws IOException {
        ObjectId blob = t.repo().objects().write(StoredObject.blob("evil".getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream tree = new ByteArrayOutputStream();
        tree.writeBytes("blob ..".getBytes(StandardCharsets.UTF_8));
        tree.write(0);
        tree.writeBytes(blob.toBytes());
        ObjectId treeId = t.repo().objects().write(new StoredObject(ObjectType.TREE, tree.toByteArray()));
        ObjectId evil = t.repo().objects().write(
                new Commit(treeId, List.of(base), "x", Instant.now(), "evil").toObject());

        assertThrows(IOException.class, () -> Checkout.moveTo(t.repo(), evil, Head.detachedAt(evil), true));
        assertEquals(Head.onBranch("main"), refs.head());
        assertFalse(Files.exists(tmp.getParent().resolve("evil")));
    }
}
