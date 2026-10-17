package dev.ginger.index;

import dev.ginger.objects.CorruptObjectException;
import dev.ginger.objects.HashUtil;
import dev.ginger.objects.ObjectId;
import dev.ginger.objects.TreeEntry;
import dev.ginger.util.AtomicFiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public final class Index {

    public static final int VERSION = 1;

    private static final byte[] MAGIC = "GIDX".getBytes(StandardCharsets.US_ASCII);
    private static final int ID_BYTES = ObjectId.HEX_LENGTH / 2;

    private final SortedMap<String, ObjectId> entries = new TreeMap<>();

    public Index() {
    }

    public Index(SortedMap<String, ObjectId> entries) {
        entries.forEach(this::put);
    }

    public SortedMap<String, ObjectId> entries() {
        return Collections.unmodifiableSortedMap(entries);
    }

    public ObjectId get(String path) {
        return entries.get(path);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void put(String path, ObjectId id) {
        validatePath(path);
        int slash = path.indexOf('/');
        while (slash >= 0) {
            entries.remove(path.substring(0, slash));
            slash = path.indexOf('/', slash + 1);
        }
        pathsUnder(path).forEach(entries::remove);
        entries.put(path, id);
    }

    public void remove(String path) {
        entries.remove(path);
    }

    public List<String> pathsUnder(String prefix) {
        if (prefix.isEmpty()) {
            return List.copyOf(entries.keySet());
        }
        return entries.keySet().stream()
                .filter(p -> p.equals(prefix) || p.startsWith(prefix + "/"))
                .toList();
    }

    public static void validatePath(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("empty path");
        }
        for (String part : path.split("/", -1)) {
            TreeEntry.validateName(part);
        }
    }

    public static Index read(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        Index index = new Index();
        if (data.length == 0) {
            return index;
        }
        if (data.length < MAGIC.length + 8 + ID_BYTES) {
            throw new CorruptObjectException("index is truncated");
        }

        byte[] body = Arrays.copyOf(data, data.length - ID_BYTES);
        byte[] checksum = Arrays.copyOfRange(data, data.length - ID_BYTES, data.length);
        if (!Arrays.equals(HashUtil.sha256(body).toBytes(), checksum)) {
            throw new CorruptObjectException("index checksum mismatch");
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
            if (!Arrays.equals(in.readNBytes(MAGIC.length), MAGIC)) {
                throw new CorruptObjectException("index has an unknown signature");
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new CorruptObjectException("unsupported index version: " + version);
            }
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int length = in.readInt();
                if (length <= 0 || length > in.available()) {
                    throw new CorruptObjectException("index entry has an invalid length");
                }
                String path = new String(in.readNBytes(length), StandardCharsets.UTF_8);
                ObjectId id = ObjectId.fromBytes(in.readNBytes(ID_BYTES));
                index.put(path, id);
            }
            if (in.available() != 0 || index.entries.size() != count) {
                throw new CorruptObjectException("index has unexpected content");
            }
        } catch (IllegalArgumentException e) {
            throw new CorruptObjectException("index is malformed: " + e.getMessage());
        }
        return index;
    }

    public void write(Path file) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.write(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(entries.size());
            for (var entry : entries.entrySet()) {
                byte[] path = entry.getKey().getBytes(StandardCharsets.UTF_8);
                out.writeInt(path.length);
                out.write(path);
                out.write(entry.getValue().toBytes());
            }
        }
        byte[] body = bytes.toByteArray();
        bytes.writeBytes(HashUtil.sha256(body).toBytes());
        AtomicFiles.write(file, bytes.toByteArray());
    }
}
