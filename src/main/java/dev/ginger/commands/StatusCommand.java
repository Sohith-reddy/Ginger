package dev.ginger.commands;

import dev.ginger.cli.UsageException;
import dev.ginger.repository.Repository;
import dev.ginger.status.Status;
import dev.ginger.status.StatusCalculator;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

public final class StatusCommand implements Command {

    private final PrintStream out;
    private final Path workingDirectory;

    public StatusCommand(PrintStream out, Path workingDirectory) {
        this.out = out;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String description() {
        return "Show the working tree status";
    }

    @Override
    public String usage() {
        return "ginger status";
    }

    @Override
    public void validate(List<String> args) throws UsageException {
        if (!args.isEmpty()) {
            throw new UsageException("status takes no arguments");
        }
    }

    @Override
    public int execute(List<String> args) throws Exception {
        Status status = StatusCalculator.compute(Repository.find(workingDirectory));
        out.println(status.head().describe());
        if (!status.hasCommits()) {
            out.println();
            out.println("No commits yet");
        }

        if (status.isMerging()) {
            out.println();
            out.println("You are merging '" + status.mergingBranch() + "'.");
            if (status.conflicts().isEmpty()) {
                out.println("All conflicts fixed; run 'ginger commit' to finish the merge.");
            }
        }
        if (!status.conflicts().isEmpty()) {
            out.println();
            out.println("Unmerged paths (fix them, then run 'ginger add <path>'):");
            status.conflicts().forEach(p -> out.println("  unmerged:   " + p));
        }
        if (status.hasStagedChanges()) {
            out.println();
            out.println("Changes to be committed:");
            status.stagedAdded().forEach(p -> out.println("  new file:   " + p));
            status.stagedModified().forEach(p -> out.println("  modified:   " + p));
            status.stagedDeleted().forEach(p -> out.println("  deleted:    " + p));
        }
        if (status.hasUnstagedChanges()) {
            out.println();
            out.println("Changes not staged for commit:");
            status.modified().forEach(p -> out.println("  modified:   " + p));
            status.deleted().forEach(p -> out.println("  deleted:    " + p));
        }
        if (!status.untracked().isEmpty()) {
            out.println();
            out.println("Untracked files:");
            status.untracked().forEach(p -> out.println("  " + p));
        }
        if (status.isClean()) {
            out.println();
            out.println(status.hasCommits() ? "nothing to commit, working tree clean" : "nothing to commit");
        }
        return 0;
    }
}
