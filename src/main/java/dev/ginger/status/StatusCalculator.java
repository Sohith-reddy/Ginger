package dev.ginger.status;

import dev.ginger.merge.MergeState;
import dev.ginger.objects.ObjectId;
import dev.ginger.objects.Snapshots;
import dev.ginger.refs.Refs;
import dev.ginger.repository.Repository;
import dev.ginger.worktree.IgnoreRules;
import dev.ginger.worktree.WorkTree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

public final class StatusCalculator {

    private StatusCalculator() {
    }

    public static Status compute(Repository repo) throws IOException {
        Refs refs = repo.refs();
        Optional<ObjectId> headCommit = refs.headCommit();
        SortedMap<String, ObjectId> head = headCommit.isPresent()
                ? Snapshots.readCommitTree(repo.objects(), headCommit.get())
                : new TreeMap<>();
        SortedMap<String, ObjectId> index = repo.readIndex().entries();
        WorkTree files = repo.files();
        Optional<MergeState> merge = MergeState.read(repo);
        List<String> conflicts = merge.map(MergeState::conflicts).orElse(List.of());

        List<String> stagedAdded = new ArrayList<>();
        List<String> stagedModified = new ArrayList<>();
        List<String> stagedDeleted = new ArrayList<>();
        for (Map.Entry<String, ObjectId> entry : index.entrySet()) {
            ObjectId committed = head.get(entry.getKey());
            if (committed == null) {
                stagedAdded.add(entry.getKey());
            } else if (!committed.equals(entry.getValue())) {
                stagedModified.add(entry.getKey());
            }
        }
        head.keySet().stream().filter(p -> !index.containsKey(p)).forEach(stagedDeleted::add);

        List<String> modified = new ArrayList<>();
        List<String> deleted = new ArrayList<>();
        for (Map.Entry<String, ObjectId> entry : index.entrySet()) {
            if (conflicts.contains(entry.getKey())) {
                continue;
            }
            ObjectId current = files.hash(entry.getKey());
            if (current == null) {
                deleted.add(entry.getKey());
            } else if (!current.equals(entry.getValue())) {
                modified.add(entry.getKey());
            }
        }

        IgnoreRules ignore = files.ignoreRules();
        List<String> untracked = files.listFiles("").keySet().stream()
                .filter(p -> !index.containsKey(p) && !ignore.isIgnored(p) && !conflicts.contains(p))
                .toList();

        return new Status(refs.head(), headCommit.isPresent(),
                stagedAdded, stagedModified, stagedDeleted, modified, deleted, untracked,
                conflicts, merge.map(MergeState::branch).orElse(null));
    }
}
