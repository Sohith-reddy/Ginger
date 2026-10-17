package dev.ginger.refs;

import dev.ginger.objects.CorruptObjectException;
import dev.ginger.objects.ObjectId;
import dev.ginger.util.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class Refs {

    private static final String BRANCH_PREFIX = "ref: refs/heads/";
    private static final Pattern BRANCH_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final Path headFile;
    private final Path headsDir;

    public Refs(Path headFile, Path headsDir) {
        this.headFile = headFile;
        this.headsDir = headsDir;
    }

    public static void validateBranchName(String name) {
        if (name == null || !BRANCH_NAME.matcher(name).matches() || name.contains("..")
                || name.endsWith(".") || name.endsWith(".lock") || name.equals("HEAD")) {
            throw new IllegalArgumentException("invalid branch name: " + name);
        }
    }

    public Head head() throws IOException {
        String text = Files.readString(headFile, StandardCharsets.UTF_8);
        if (!text.endsWith("\n")) {
            throw new CorruptObjectException("HEAD is malformed");
        }
        String value = text.substring(0, text.length() - 1);
        try {
            if (value.startsWith(BRANCH_PREFIX)) {
                String branch = value.substring(BRANCH_PREFIX.length());
                validateBranchName(branch);
                return Head.onBranch(branch);
            }
            return Head.detachedAt(ObjectId.of(value));
        } catch (IllegalArgumentException e) {
            throw new CorruptObjectException("HEAD is malformed: " + e.getMessage());
        }
    }

    public Optional<ObjectId> headCommit() throws IOException {
        Head head = head();
        return head.isDetached() ? Optional.of(head.detached()) : branch(head.branch());
    }

    public void setHead(Head head) throws IOException {
        String value = head.isDetached() ? head.detached().hex() : BRANCH_PREFIX + head.branch();
        AtomicFiles.write(headFile, (value + "\n").getBytes(StandardCharsets.UTF_8));
    }

    public Optional<ObjectId> branch(String name) throws IOException {
        validateBranchName(name);
        Path file = headsDir.resolve(name);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        try {
            if (!text.endsWith("\n")) {
                throw new IllegalArgumentException("missing newline");
            }
            return Optional.of(ObjectId.of(text.substring(0, text.length() - 1)));
        } catch (IllegalArgumentException e) {
            throw new CorruptObjectException("branch '" + name + "' is malformed: " + e.getMessage());
        }
    }

    public void updateBranch(String name, ObjectId id) throws IOException {
        validateBranchName(name);
        AtomicFiles.write(headsDir.resolve(name), (id.hex() + "\n").getBytes(StandardCharsets.UTF_8));
    }

    public void updateHead(ObjectId id) throws IOException {
        Head head = head();
        if (head.isDetached()) {
            setHead(Head.detachedAt(id));
        } else {
            updateBranch(head.branch(), id);
        }
    }

    public List<String> branches() throws IOException {
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(headsDir)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (Files.isRegularFile(file) && BRANCH_NAME.matcher(name).matches() && !name.startsWith("tmp-")) {
                    names.add(name);
                }
            }
        }
        names.sort(null);
        return names;
    }
}
