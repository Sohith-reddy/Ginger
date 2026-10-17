package dev.ginger.commands;

import dev.ginger.cli.UsageException;
import dev.ginger.objects.ObjectId;
import dev.ginger.refs.Head;
import dev.ginger.refs.Refs;
import dev.ginger.repository.Repository;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

public final class BranchCommand implements Command {

    private final PrintStream out;
    private final Path workingDirectory;

    public BranchCommand(PrintStream out, Path workingDirectory) {
        this.out = out;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String name() {
        return "branch";
    }

    @Override
    public String description() {
        return "List branches, or create one at the current commit";
    }

    @Override
    public String usage() {
        return "ginger branch [<name>]";
    }

    @Override
    public void validate(List<String> args) throws UsageException {
        if (args.size() > 1) {
            throw new UsageException("too many arguments");
        }
        if (args.size() == 1) {
            try {
                Refs.validateBranchName(args.get(0));
            } catch (IllegalArgumentException e) {
                throw new UsageException("'" + args.get(0) + "' is not a valid branch name");
            }
        }
    }

    @Override
    public int execute(List<String> args) throws Exception {
        Repository repo = Repository.find(workingDirectory);
        Refs refs = repo.refs();
        Head head = refs.head();

        if (args.isEmpty()) {
            if (head.isDetached()) {
                out.println("* (HEAD detached at " + head.detached().abbreviate() + ")");
            }
            for (String branch : refs.branches()) {
                out.println((branch.equals(head.branch()) ? "* " : "  ") + branch);
            }
            return 0;
        }

        String name = args.get(0);
        if (refs.branch(name).isPresent()) {
            throw new IOException("a branch named '" + name + "' already exists");
        }
        ObjectId commit = refs.headCommit()
                .orElseThrow(() -> new IOException("cannot create branch '" + name + "': no commits yet"));
        refs.updateBranch(name, commit);
        return 0;
    }
}
