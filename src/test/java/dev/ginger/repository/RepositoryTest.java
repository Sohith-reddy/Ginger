package dev.ginger.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryTest {

    @TempDir
    Path tmp;

    @Test
    void initCreatesRepositoryLayout() throws IOException {
        Repository repo = Repository.init(tmp);

        Path dir = repo.gingerDir();
        assertEquals(tmp.resolve(".ginger"), dir);
        assertTrue(Files.isDirectory(dir.resolve("objects")));
        assertTrue(Files.isDirectory(dir.resolve("refs/heads")));
        assertEquals("ref: refs/heads/main\n", Files.readString(dir.resolve("HEAD")));
        assertEquals("version=1\n", Files.readString(dir.resolve("config")));
        assertEquals(0, Files.size(dir.resolve("index")));
    }

    @Test
    void initFailsWhenRepositoryExists() throws IOException {
        Repository.init(tmp);
        assertThrows(IOException.class, () -> Repository.init(tmp));
    }

    @Test
    void initCreatesMissingDirectoryAndNothingElse() throws IOException {
        Path project = tmp.resolve("project");
        Repository.init(project);

        try (Stream<Path> top = Files.list(tmp); Stream<Path> inside = Files.list(project)) {
            assertEquals(List.of(project), top.toList());
            assertEquals(List.of(project.resolve(".ginger")), inside.toList());
        }
    }

    @Test
    void findFromNestedDirectory() throws IOException {
        Repository.init(tmp);
        Path nested = Files.createDirectories(tmp.resolve("a/b/c"));

        Repository repo = Repository.find(nested);

        assertEquals(tmp, repo.workTree());
        assertEquals(tmp.resolve(".ginger/objects"), repo.objectsDir());
    }

    @Test
    void findOutsideRepositoryFails() {
        assertThrows(RepositoryNotFoundException.class, () -> Repository.find(tmp));
    }

    @Test
    void openRejectsIncompleteRepository() throws IOException {
        Repository.init(tmp);
        Files.delete(tmp.resolve(".ginger/HEAD"));

        IOException e = assertThrows(IOException.class, () -> Repository.open(tmp));
        assertTrue(e.getMessage().contains("HEAD"));
    }

    @Test
    void openRejectsUnknownFormatVersion() throws IOException {
        Repository.init(tmp);
        Files.writeString(tmp.resolve(".ginger/config"), "version=99\n");

        assertThrows(IOException.class, () -> Repository.open(tmp));
    }

    @Test
    void initLeavesNoStagingDirectoryBehind() throws IOException {
        Repository.init(tmp);

        try (Stream<Path> entries = Files.list(tmp)) {
            assertFalse(entries.anyMatch(p -> p.getFileName().toString().startsWith(".ginger-init-")));
        }
    }
}
