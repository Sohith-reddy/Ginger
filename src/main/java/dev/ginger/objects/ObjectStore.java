package dev.ginger.objects;

import dev.ginger.util.AtomicFiles;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ObjectStore {

    private final Path objectsDir;

    public ObjectStore(Path objectsDir) {
        this.objectsDir = objectsDir;
    }

    public Path pathFor(ObjectId id) {
        return objectsDir.resolve(id.directory()).resolve(id.fileName());
    }

    public boolean contains(ObjectId id) {
        return Files.isRegularFile(pathFor(id));
    }

    public ObjectId write(StoredObject object) throws IOException {
        byte[] encoded = object.encode();
        ObjectId id = HashUtil.sha256(encoded);
        Path target = pathFor(id);

        if (contains(id) && isValid(id)) {
            return id;
        }

        AtomicFiles.write(target, encoded);
        return id;
    }

    public ObjectId resolve(String prefix) throws IOException {
        if (!prefix.matches("[0-9a-f]{4," + ObjectId.HEX_LENGTH + "}")) {
            throw new IOException("invalid object id: " + prefix);
        }
        if (prefix.length() == ObjectId.HEX_LENGTH) {
            ObjectId id = ObjectId.of(prefix);
            if (!contains(id)) {
                throw new ObjectNotFoundException(id);
            }
            return id;
        }

        Path dir = objectsDir.resolve(prefix.substring(0, 2));
        List<ObjectId> matches = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
                for (Path file : files) {
                    String hex = prefix.substring(0, 2) + file.getFileName();
                    if (hex.startsWith(prefix) && hex.length() == ObjectId.HEX_LENGTH && Files.isRegularFile(file)) {
                        matches.add(ObjectId.of(hex));
                    }
                }
            }
        }
        if (matches.isEmpty()) {
            throw new IOException("unknown object id: " + prefix);
        }
        if (matches.size() > 1) {
            throw new IOException("ambiguous object id: " + prefix);
        }
        return matches.get(0);
    }

    public StoredObject read(ObjectId id, ObjectType expected) throws IOException {
        StoredObject object = read(id);
        if (object.type() != expected) {
            throw new IOException("object " + id + " is a " + object.type().label() + ", not a " + expected.label());
        }
        return object;
    }

    public StoredObject read(ObjectId id) throws IOException {
        Path path = pathFor(id);
        if (!Files.isRegularFile(path)) {
            throw new ObjectNotFoundException(id);
        }

        byte[] data = Files.readAllBytes(path);
        ObjectId actual = HashUtil.sha256(data);
        if (!actual.equals(id)) {
            throw new CorruptObjectException("object " + id + " is corrupt: content hashes to " + actual + " (" + path + ")");
        }

        try {
            return StoredObject.decode(data);
        } catch (CorruptObjectException e) {
            throw new CorruptObjectException("object " + id + " is corrupt: " + e.getMessage() + " (" + path + ")");
        }
    }

    private boolean isValid(ObjectId id) {
        try {
            read(id);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
