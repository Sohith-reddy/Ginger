package dev.ginger.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public record Repository(Path workTree, Path gingerDir) {

    public static final String GINGER_DIR = ".ginger";
    public static final String DEFAULT_BRANCH = "main";
    public static final int FORMAT_VERSION = 1;

    public static Repository init(Path workTree) throws IOException {
        Path root = workTree.toAbsolutePath().normalize();
        Path gingerDir = root.resolve(GINGER_DIR);
        if (Files.exists(gingerDir)) {
            throw new IOException("repository already exists at " + gingerDir);
        }

        Files.createDirectories(root);
        Path staging = Files.createTempDirectory(root, GINGER_DIR + "-init-");
        try {
            Files.createDirectory(staging.resolve("objects"));
            Files.createDirectories(staging.resolve("refs").resolve("heads"));
            Files.writeString(staging.resolve("config"), "version=" + FORMAT_VERSION + "\n");
            Files.writeString(staging.resolve("HEAD"), "ref: refs/heads/" + DEFAULT_BRANCH + "\n");
            Files.createFile(staging.resolve("index"));
            Files.move(staging, gingerDir);
        } catch (IOException | RuntimeException e) {
            deleteRecursively(staging);
            throw e;
        }

        return new Repository(root, gingerDir);
    }

    public static Repository open(Path workTree) throws IOException {
        Path root = workTree.toAbsolutePath().normalize();
        Repository repo = new Repository(root, root.resolve(GINGER_DIR));
        repo.validate();
        return repo;
    }

    public static Repository find(Path start) throws IOException {
        Path dir = start.toAbsolutePath().normalize();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve(GINGER_DIR))) {
                return open(dir);
            }
            dir = dir.getParent();
        }
        throw new RepositoryNotFoundException(start);
    }

    public Path configFile() {
        return gingerDir.resolve("config");
    }

    public Path headFile() {
        return gingerDir.resolve("HEAD");
    }

    public Path indexFile() {
        return gingerDir.resolve("index");
    }

    public Path objectsDir() {
        return gingerDir.resolve("objects");
    }

    public Path headsDir() {
        return gingerDir.resolve("refs").resolve("heads");
    }

    private void validate() throws IOException {
        List<Path> files = List.of(configFile(), headFile(), indexFile());
        List<Path> dirs = List.of(objectsDir(), headsDir());
        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                throw new IOException("not a valid ginger repository: missing " + gingerDir.relativize(file));
            }
        }
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) {
                throw new IOException("not a valid ginger repository: missing " + gingerDir.relativize(dir));
            }
        }

        String version = readConfig().get("version");
        if (!String.valueOf(FORMAT_VERSION).equals(version)) {
            throw new IOException("unsupported repository format version: " + version);
        }
    }

    private Map<String, String> readConfig() throws IOException {
        Map<String, String> config = new HashMap<>();
        for (String line : Files.readAllLines(configFile())) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                config.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
            }
        }
        return config;
    }

    private static void deleteRecursively(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
        }
    }
}
