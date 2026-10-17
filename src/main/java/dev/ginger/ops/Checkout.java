package dev.ginger.ops;

import dev.ginger.index.Index;
import dev.ginger.objects.ObjectId;
import dev.ginger.objects.ObjectStore;
import dev.ginger.objects.ObjectType;
import dev.ginger.objects.Snapshots;
import dev.ginger.refs.Head;
import dev.ginger.repository.Repository;
import dev.ginger.worktree.WorkTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

public final class Checkout {

    private Checkout() {
    }

    public static void moveTo(Repository repo, ObjectId target, Head newHead, boolean force) throws IOException {
        ObjectStore store = repo.objects();
        WorkTree files = repo.files();
        Optional<ObjectId> current = repo.refs().headCommit();
        SortedMap<String, ObjectId> head = current.isPresent()
                ? Snapshots.readCommitTree(store, current.get())
                : new TreeMap<>();
        SortedMap<String, ObjectId> wanted = Snapshots.readCommitTree(store, target);
        Index index = repo.readIndex();

        TreeSet<String> deletes = new TreeSet<>();
        SortedMap<String, ObjectId> writes = new TreeMap<>();
        TreeSet<String> changed = new TreeSet<>(head.keySet());
        changed.addAll(wanted.keySet());
        for (String path : changed) {
            ObjectId t = wanted.get(path);
            if (Objects.equals(head.get(path), t)) {
                continue;
            }
            if (t == null) {
                deletes.add(path);
            } else {
                writes.put(path, t);
            }
        }

        List<String> conflicts = new ArrayList<>();
        for (String path : changed) {
            ObjectId h = head.get(path);
            ObjectId t = wanted.get(path);
            if (Objects.equals(h, t)) {
                continue;
            }
            ObjectId i = index.get(path);
            State w = state(files, path, deletes);
            boolean alreadyThere = w.matches(t) && (Objects.equals(i, h) || Objects.equals(i, t));
            boolean untouched = Objects.equals(i, h) && w.matches(h);
            if (!force && !alreadyThere && !untouched) {
                conflicts.add(path);
            } else if (force && w.blocking()) {
                conflicts.add(path);
            }
        }
        for (String path : writes.keySet()) {
            String blocker = blockingAncestor(files, path, deletes);
            if (blocker != null) {
                conflicts.add(blocker);
            }
        }
        if (!conflicts.isEmpty()) {
            throw new IOException("local changes would be overwritten:\n  "
                    + String.join("\n  ", new TreeSet<>(conflicts))
                    + "\ncommit or restore them, or use --force to discard them");
        }

        for (ObjectId id : writes.values()) {
            store.read(id, ObjectType.BLOB);
        }

        for (String path : deletes.descendingSet()) {
            files.delete(path);
            index.remove(path);
        }
        for (Map.Entry<String, ObjectId> write : writes.entrySet()) {
            files.write(write.getKey(), store.read(write.getValue(), ObjectType.BLOB).content());
            index.put(write.getKey(), write.getValue());
        }
        repo.writeIndex(index);
        repo.refs().setHead(newHead);
    }

    private record State(ObjectId id, boolean present, boolean blocking) {

        boolean matches(ObjectId expected) {
            return !blocking && (expected == null ? !present : expected.equals(id));
        }
    }

    private static State state(WorkTree files, String path, TreeSet<String> deletes) throws IOException {
        Path file = files.resolve(path);
        if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return new State(files.hash(path), true, false);
        }
        if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
            boolean emptied = deletes.containsAll(files.listFiles(path).keySet());
            return new State(null, !emptied, !emptied);
        }
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return new State(null, true, false);
        }
        return new State(null, false, false);
    }

    private static String blockingAncestor(WorkTree files, String path, TreeSet<String> deletes) {
        int slash = path.indexOf('/');
        while (slash >= 0) {
            String ancestor = path.substring(0, slash);
            Path dir = files.resolve(ancestor);
            if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)
                    && !deletes.contains(ancestor)) {
                return ancestor;
            }
            slash = path.indexOf('/', slash + 1);
        }
        return null;
    }
}
