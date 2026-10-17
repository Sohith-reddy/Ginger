package dev.ginger.commands;

import dev.ginger.cli.UsageException;
import dev.ginger.diff.RepositoryDiff;
import dev.ginger.repository.Repository;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

public final class DiffCommand implements Command {

    private final PrintStream out;
    private final Path workingDirectory;

    public DiffCommand(PrintStream out, Path workingDirectory) {
        this.out = out;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String name() {
        return "diff";
    }

    @Override
    public String description() {
        return "Show unstaged changes, or staged changes with --staged";
    }

    @Override
    public String usage() {
        return "ginger diff [--staged]";
    }

    @Override
    public void validate(List<String> args) throws UsageException {
        if (args.size() > 1 || (args.size() == 1 && !isStaged(args.get(0)))) {
            throw new UsageException("unknown arguments: " + String.join(" ", args));
        }
    }

    @Override
    public int execute(List<String> args) throws Exception {
        Repository repo = Repository.find(workingDirectory);
        String diff = args.isEmpty() ? RepositoryDiff.workTreeAgainstIndex(repo) : RepositoryDiff.indexAgainstHead(repo);
        out.print(diff);
        out.flush();
        return 0;
    }

    private static boolean isStaged(String arg) {
        return arg.equals("--staged") || arg.equals("--cached");
    }
}
