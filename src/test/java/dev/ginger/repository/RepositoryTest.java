package dev.ginger.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryTest {

    @TempDir
    Path tmp;

    @Test
    void initCreatesRepositoryLayout() throws IOException {
        Repository repo = Repository.init(tmp);

        Path dir = repo.gingerDir();
        assertTrue(Files.isDirectory(dir.resolve("objects")));
        assertTrue(Files.isDirectory(dir.resolve("refs/heads")));
        assertTrue(Files.isDirectory(dir.resolve("refs/tags")));
        assertEquals("ref: refs/heads/main\n", Files.readString(dir.resolve("HEAD")));
    }

    @Test
    void initFailsWhenRepositoryExists() throws IOException {
        Repository.init(tmp);
        assertThrows(IOException.class, () -> Repository.init(tmp));
    }
}
