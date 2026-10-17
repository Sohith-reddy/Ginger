package dev.ginger.commands;

import dev.ginger.cli.UsageException;
import dev.ginger.ops.Staging;
import dev.ginger.repository.Repository;

import java.nio.file.Path;
import java.util.List;

public final class AddCommand implements Command {

    private final Path workingDirectory;

    public AddCommand(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String name() {
        return "add";
    }

    @Override
    public String description() {
        return "Stage file contents for the next commit";
    }

    @Override
    public String usage() {
        return "ginger add <path>...";
    }

    @Override
    public void validate(List<String> args) throws UsageException {
        if (args.isEmpty()) {
            throw new UsageException("nothing specified, nothing added");
        }
    }

    @Override
    public int execute(List<String> args) throws Exception {
        Staging.add(Repository.find(workingDirectory), workingDirectory, args);
        return 0;
    }
}
