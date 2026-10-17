package dev.ginger.worktree;

import dev.ginger.TestRepo;
import dev.ginger.objects.CorruptObjectException;
import dev.ginger.objects.ObjectId;
import dev.ginger.ops.Checkout;
import dev.ginger.refs.Head;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HardeningTest {

    @TempDir
    Path tmp;

    private TestRepo t;

    @BeforeEach
    void setUp() throws IOException {
        t = TestRepo.init(tmp.resolve("repo"));
    }

    @Test
    void unicodeAndSpacedNamesSurviveCommitAndCheckout() throws IOException {
        String encoding = System.getProperty("sun.jnu.encoding", "UTF-8");
        assumeTrue(Charset.forName(encoding).newEncoder().canEncode("naïve 文件"), "file names use " + encoding);
        ObjectId first = t.write("naïve 文件.txt", "unicode").write("my docs/a b.txt", "spaces").add(".").commit("first");
        t.delete("naïve 文件.txt").delete("my docs/a b.txt").add(".").commit("second");

        Checkout.moveTo(t.repo(), first, Head.detachedAt(first), false);

        assertEquals("unicode", t.read("naïve 文件.txt"));
        assertEquals("spaces", t.read("my docs/a b.txt"));
    }

    @Test
    void nestedMetadataDirectoriesAreNeverStaged() throws IOException {
        t.write("sub/.ginger/HEAD", "x").write("sub/file.txt", "f").add(".");
        assertEquals(List.of("sub/file.txt"), List.copyOf(t.repo().readIndex().entries().keySet()));
        assertThrows(IOException.class, () -> t.add(".ginger"));
    }

    @Test
    void pathsThroughSymbolicLinksAreRejected() throws IOException {
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "secret");
        try {
            Files.createSymbolicLink(t.root().resolve("link"), outside);
        } catch (UnsupportedOperationException | IOException e) {
            return;
        }

        IOException e = assertThrows(IOException.class, () -> t.add("link/secret.txt"));
        assertTrue(e.getMessage().contains("symbolic link"));
        t.add(".");
        assertTrue(t.repo().readIndex().isEmpty());
    }

    @Test
    void readOnlyFilesCanBeStagedAndRestored() throws IOException {
        t.write("ro.txt", "v1").add("ro.txt").commit("first");
        Path file = t.root().resolve("ro.txt");
        file.toFile().setReadOnly();
        t.delete("ro.txt");

        dev.ginger.ops.Restorer.restoreWorkTree(t.repo(), t.root(), List.of("ro.txt"), false);

        assertEquals("v1", t.read("ro.txt"));
    }

    @Test
    void missingDirectoriesAreReportedClearly() {
        IOException e = assertThrows(IOException.class, () -> t.add("no/such/dir"));
        assertTrue(e.getMessage().contains("did not match"));
    }

    @Test
    void corruptIndexGivesActionableError() throws IOException {
        t.write("a.txt", "a").add("a.txt");
        Files.write(t.repo().indexFile(), new byte[]{'G', 'I', 'D', 'X', 0, 0, 0});

        CorruptObjectException e = assertThrows(CorruptObjectException.class, () -> t.repo().readIndex());
        assertTrue(e.getMessage().contains("restore --staged"));
    }

    @Test
    void corruptObjectNamesTheFile() throws IOException {
        ObjectId id = t.write("a.txt", "a").add("a.txt").commit("first");
        Files.write(t.repo().objects().pathFor(id), new byte[]{1});

        CorruptObjectException e = assertThrows(CorruptObjectException.class, () -> t.repo().objects().read(id));
        assertTrue(e.getMessage().contains(t.repo().objects().pathFor(id).toString()));
        assertFalse(e.getMessage().contains("\u0001"));
    }
}
