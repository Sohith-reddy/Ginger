package dev.ginger.commands;

import dev.ginger.cli.UsageException;
import dev.ginger.objects.Commit;
import dev.ginger.objects.ObjectId;
import dev.ginger.objects.Snapshots;
import dev.ginger.refs.Head;
import dev.ginger.repository.Repository;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class LogCommand implements Command {

    private final PrintStream out;
    private final Path workingDirectory;

    public LogCommand(PrintStream out, Path workingDirectory) {
        this.out = out;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String name() {
        return "log";
    }

    @Override
    public String description() {
        return "Show commit history";
    }

    @Override
    public String usage() {
        return "ginger log";
    }

    @Override
    public void validate(List<String> args) throws UsageException {
        if (!args.isEmpty()) {
            throw new UsageException("log takes no arguments");
        }
    }

    @Override
    public int execute(List<String> args) throws Exception {
        Repository repo = Repository.find(workingDirectory);
        Optional<ObjectId> start = repo.refs().headCommit();
        if (start.isEmpty()) {
            Head head = repo.refs().head();
            out.println("No commits yet on branch " + head.branch());
            return 0;
        }

        Set<ObjectId> seen = new HashSet<>();
        ObjectId current = start.get();
        boolean first = true;
        while (current != null) {
            if (!seen.add(current)) {
                throw new IOException("commit history loops at " + current);
            }
            Commit commit = Snapshots.readCommit(repo.objects(), current);
            if (!first) {
                out.println();
            }
            out.println("commit " + current);
            if (commit.parents().size() > 1) {
                out.println("Merge:  " + String.join(" ", commit.parents().stream().map(ObjectId::abbreviate).toList()));
            }
            out.println("Author: " + commit.author());
            out.println("Date:   " + commit.timestamp());
            out.println();
            commit.message().lines().forEach(line -> out.println("    " + line));
            first = false;
            current = commit.parents().isEmpty() ? null : commit.parents().get(0);
        }
        return 0;
    }
}
