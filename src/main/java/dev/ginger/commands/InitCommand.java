package dev.ginger.commands;

import dev.ginger.cli.UsageException;
import dev.ginger.repository.Repository;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

public final class InitCommand implements Command {

    private final PrintStream out;

    public InitCommand(PrintStream out) {
        this.out = out;
    }

    @Override
    public String name() {
        return "init";
    }

    @Override
    public String description() {
        return "Create an empty Ginger repository";
    }

    @Override
    public String usage() {
        return "ginger init [<directory>]";
    }

    @Override
    public void validate(List<String> args) throws UsageException {
        if (args.size() > 1) {
            throw new UsageException("too many arguments");
        }
    }

    @Override
    public int execute(List<String> args) throws Exception {
        Path workTree = Path.of(args.isEmpty() ? "." : args.get(0)).toAbsolutePath().normalize();
        Repository repo = Repository.init(workTree);
        out.println("Initialized empty Ginger repository in " + repo.gingerDir());
        return 0;
    }
}
