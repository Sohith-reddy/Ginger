package dev.ginger.ops;

import dev.ginger.index.Index;
import dev.ginger.merge.MergeState;
import dev.ginger.objects.Commit;
import dev.ginger.objects.ObjectId;
import dev.ginger.objects.ObjectStore;
import dev.ginger.objects.Snapshots;
import dev.ginger.refs.Head;
import dev.ginger.refs.Refs;
import dev.ginger.repository.Repository;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Committer {

    public record Result(ObjectId id, Commit commit, Head head) {
    }

    private Committer() {
    }

    public static Optional<Result> commit(Repository repo, String message, String author, Instant time)
            throws IOException {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("empty commit message");
        }
        Refs refs = repo.refs();
        Head head = refs.head();
        Optional<ObjectId> parent = refs.headCommit();
        Index index = repo.readIndex();
        ObjectStore store = repo.objects();
        Optional<MergeState> merge = MergeState.read(repo);
        if (merge.isPresent() && !merge.get().conflicts().isEmpty()) {
            throw new IOException("cannot commit with unresolved conflicts:\n  "
                    + String.join("\n  ", merge.get().conflicts())
                    + "\nfix them and run 'ginger add <path>', or run 'ginger merge --abort'");
        }

        if (parent.isEmpty() && index.isEmpty()) {
            return Optional.empty();
        }
        ObjectId tree = Snapshots.writeTree(store, index.entries());
        if (merge.isEmpty() && parent.isPresent() && Snapshots.readCommit(store, parent.get()).tree().equals(tree)) {
            return Optional.empty();
        }

        List<ObjectId> parents = new ArrayList<>();
        parent.ifPresent(parents::add);
        merge.ifPresent(m -> parents.add(m.mergeHead()));
        Commit commit = new Commit(tree, parents, author, time, message);
        ObjectId id = store.write(commit.toObject());
        refs.updateHead(id);
        if (merge.isPresent()) {
            MergeState.clear(repo);
        }
        return Optional.of(new Result(id, commit, head));
    }
}
