package dev.ginger.diff;

import dev.ginger.objects.ObjectId;
import dev.ginger.objects.ObjectStore;
import dev.ginger.objects.ObjectType;
import dev.ginger.objects.Snapshots;
import dev.ginger.repository.Repository;
import dev.ginger.worktree.WorkTree;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

public final class RepositoryDiff {

    private RepositoryDiff() {
    }

    public static String workTreeAgainstIndex(Repository repo) throws IOException {
        ObjectStore store = repo.objects();
        WorkTree files = repo.files();
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, ObjectId> entry : repo.readIndex().entries().entrySet()) {
            String path = entry.getKey();
            ObjectId current = files.hash(path);
            if (current == null) {
                out.append(UnifiedDiff.format(path, blob(store, entry.getValue()), null));
            } else if (!current.equals(entry.getValue())) {
                out.append(UnifiedDiff.format(path, blob(store, entry.getValue()), files.read(path)));
            }
        }
        return out.toString();
    }

    public static String indexAgainstHead(Repository repo) throws IOException {
        ObjectStore store = repo.objects();
        Optional<ObjectId> headCommit = repo.refs().headCommit();
        SortedMap<String, ObjectId> head = headCommit.isPresent()
                ? Snapshots.readCommitTree(store, headCommit.get())
                : new TreeMap<>();
        SortedMap<String, ObjectId> index = repo.readIndex().entries();

        TreeSet<String> paths = new TreeSet<>(head.keySet());
        paths.addAll(index.keySet());
        StringBuilder out = new StringBuilder();
        for (String path : paths) {
            ObjectId before = head.get(path);
            ObjectId after = index.get(path);
            if (before == null || !before.equals(after)) {
                out.append(UnifiedDiff.format(path, blob(store, before), blob(store, after)));
            }
        }
        return out.toString();
    }

    private static byte[] blob(ObjectStore store, ObjectId id) throws IOException {
        return id == null ? null : store.read(id, ObjectType.BLOB).content();
    }
}
