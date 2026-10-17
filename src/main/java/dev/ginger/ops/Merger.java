package dev.ginger.ops;

import dev.ginger.diff.UnifiedDiff;
import dev.ginger.index.Index;
import dev.ginger.merge.MergeBase;
import dev.ginger.merge.MergeState;
import dev.ginger.merge.TextMerge;
import dev.ginger.objects.Commit;
import dev.ginger.objects.ObjectId;
import dev.ginger.objects.ObjectStore;
import dev.ginger.objects.ObjectType;
import dev.ginger.objects.Snapshots;
import dev.ginger.objects.StoredObject;
import dev.ginger.refs.Head;
import dev.ginger.refs.Refs;
import dev.ginger.repository.Repository;
import dev.ginger.status.Status;
import dev.ginger.status.StatusCalculator;
import dev.ginger.worktree.WorkTree;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

public final class Merger {

    public enum Outcome { UP_TO_DATE, FAST_FORWARD, MERGED, CONFLICTED }

    public record Conflict(String path, String kind) {
    }

    public record Result(Outcome outcome, ObjectId commit, List<Conflict> conflicts) {

        public Result {
            conflicts = List.copyOf(conflicts);
        }
    }

    private Merger() {
    }

    public static Result merge(Repository repo, String branch, String author, Instant time) throws IOException {
        if (MergeState.read(repo).isPresent()) {
            throw new IOException("a merge is already in progress; commit it or run 'ginger merge --abort'");
        }
        Refs refs = repo.refs();
        ObjectStore store = repo.objects();
        Head head = refs.head();
        ObjectId ours = refs.headCommit()
                .orElseThrow(() -> new IOException("cannot merge: the current branch has no commits yet"));
        ObjectId theirs = refs.branch(branch).orElseThrow(() -> new IOException("invalid reference: " + branch));

        Status status = StatusCalculator.compute(repo);
        if (status.hasStagedChanges() || status.hasUnstagedChanges()) {
            throw new IOException("local changes would be overwritten by merge; commit or restore them first");
        }

        ObjectId base = MergeBase.find(store, ours, theirs)
                .orElseThrow(() -> new IOException("refusing to merge unrelated histories"));
        if (base.equals(theirs)) {
            return new Result(Outcome.UP_TO_DATE, ours, List.of());
        }
        if (base.equals(ours)) {
            Checkout.moveTo(repo, theirs, head, false);
            refs.updateHead(theirs);
            return new Result(Outcome.FAST_FORWARD, theirs, List.of());
        }

        SortedMap<String, ObjectId> baseTree = Snapshots.readCommitTree(store, base);
        SortedMap<String, ObjectId> oursTree = Snapshots.readCommitTree(store, ours);
        SortedMap<String, ObjectId> theirsTree = Snapshots.readCommitTree(store, theirs);
        TreeSet<String> paths = new TreeSet<>(baseTree.keySet());
        paths.addAll(oursTree.keySet());
        paths.addAll(theirsTree.keySet());

        SortedMap<String, ObjectId> merged = new TreeMap<>();
        Map<String, byte[]> conflictFiles = new TreeMap<>();
        List<Conflict> conflicts = new ArrayList<>();
        for (String path : paths) {
            ObjectId b = baseTree.get(path);
            ObjectId o = oursTree.get(path);
            ObjectId t = theirsTree.get(path);
            if (Objects.equals(o, t) || Objects.equals(b, t)) {
                putIfPresent(merged, path, o);
            } else if (Objects.equals(b, o)) {
                putIfPresent(merged, path, t);
            } else if (o == null || t == null) {
                conflicts.add(new Conflict(path, o == null ? "delete/modify" : "modify/delete"));
                putIfPresent(merged, path, o);
                if (o == null) {
                    conflictFiles.put(path, blob(store, t));
                }
            } else {
                byte[] baseBytes = b == null ? new byte[0] : blob(store, b);
                byte[] oursBytes = blob(store, o);
                byte[] theirsBytes = blob(store, t);
                String kind = b == null ? "add/add" : "content";
                if (!isText(baseBytes) || !isText(oursBytes) || !isText(theirsBytes)) {
                    conflicts.add(new Conflict(path, "binary"));
                    merged.put(path, o);
                    continue;
                }
                var result = TextMerge.merge(text(baseBytes), text(oursBytes), text(theirsBytes), "HEAD", branch);
                if (result.isEmpty()) {
                    conflicts.add(new Conflict(path, "too large"));
                    merged.put(path, o);
                } else if (result.get().conflicted()) {
                    conflicts.add(new Conflict(path, kind));
                    merged.put(path, o);
                    conflictFiles.put(path, result.get().text().getBytes(StandardCharsets.UTF_8));
                } else {
                    byte[] bytes = result.get().text().getBytes(StandardCharsets.UTF_8);
                    merged.put(path, store.write(StoredObject.blob(bytes)));
                }
            }
        }

        WorkTree files = repo.files();
        TreeSet<String> deletes = new TreeSet<>();
        TreeSet<String> writes = new TreeSet<>();
        for (String path : paths) {
            ObjectId o = oursTree.get(path);
            ObjectId m = merged.get(path);
            if (conflictFiles.containsKey(path) || (m != null && !m.equals(o))) {
                writes.add(path);
            } else if (m == null && o != null) {
                deletes.add(path);
            }
        }
        for (String path : writes) {
            if (oursTree.get(path) == null && Files.exists(files.resolve(path), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("untracked file '" + path + "' would be overwritten by merge");
            }
            int slash = path.indexOf('/');
            while (slash >= 0) {
                String ancestor = path.substring(0, slash);
                Path dir = files.resolve(ancestor);
                if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)
                        && !deletes.contains(ancestor)) {
                    throw new IOException("'" + ancestor + "' is in the way of '" + path + "'");
                }
                slash = path.indexOf('/', slash + 1);
            }
        }

