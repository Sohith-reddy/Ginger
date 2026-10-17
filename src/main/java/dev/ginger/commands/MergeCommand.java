package dev.ginger.commands;

import dev.ginger.cli.UsageException;
import dev.ginger.ops.Identity;
import dev.ginger.ops.Merger;
import dev.ginger.repository.Repository;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;

public final class MergeCommand implements Command {

    private final PrintStream out;
    private final Path workingDirectory;
    private final Map<String, String> env;
    private final Clock clock;

    public MergeCommand(PrintStream out, Path workingDirectory, Map<String, String> env, Clock clock) {
        this.out = out;
        this.workingDirectory = workingDirectory;
        this.env = env;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "merge";
    }

    @Override
    public String description() {
        return "Merge a branch into the current branch";
    }

    @Override
    public String usage() {
        return "ginger merge <branch> | ginger merge --abort";
    }

    @Override
    public void validate(List<String> args) throws UsageException {
        if (args.size() != 1 || (args.get(0).startsWith("-") && !args.get(0).equals("--abort"))) {
            throw new UsageException("specify one branch to merge, or --abort");
        }
    }

    @Override
    public int execute(List<String> args) throws Exception {
        Repository repo = Repository.find(workingDirectory);
        if (args.get(0).equals("--abort")) {
            Merger.abort(repo);
            out.println("Merge aborted");
            return 0;
        }

        String branch = args.get(0);
        Merger.Result result = Merger.merge(repo, branch, Identity.author(repo, env), clock.instant());
        switch (result.outcome()) {
            case UP_TO_DATE -> out.println("Already up to date.");
            case FAST_FORWARD -> out.println("Fast-forward to " + result.commit().abbreviate());
            case MERGED -> out.println("Merged '" + branch + "' in commit " + result.commit().abbreviate());
            case CONFLICTED -> {
                for (Merger.Conflict conflict : result.conflicts()) {
                    out.println("CONFLICT (" + conflict.kind() + "): " + conflict.path());
                }
                out.println("Automatic merge failed; fix the conflicts, run 'ginger add <path>', then 'ginger commit'.");
                return 1;
            }
        }
        return 0;
    }
}
