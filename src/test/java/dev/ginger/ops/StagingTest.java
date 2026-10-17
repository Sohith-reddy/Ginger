package dev.ginger.ops;

import dev.ginger.TestRepo;
import dev.ginger.objects.HashUtil;
import dev.ginger.objects.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StagingTest {

    @TempDir
    Path tmp;

    private TestRepo t;

    @BeforeEach
    void setUp() throws IOException {
        t = TestRepo.init(tmp);
    }

    private List<String> staged() throws IOException {
        return List.copyOf(t.repo().readIndex().entries().keySet());
    }

    @Test
    void stagesSingleFileAndPersists() throws IOException {
        t.write("README.md", "hello").add("README.md");

        assertEquals(List.of("README.md"), staged());
        assertEquals(HashUtil.hash(StoredObject.blob("hello".getBytes(StandardCharsets.UTF_8))),
                t.repo().readIndex().get("README.md"));
        assertTrue(t.repo().objects().contains(t.repo().readIndex().get("README.md")));
    }

    @Test
    void stagesDirectoryRecursivelyWithoutMetadata() throws IOException {
        t.write("a.txt", "a").write("src/main/B.java", "b").write("my docs/c d.txt", "c");
        Files.createDirectories(tmp.resolve("empty"));

        t.add(".");

        assertEquals(List.of("a.txt", "my docs/c d.txt", "src/main/B.java"), staged());
    }

    @Test
    void resolvesPathsRelativeToWorkingDirectory() throws IOException {
        t.write("src/A.java", "a");
        Staging.add(t.repo(), tmp.resolve("src"), List.of("A.java"));

        assertEquals(List.of("src/A.java"), staged());
    }

    @Test
    void rejectsPathsOutsideRepository() throws IOException {
        Files.writeString(tmp.getParent().resolve("outside-" + tmp.getFileName()), "x");

        assertThrows(IOException.class, () -> t.add("../outside-" + tmp.getFileName()));
        assertThrows(IOException.class, () -> t.add(".ginger/HEAD"));
    }

    @Test
    void missingFileFailsWithoutChangingIndex() throws IOException {
        t.write("a.txt", "a").add("a.txt");
        t.write("b.txt", "b");

        IOException e = assertThrows(IOException.class, () -> t.add("b.txt", "missing.txt"));

        assertTrue(e.getMessage().contains("missing.txt"));
        assertEquals(List.of("a.txt"), staged());
    }

    @Test
    void repeatedAddIsIdempotent() throws IOException {
        t.write("a.txt", "same").add("a.txt");
        byte[] before = Files.readAllBytes(t.repo().indexFile());
        long objects = countObjects();

        t.add("a.txt").add(".");

        assertEquals(objects, countObjects());
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(t.repo().indexFile())));
    }

    @Test
    void addingDeletedFileStagesDeletion() throws IOException {
        t.write("a.txt", "a").write("dir/b.txt", "b").add(".");
        t.delete("a.txt").delete("dir/b.txt");

        t.add("a.txt").add(".");

        assertEquals(List.of(), staged());
    }

    @Test
    void symbolicLinksAreNotFollowed() throws IOException {
        t.write("target.txt", "t");
        try {
            Files.createSymbolicLink(tmp.resolve("link.txt"), tmp.resolve("target.txt"));
        } catch (UnsupportedOperationException | IOException e) {
            return;
        }

        assertThrows(IOException.class, () -> t.add("link.txt"));
        t.add(".");
        assertEquals(List.of("target.txt"), staged());
    }

    private long countObjects() throws IOException {
        try (Stream<Path> walk = Files.walk(t.repo().objectsDir())) {
            return walk.filter(Files::isRegularFile).count();
        }
    }
}
