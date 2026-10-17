package dev.ginger;

import dev.ginger.ops.Committer;
import dev.ginger.ops.Staging;
import dev.ginger.objects.ObjectId;
import dev.ginger.repository.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public final class TestRepo {

    private final Repository repo;
    private Instant time = Instant.parse("2026-01-01T00:00:00Z");

    private TestRepo(Repository repo) {
        this.repo = repo;
    }

    public static TestRepo init(Path dir) throws IOException {
        return new TestRepo(Repository.init(dir));
    }

    public Repository repo() {
        return repo;
    }

    public Path root() {
        return repo.workTree();
    }

    public TestRepo write(String path, String content) throws IOException {
        Path file = root().resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return this;
    }

    public String read(String path) throws IOException {
        return Files.readString(root().resolve(path), StandardCharsets.UTF_8);
    }

    public boolean exists(String path) {
        return Files.exists(root().resolve(path));
    }

    public TestRepo delete(String path) throws IOException {
        Files.delete(root().resolve(path));
        return this;
    }

    public TestRepo add(String... paths) throws IOException {
        Staging.add(repo, root(), List.of(paths));
        return this;
    }

    public ObjectId commit(String message) throws IOException {
        time = time.plusSeconds(60);
        return Committer.commit(repo, message, "Test <test@example.com>", time).orElseThrow().id();
    }
}
