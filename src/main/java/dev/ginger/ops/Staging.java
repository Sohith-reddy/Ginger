package dev.ginger.ops;

import dev.ginger.index.Index;
import dev.ginger.merge.MergeState;
import dev.ginger.objects.ObjectId;
import dev.ginger.objects.ObjectStore;
import dev.ginger.objects.StoredObject;
import dev.ginger.repository.Repository;
import dev.ginger.worktree.IgnoreRules;
import dev.ginger.worktree.WorkTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

public final class Staging {

    private Staging() {
    }

    public static void add(Repository repo, Path cwd, List<String> pathspecs) throws IOException {
        WorkTree files = repo.files();
        Index index = repo.readIndex();
        IgnoreRules ignore = files.ignoreRules();
        Optional<MergeState> merge = MergeState.read(repo);
        List<String> conflicts = merge.map(MergeState::conflicts).orElse(List.of());
        Set<String> toStage = new TreeSet<>();
        Set<String> toRemove = new TreeSet<>();

        for (String spec : pathspecs) {
            String path = files.relativize(cwd, spec);
            Path abs = files.resolve(path);
            if (Files.isSymbolicLink(abs)) {
                throw new IOException("refusing to add symbolic link '" + spec + "'");
            } else if (Files.isRegularFile(abs, LinkOption.NOFOLLOW_LINKS)) {
                toStage.add(path);
            } else if (Files.isDirectory(abs, LinkOption.NOFOLLOW_LINKS)) {
                SortedMap<String, Path> found = files.listFiles(path);
                found.keySet().stream()
                        .filter(p -> !ignore.isIgnored(p) || index.get(p) != null)
                        .forEach(toStage::add);
                index.pathsUnder(path).stream().filter(p -> !found.containsKey(p)).forEach(toRemove::add);
            } else if (Files.exists(abs, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("'" + spec + "' is not a regular file");
            } else {
                List<String> tracked = new ArrayList<>(index.pathsUnder(path));
                conflicts.stream().filter(p -> p.equals(path) || p.startsWith(path + "/")).forEach(tracked::add);
                if (tracked.isEmpty()) {
                    throw new IOException("pathspec '" + spec + "' did not match any files");
                }
                toRemove.addAll(tracked);
            }
        }

        ObjectStore store = repo.objects();
        SortedMap<String, ObjectId> staged = new TreeMap<>();
        for (String path : toStage) {
            staged.put(path, store.write(StoredObject.blob(files.read(path))));
        }

        toRemove.forEach(index::remove);
        try {
            staged.forEach(index::put);
        } catch (IllegalArgumentException e) {
            throw new IOException("cannot stage file: " + e.getMessage(), e);
        }
        repo.writeIndex(index);

        if (merge.isPresent()) {
            Set<String> touched = new TreeSet<>(toStage);
            touched.addAll(toRemove);
            merge.get().resolve(touched).write(repo);
        }
    }
}
