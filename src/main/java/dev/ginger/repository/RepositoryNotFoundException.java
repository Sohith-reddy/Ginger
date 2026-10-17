package dev.ginger.repository;

import java.io.IOException;
import java.nio.file.Path;

public class RepositoryNotFoundException extends IOException {

    public RepositoryNotFoundException(Path start) {
        super("not a ginger repository (or any of the parent directories): " + start.toAbsolutePath().normalize());
    }
}
