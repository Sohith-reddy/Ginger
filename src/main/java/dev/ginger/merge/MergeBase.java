package dev.ginger.merge;

import dev.ginger.objects.Commit;
import dev.ginger.objects.ObjectId;
import dev.ginger.objects.ObjectStore;
import dev.ginger.objects.Snapshots;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MergeBase {

    private MergeBase() {
    }

    public static Optional<ObjectId> find(ObjectStore store, ObjectId a, ObjectId b) throws IOException {
        Map<ObjectId, Commit> commits = new HashMap<>();
        Set<ObjectId> fromA = ancestors(store, a, commits);
        Set<ObjectId> fromB = ancestors(store, b, commits);
        Set<ObjectId> common = new HashSet<>(fromA);
        common.retainAll(fromB);

        Set<ObjectId> best = new HashSet<>(common);
        for (ObjectId id : common) {
            commits.get(id).parents().forEach(best::remove);
        }
        return best.stream().max(Comparator
                .comparing((ObjectId id) -> commits.get(id).timestamp())
                .thenComparing(ObjectId::hex));
    }

    public static boolean isAncestor(ObjectStore store, ObjectId ancestor, ObjectId of) throws IOException {
        return ancestors(store, of, new HashMap<>()).contains(ancestor);
    }

    private static Set<ObjectId> ancestors(ObjectStore store, ObjectId start, Map<ObjectId, Commit> commits)
            throws IOException {
        Set<ObjectId> seen = new HashSet<>();
        Deque<ObjectId> pending = new ArrayDeque<>();
        pending.push(start);
        while (!pending.isEmpty()) {
            ObjectId id = pending.pop();
            if (!seen.add(id)) {
                continue;
            }
            Commit commit = commits.get(id);
            if (commit == null) {
                commit = Snapshots.readCommit(store, id);
                commits.put(id, commit);
            }
            commit.parents().forEach(pending::push);
        }
        return seen;
    }
}
