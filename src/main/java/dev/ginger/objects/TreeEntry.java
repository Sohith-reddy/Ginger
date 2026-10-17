package dev.ginger.objects;

import java.util.Objects;

public record TreeEntry(String name, ObjectType type, ObjectId id) {

    public TreeEntry {
        validateName(name);
        if (type != ObjectType.BLOB && type != ObjectType.TREE) {
            throw new IllegalArgumentException("tree entries must be blobs or trees: " + type);
        }
        Objects.requireNonNull(id, "id");
    }

    public static void validateName(String name) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                || name.equals(".ginger") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("invalid tree entry name: " + name);
        }
    }
}
