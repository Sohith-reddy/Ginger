package dev.ginger.worktree;

import dev.ginger.objects.HashUtil;
import dev.ginger.objects.ObjectId;
import dev.ginger.objects.StoredObject;
import dev.ginger.repository.Repository;
import dev.ginger.util.AtomicFiles;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.SortedMap;
import java.util.TreeMap;

public final class WorkTree {

    private final Path root;

    public WorkTree(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public String relativize(Path cwd, String arg) throws IOException {
        Path abs = cwd.resolve(arg).toAbsolutePath().normalize();
        if (!abs.startsWith(root)) {
            throw new IOException("'" + arg + "' is outside the repository");
        }
        Path rel = root.relativize(abs);
        for (Path part : rel) {
            if (part.toString().equals(Repository.GINGER_DIR)) {
                throw new IOException("'" + arg + "' is inside the repository metadata");
            }
        }
        return toRepoPath(rel);
    }

    private static String toRepoPath(Path rel) {
        if (rel.toString().isEmpty()) {
            return "";
        }
        StringBuilder path = new StringBuilder();
        for (Path part : rel) {
            if (!path.isEmpty()) {
                path.append('/');
            }
            path.append(part);
        }
        return path.toString();
    }

    public Path resolve(String path) {
        Path result = root;
        if (!path.isEmpty()) {
            for (String part : path.split("/")) {
                result = result.resolve(part);
            }
        }
        return result;
    }

    public boolean isFile(String path) {
        return Files.isRegularFile(resolve(path), LinkOption.NOFOLLOW_LINKS);
    }

    public byte[] read(String path) throws IOException {
        Path file = resolve(path);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("'" + path + "' is not a regular file");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IOException("cannot read '" + path + "': " + e.getMessage(), e);
        }
    }

    public ObjectId hash(String path) throws IOException {
        if (!isFile(path)) {
            return null;
        }
        return HashUtil.hash(StoredObject.blob(read(path)));
    }

    public SortedMap<String, Path> listFiles(String prefix) throws IOException {
        SortedMap<String, Path> files = new TreeMap<>();
        Path start = resolve(prefix);
        if (!Files.isDirectory(start, LinkOption.NOFOLLOW_LINKS)) {
            return files;
        }
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return dir.getFileName() != null && dir.getFileName().toString().equals(Repository.GINGER_DIR)
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isRegularFile() && !file.getFileName().toString().equals(Repository.GINGER_DIR)) {
                    files.put(toRepoPath(root.relativize(file)), file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    public void write(String path, byte[] data) throws IOException {
        Path target = resolve(path);
        checkNoLinks(target.getParent());
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("cannot write '" + path + "': a directory is in the way");
        }
        Path parent = target.getParent();
        for (Path dir = root; !dir.equals(parent); ) {
            dir = dir.resolve(parent.getName(dir.getNameCount()));
            if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("cannot write '" + path + "': " + root.relativize(dir) + " is not a directory");
            }
        }
        if (Files.isSymbolicLink(target)) {
            Files.delete(target);
        }
        AtomicFiles.write(target, data);
    }

    public void delete(String path) throws IOException {
        Path target = resolve(path);
        checkNoLinks(target.getParent());
        Files.deleteIfExists(target);
        Path dir = target.getParent();
        while (!dir.equals(root) && isEmptyDirectory(dir)) {
            Files.delete(dir);
            dir = dir.getParent();
        }
    }

    private void checkNoLinks(Path dir) throws IOException {
        for (Path current = dir; !current.equals(root); current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("refusing to write through symbolic link " + root.relativize(current));
            }
        }
    }

    private static boolean isEmptyDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (var entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        }
    }
}
