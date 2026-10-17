package dev.ginger.commands;

import dev.ginger.cli.UsageException;
import dev.ginger.ops.Restorer;
import dev.ginger.repository.Repository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RestoreCommand implements Command {

    private final Path workingDirectory;

    public RestoreCommand(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String name() {
        return "restore";
    }

    @Override
    public String description() {
        return "Restore files from the index, or unstage them with --staged";
    }

    @Override
    public String usage() {
        return "ginger restore [--staged] [--force] <path>...";
    }

    @Override
    public void validate(List<String> args) throws UsageException {
        if (paths(args).isEmpty()) {
            throw new UsageException("you must specify path(s) to restore");
        }
        for (String arg : args) {
            if (arg.startsWith("-") && !arg.equals("--staged") && !arg.equals("--force") && !arg.equals("-f")) {
                throw new UsageException("unknown option: " + arg);
            }
        }
    }

    @Override
    public int execute(List<String> args) throws Exception {
        Repository repo = Repository.find(workingDirectory);
        if (args.contains("--staged")) {
            Restorer.unstage(repo, workingDirectory, paths(args));
        } else {
            boolean force = args.contains("--force") || args.contains("-f");
            Restorer.restoreWorkTree(repo, workingDirectory, paths(args), force);
        }
        return 0;
    }

    private static List<String> paths(List<String> args) {
        List<String> paths = new ArrayList<>();
        for (String arg : args) {
            if (!arg.startsWith("-")) {
                paths.add(arg);
            }
        }
        return paths;
    }
}
