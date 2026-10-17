package dev.ginger.objects;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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

        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "tmp-", null);
        try {
            Files.write(temp, encoded);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return id;
    }

    public StoredObject read(ObjectId id) throws IOException {
        Path path = pathFor(id);
        if (!Files.isRegularFile(path)) {
            throw new ObjectNotFoundException(id);
        }

        byte[] data = Files.readAllBytes(path);
        ObjectId actual = HashUtil.sha256(data);
        if (!actual.equals(id)) {
            throw new CorruptObjectException("object " + id + " is corrupt: content hashes to " + actual);
        }

        try {
            return StoredObject.decode(data);
        } catch (CorruptObjectException e) {
            throw new CorruptObjectException("object " + id + " is corrupt: " + e.getMessage());
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