        Index index = repo.readIndex();
        for (String path : deletes.descendingSet()) {
            files.delete(path);
            index.remove(path);
        }
        for (String path : writes) {
            byte[] content = conflictFiles.containsKey(path) ? conflictFiles.get(path) : blob(store, merged.get(path));
            files.write(path, content);
            ObjectId staged = merged.get(path);
            if (staged != null) {
                index.put(path, staged);
            }
        }
        repo.writeIndex(index);

        if (!conflicts.isEmpty()) {
            new MergeState(theirs, branch, conflicts.stream().map(Conflict::path).toList()).write(repo);
            return new Result(Outcome.CONFLICTED, null, conflicts);
        }

        ObjectId tree = Snapshots.writeTree(store, index.entries());
        Commit commit = new Commit(tree, List.of(ours, theirs), author, time, "Merge branch '" + branch + "'");
        ObjectId id = store.write(commit.toObject());
        refs.updateHead(id);
        return new Result(Outcome.MERGED, id, List.of());
    }

    public static void abort(Repository repo) throws IOException {
        MergeState state = MergeState.read(repo)
                .orElseThrow(() -> new IOException("there is no merge to abort"));
        ObjectStore store = repo.objects();
        ObjectId head = repo.refs().headCommit().orElseThrow();
        SortedMap<String, ObjectId> headTree = Snapshots.readCommitTree(store, head);
        Index index = repo.readIndex();
        WorkTree files = repo.files();

        TreeSet<String> paths = new TreeSet<>(headTree.keySet());
        paths.addAll(index.entries().keySet());
        paths.addAll(state.conflicts());
        for (String path : paths.descendingSet()) {
            ObjectId wanted = headTree.get(path);
            if (wanted == null) {
                files.delete(path);
            } else if (!wanted.equals(files.hash(path))) {
                files.write(path, blob(store, wanted));
            }
        }
        repo.writeIndex(new Index(headTree));
        MergeState.clear(repo);
    }

    private static void putIfPresent(Map<String, ObjectId> map, String path, ObjectId id) {
        if (id != null) {
            map.put(path, id);
        }
    }

    private static byte[] blob(ObjectStore store, ObjectId id) throws IOException {
        return store.read(id, ObjectType.BLOB).content();
    }

    private static boolean isText(byte[] bytes) {
        return !UnifiedDiff.isBinary(bytes) && bytes.length <= UnifiedDiff.MAX_TEXT_BYTES;
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
