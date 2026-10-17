package dev.ginger.merge;

import dev.ginger.objects.CorruptObjectException;
import dev.ginger.objects.ObjectId;
import dev.ginger.repository.Repository;
import dev.ginger.util.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public record MergeState(ObjectId mergeHead, String branch, List<String> conflicts) {

    public MergeState {
        conflicts = conflicts.stream().sorted().distinct().toList();
    }

    public static Optional<MergeState> read(Repository repo) throws IOException {
        Path headFile = headFile(repo);
        if (!Files.exists(headFile)) {
            return Optional.empty();
        }
        List<String> head = Files.readAllLines(headFile, StandardCharsets.UTF_8);
        List<String> conflicts = new ArrayList<>();
        Path conflictsFile = conflictsFile(repo);
        if (Files.exists(conflictsFile)) {
            for (String line : Files.readAllLines(conflictsFile, StandardCharsets.UTF_8)) {
                conflicts.add(unescape(line));
            }
        }
        try {
            if (head.size() != 2) {
                throw new IllegalArgumentException("expected a commit id and a branch name");
            }
            return Optional.of(new MergeState(ObjectId.of(head.get(0)), head.get(1), conflicts));
        } catch (IllegalArgumentException e) {
            throw new CorruptObjectException("merge state is malformed: " + e.getMessage() + " (" + headFile
                    + "); run 'ginger merge --abort'");
        }
    }

    public void write(Repository repo) throws IOException {
        StringBuilder text = new StringBuilder();
        conflicts.forEach(path -> text.append(escape(path)).append('\n'));
        AtomicFiles.write(conflictsFile(repo), text.toString().getBytes(StandardCharsets.UTF_8));
        String head = mergeHead.hex() + "\n" + branch + "\n";
        AtomicFiles.write(headFile(repo), head.getBytes(StandardCharsets.UTF_8));
    }

    public MergeState resolve(Collection<String> paths) {
        return new MergeState(mergeHead, branch, conflicts.stream().filter(p -> !paths.contains(p)).toList());
    }

    public static void clear(Repository repo) throws IOException {
        Files.deleteIfExists(headFile(repo));
        Files.deleteIfExists(conflictsFile(repo));
    }

    private static Path headFile(Repository repo) {
        return repo.gingerDir().resolve("MERGE_HEAD");
    }

    private static Path conflictsFile(Repository repo) {
        return repo.gingerDir().resolve("MERGE_CONFLICTS");
    }

    private static String escape(String path) {
        return path.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private static String unescape(String line) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                char next = line.charAt(++i);
                out.append(next == 'n' ? '\n' : next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
