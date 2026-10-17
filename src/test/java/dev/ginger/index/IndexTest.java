package dev.ginger.index;

import dev.ginger.objects.CorruptObjectException;
import dev.ginger.objects.HashUtil;
import dev.ginger.objects.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexTest {

    @TempDir
    Path tmp;

    private static ObjectId id(int n) {
        return HashUtil.sha256(new byte[]{(byte) n});
    }

    @Test
    void emptyFileIsEmptyIndex() throws IOException {
        Path file = Files.createFile(tmp.resolve("index"));
        assertTrue(Index.read(file).isEmpty());
    }

    @Test
    void roundTripsUnusualNames() throws IOException {
        Index index = new Index();
        index.put("with space.txt", id(1));
        index.put("dir/new\nline", id(2));
        index.put("ünïcödé/файл", id(3));
        Path file = tmp.resolve("index");
        index.write(file);

        assertEquals(index.entries(), Index.read(file).entries());
    }

    @Test
    void serializationIsDeterministic() throws IOException {
        Index a = new Index();
        a.put("b", id(2));
        a.put("a", id(1));
        Index b = new Index();
        b.put("a", id(1));
        b.put("b", id(2));
        a.write(tmp.resolve("a"));
        b.write(tmp.resolve("b"));

        assertArrayEquals(Files.readAllBytes(tmp.resolve("a")), Files.readAllBytes(tmp.resolve("b")));
    }

    @Test
    void detectsCorruption() throws IOException {
        Index index = new Index();
        index.put("a", id(1));
        Path file = tmp.resolve("index");
        index.write(file);
        byte[] bytes = Files.readAllBytes(file);
        bytes[12] ^= 1;
        Files.write(file, bytes);

        assertThrows(CorruptObjectException.class, () -> Index.read(file));
    }

    @Test
    void rejectsInvalidPaths() {
        Index index = new Index();
        for (String bad : List.of("", "/a", "a/", "a//b", "../a", "a/./b", ".ginger/HEAD", "a\\b")) {
            assertThrows(IllegalArgumentException.class, () -> index.put(bad, id(1)), bad);
        }
    }

    @Test
    void fileReplacesDirectoryAndViceVersa() {
        Index index = new Index();
        index.put("a/b", id(1));
        index.put("a/c", id(2));
        index.put("a", id(3));
        assertEquals(List.of("a"), List.copyOf(index.entries().keySet()));

        index.put("a/d", id(4));
        assertEquals(List.of("a/d"), List.copyOf(index.entries().keySet()));
    }
}
