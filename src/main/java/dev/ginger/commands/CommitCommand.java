package dev.ginger.commands;

import dev.ginger.cli.UsageException;
import dev.ginger.ops.Committer;
import dev.ginger.ops.Identity;
import dev.ginger.repository.Repository;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CommitCommand implements Command {

    private final PrintStream out;
    private final Path workingDirectory;
    private final Map<String, String> env;
    private final Clock clock;

    public CommitCommand(PrintStream out, Path workingDirectory, Map<String, String> env, Clock clock) {
        this.out = out;
        this.workingDirectory = workingDirectory;
        this.env = env;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "commit";
    }

    @Override
    public String description() {
        return "Record staged changes";
    }

    @Override
    public String usage() {
        return "ginger commit -m <message>";
    }

    @Override
    public void validate(List<String> args) throws UsageException {
        if (args.size() != 2 || !(args.get(0).equals("-m") || args.get(0).equals("--message"))) {
            throw new UsageException("a commit message is required");
        }
        if (args.get(1).isBlank()) {
            throw new UsageException("aborting commit due to empty commit message");
        }
    }

    @Override
    public int execute(List<String> args) throws Exception {
        Repository repo = Repository.find(workingDirectory);
        Optional<Committer.Result> result = Committer.commit(repo, args.get(1), Identity.author(repo, env), clock.instant());
        if (result.isEmpty()) {
            out.println("nothing to commit");
            return 1;
        }

        Committer.Result r = result.get();
        String where = r.head().isDetached() ? "detached HEAD" : r.head().branch();
        String root = r.commit().parents().isEmpty() ? " (root-commit)" : "";
        out.println("[" + where + root + " " + r.id().abbreviate() + "] " + r.commit().summary());
        return 0;
    }
}
