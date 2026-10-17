package dev.ginger.objects;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public final class Snapshots {

    private Snapshots() {
    }

    public static ObjectId writeTree(ObjectStore store, Map<String, ObjectId> files) throws IOException {
        SortedMap<String, ObjectId> blobs = new TreeMap<>();
        SortedMap<String, Map<String, ObjectId>> dirs = new TreeMap<>();
        for (Map.Entry<String, ObjectId> file : files.entrySet()) {
            String path = file.getKey();
            int slash = path.indexOf('/');
            if (slash < 0) {
                blobs.put(path, file.getValue());
            } else {
                dirs.computeIfAbsent(path.substring(0, slash), k -> new TreeMap<>())
                        .put(path.substring(slash + 1), file.getValue());
            }
        }

        List<TreeEntry> entries = new ArrayList<>();
        for (Map.Entry<String, ObjectId> blob : blobs.entrySet()) {
            entries.add(new TreeEntry(blob.getKey(), ObjectType.BLOB, blob.getValue()));
        }
        for (Map.Entry<String, Map<String, ObjectId>> dir : dirs.entrySet()) {
            entries.add(new TreeEntry(dir.getKey(), ObjectType.TREE, writeTree(store, dir.getValue())));
        }
        return store.write(new Tree(entries).toObject());
    }

    public static SortedMap<String, ObjectId> readTree(ObjectStore store, ObjectId treeId) throws IOException {
        SortedMap<String, ObjectId> files = new TreeMap<>();
        collect(store, treeId, "", files, 0);
        return files;
    }

    public static Commit readCommit(ObjectStore store, ObjectId commitId) throws IOException {
        return Commit.fromObject(store.read(commitId, ObjectType.COMMIT));
    }

    public static SortedMap<String, ObjectId> readCommitTree(ObjectStore store, ObjectId commitId) throws IOException {
        return readTree(store, readCommit(store, commitId).tree());
    }

    private static void collect(ObjectStore store, ObjectId treeId, String prefix,
                                SortedMap<String, ObjectId> files, int depth) throws IOException {
        if (depth > 256) {
            throw new CorruptObjectException("tree nesting is too deep");
        }
        Tree tree = Tree.fromObject(store.read(treeId, ObjectType.TREE));
        for (TreeEntry entry : tree.entries()) {
            String path = prefix + entry.name();
            if (entry.type() == ObjectType.TREE) {
                collect(store, entry.id(), path + "/", files, depth + 1);
            } else {
                files.put(path, entry.id());
            }
        }
    }
}
