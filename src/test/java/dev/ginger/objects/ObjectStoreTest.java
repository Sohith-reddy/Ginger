package dev.ginger.objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectStoreTest {

    @TempDir
    Path objectsDir;

    private ObjectStore store;

    @BeforeEach
    void setUp() {
        store = new ObjectStore(objectsDir);
    }

    @Test
    void emptyPayloadRoundTrips() throws IOException {
        assertRoundTrip(new byte[0]);
    }

    @Test
    void textPayloadRoundTrips() throws IOException {
        assertRoundTrip("Hello, Ginger!\r\nline two\n".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void binaryPayloadRoundTrips() throws IOException {
        byte[] bytes = new byte[512];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        assertRoundTrip(bytes);
    }

    @Test
    void storesUnderTwoLevelPath() throws IOException {
        ObjectId id = store.write(StoredObject.blob("x".getBytes(StandardCharsets.UTF_8)));

        Path expected = objectsDir.resolve(id.hex().substring(0, 2)).resolve(id.hex().substring(2));
        assertEquals(expected, store.pathFor(id));
        assertTrue(Files.isRegularFile(expected));
    }

    @Test
    void identicalContentIsStoredOnce() throws IOException {
        StoredObject object = StoredObject.blob("same".getBytes(StandardCharsets.UTF_8));
        ObjectId first = store.write(object);
        Path path = store.pathFor(first);
        FileTime written = FileTime.fromMillis(0);
        Files.setLastModifiedTime(path, written);

        ObjectId second = store.write(object);

        assertEquals(first, second);
        assertEquals(written, Files.getLastModifiedTime(path));
        assertEquals(List.of(path), allFiles());
    }

    @Test
    void corruptContentIsDetected() throws IOException {
        ObjectId id = store.write(StoredObject.blob("original".getBytes(StandardCharsets.UTF_8)));
        Files.write(store.pathFor(id), "blob 8\0tampered".getBytes(StandardCharsets.US_ASCII));

        CorruptObjectException e = assertThrows(CorruptObjectException.class, () -> store.read(id));
        assertTrue(e.getMessage().contains(id.hex()));
    }

    @Test
    void malformedObjectWithMatchingHashIsDetected() throws IOException {
        byte[] garbage = "no header here".getBytes(StandardCharsets.US_ASCII);
        ObjectId id = HashUtil.sha256(garbage);
        Files.createDirectories(store.pathFor(id).getParent());
        Files.write(store.pathFor(id), garbage);

        assertThrows(CorruptObjectException.class, () -> store.read(id));
    }

    @Test
    void corruptObjectIsRepairedOnWrite() throws IOException {
        StoredObject object = StoredObject.blob("data".getBytes(StandardCharsets.UTF_8));
        ObjectId id = store.write(object);
        Files.write(store.pathFor(id), new byte[]{1, 2, 3});

        assertEquals(id, store.write(object));
        assertEquals(object, store.read(id));
    }

    @Test
    void missingObjectIsReported() {
        ObjectId id = HashUtil.sha256(new byte[]{42});

        assertFalse(store.contains(id));
        assertThrows(ObjectNotFoundException.class, () -> store.read(id));
    }

    @Test
    void writeLeavesNoTemporaryFiles() throws IOException {
        store.write(StoredObject.blob(new byte[]{1}));
        store.write(StoredObject.blob(new byte[]{2}));

        assertTrue(allFiles().stream().noneMatch(p -> p.getFileName().toString().startsWith("tmp-")));
        assertEquals(2, allFiles().size());
    }

    private void assertRoundTrip(byte[] bytes) throws IOException {
        ObjectId id = store.write(StoredObject.blob(bytes));

        StoredObject read = store.read(id);

        assertEquals(ObjectType.BLOB, read.type());
        assertArrayEquals(bytes, read.content());
        assertEquals(HashUtil.hash(StoredObject.blob(bytes)), id);
    }

    private List<Path> allFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(objectsDir)) {
            return walk.filter(Files::isRegularFile).toList();
        }
    }
}
