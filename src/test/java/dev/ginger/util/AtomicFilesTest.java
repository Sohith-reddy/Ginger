package dev.ginger.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtomicFilesTest {

    @TempDir
    Path tmp;

    @Test
    void replacesExistingFile() throws IOException {
        Path file = tmp.resolve("ref");
        Files.writeString(file, "old");
        AtomicFiles.write(file, "new".getBytes());
        assertEquals("new", Files.readString(file));
    }

    @Test
    void failedWriteKeepsTargetAndLeavesNoTemporaryFile() throws IOException {
        Path target = tmp.resolve("blocked");
        Files.createDirectories(target.resolve("child"));

        assertThrows(IOException.class, () -> AtomicFiles.write(target, "data".getBytes()));

        try (Stream<Path> entries = Files.list(tmp)) {
            assertEquals(List.of(target), entries.toList());
        }
    }

    @Test
    void createsMissingDirectories() throws IOException {
        Path file = tmp.resolve("a/b/c");
        AtomicFiles.write(file, new byte[]{1});
        assertEquals(1, Files.size(file));
    }
}
