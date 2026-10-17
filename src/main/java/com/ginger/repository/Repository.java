package com.ginger.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A Ginger repository: a working tree plus its {@code .ginger} metadata directory.
 */
public record Repository(Path workTree, Path gingerDir) {

    public static final String GINGER_DIR = ".ginger";
    public static final String DEFAULT_BRANCH = "main";

    /**
     * Creates the on-disk layout for a new repository rooted at {@code workTree}.
     *
     * <pre>
     * .ginger/
     *   HEAD            -> "ref: refs/heads/main"
     *   objects/
     *   refs/heads/
     *   refs/tags/
     * </pre>
     */
    public static Repository init(Path workTree) throws IOException {
        Path gingerDir = workTree.resolve(GINGER_DIR);
        if (Files.exists(gingerDir)) {
            throw new IOException("repository already exists at " + gingerDir);
        }

        Files.createDirectories(gingerDir.resolve("objects"));
        Files.createDirectories(gingerDir.resolve("refs").resolve("heads"));
        Files.createDirectories(gingerDir.resolve("refs").resolve("tags"));
        Files.writeString(gingerDir.resolve("HEAD"), "ref: refs/heads/" + DEFAULT_BRANCH + "\n");

        return new Repository(workTree, gingerDir);
    }
}
