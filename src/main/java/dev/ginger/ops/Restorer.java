package dev.ginger.ops;

import dev.ginger.index.Index;
import dev.ginger.objects.ObjectId;
import dev.ginger.objects.ObjectStore;
import dev.ginger.objects.ObjectType;
import dev.ginger.objects.Snapshots;
import dev.ginger.repository.Repository;
import dev.ginger.worktree.WorkTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

public final class Restorer {

    private Restorer() {
    }

    public static void restoreWorkTree(Repository repo, Path cwd, List<String> specs, boolean force)
            throws IOException {
        WorkTree files = repo.files();
        Index index = repo.readIndex();
        ObjectStore store = repo.objects();

        TreeSet<String> paths = new TreeSet<>();
        for (String spec : specs) {
            List<String> tracked = index.pathsUnder(files.relativize(cwd, spec));
            if (tracked.isEmpty()) {
                throw new IOException("pathspec '" + spec + "' did not match any tracked files");
            }
            paths.addAll(tracked);
        }

        List<String> conflicts = new ArrayList<>();
        SortedMap<String, ObjectId> toWrite = new TreeMap<>();
        for (String path : paths) {
            ObjectId staged = index.get(path);
            Path file = files.resolve(path);
            ObjectId current = files.hash(path);
            if (staged.equals(current)) {
                continue;
            }
            boolean present = Files.exists(file, LinkOption.NOFOLLOW_LINKS);
            if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) || (present && !force)) {
                conflicts.add(path);
            }
            store.read(staged, ObjectType.BLOB);
            toWrite.put(path, staged);
        }
        if (!conflicts.isEmpty()) {
            throw new IOException("local changes would be overwritten:\n  " + String.join("\n  ", conflicts)
                    + "\nuse --force to discard them");
        }

        for (var entry : toWrite.entrySet()) {
            files.write(entry.getKey(), store.read(entry.getValue(), ObjectType.BLOB).content());
        }
    }

    public static void unstage(Repository repo, Path cwd, List<String> specs) throws IOException {
        WorkTree files = repo.files();
        Index index = repo.readIndex();
        Optional<ObjectId> headCommit = repo.refs().headCommit();
        SortedMap<String, ObjectId> head = headCommit.isPresent()
                ? Snapshots.readCommitTree(repo.objects(), headCommit.get())
                : new TreeMap<>();

        TreeSet<String> paths = new TreeSet<>();
        for (String spec : specs) {
            String prefix = files.relativize(cwd, spec);
            List<String> matched = new ArrayList<>(index.pathsUnder(prefix));
            new Index(head).pathsUnder(prefix).forEach(matched::add);
            if (matched.isEmpty()) {
                throw new IOException("pathspec '" + spec + "' did not match any tracked files");
            }
            paths.addAll(matched);
        }

        for (String path : paths) {
            ObjectId committed = head.get(path);
            if (committed == null) {
                index.remove(path);
            } else {
                index.put(path, committed);
            }
        }
        repo.writeIndex(index);
    }
}
