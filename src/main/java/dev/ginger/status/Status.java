package dev.ginger.status;

import dev.ginger.refs.Head;

import java.util.List;

public record Status(Head head, boolean hasCommits,
                     List<String> stagedAdded, List<String> stagedModified, List<String> stagedDeleted,
                     List<String> modified, List<String> deleted, List<String> untracked) {

    public Status {
        stagedAdded = List.copyOf(stagedAdded);
        stagedModified = List.copyOf(stagedModified);
        stagedDeleted = List.copyOf(stagedDeleted);
        modified = List.copyOf(modified);
        deleted = List.copyOf(deleted);
        untracked = List.copyOf(untracked);
    }

    public boolean hasStagedChanges() {
        return !stagedAdded.isEmpty() || !stagedModified.isEmpty() || !stagedDeleted.isEmpty();
    }

    public boolean hasUnstagedChanges() {
        return !modified.isEmpty() || !deleted.isEmpty();
    }

    public boolean isClean() {
        return !hasStagedChanges() && !hasUnstagedChanges() && untracked.isEmpty();
    }
}
