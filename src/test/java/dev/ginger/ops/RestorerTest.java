package dev.ginger.ops;

import dev.ginger.TestRepo;
import dev.ginger.objects.HashUtil;
import dev.ginger.objects.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestorerTest {

    @TempDir
    Path tmp;

    private TestRepo t;

    @BeforeEach
    void setUp() throws IOException {
        t = TestRepo.init(tmp);
        t.write("a.txt", "committed").write("dir/b.txt", "b").add(".").commit("first");
    }

    private void restore(boolean force, String... paths) throws IOException {
        Restorer.restoreWorkTree(t.repo(), tmp, List.of(paths), force);
    }

    @Test
    void restoresDeletedFile() throws IOException {
        t.delete("dir/b.txt");
        restore(false, "dir");
        assertEquals("b", t.read("dir/b.txt"));
    }

    @Test
    void refusesToOverwriteLocalChangesWithoutForce() throws IOException {
        t.write("a.txt", "local edit");

        assertThrows(IOException.class, () -> restore(false, "a.txt"));
        assertEquals("local edit", t.read("a.txt"));

        restore(true, "a.txt");
        assertEquals("committed", t.read("a.txt"));
    }

    @Test
    void restoresFromIndexNotHead() throws IOException {
        t.write("a.txt", "staged").add("a.txt").delete("a.txt");
        restore(false, "a.txt");
        assertEquals("staged", t.read("a.txt"));
    }

    @Test
    void unstageResetsIndexToHead() throws IOException {
        t.write("a.txt", "changed").write("new.txt", "n").add(".");

        Restorer.unstage(t.repo(), tmp, List.of("a.txt", "new.txt"));

        assertFalse(t.repo().readIndex().entries().containsKey("new.txt"));
        assertEquals(HashUtil.hash(StoredObject.blob("committed".getBytes(StandardCharsets.UTF_8))), t.repo().readIndex().get("a.txt"));
        assertEquals("changed", t.read("a.txt"));
    }

    @Test
    void rejectsUnknownAndEscapingPaths() {
        assertThrows(IOException.class, () -> restore(false, "missing.txt"));
        assertThrows(IOException.class, () -> restore(true, "../outside"));
        assertThrows(IOException.class, () -> Restorer.unstage(t.repo(), tmp, List.of("../outside")));
    }
}
