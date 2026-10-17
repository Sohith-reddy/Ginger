package dev.ginger.command;

import dev.ginger.repository.Repository;

import java.nio.file.Path;

/**
 * {@code ginger init [<directory>]} — creates an empty Ginger repository.
 */
public final class InitCommand implements Command {

    @Override
    public String name() {
        return "init";
    }

    @Override
    public String description() {
        return "Create an empty Ginger repository";
    }

    @Override
    public int execute(String[] args) throws Exception {
        Path workTree = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Repository repo = Repository.init(workTree);
        System.out.println("Initialized empty Ginger repository in " + repo.gingerDir());
        return 0;
    }
}
