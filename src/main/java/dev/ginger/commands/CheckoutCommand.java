package dev.ginger.commands;

import dev.ginger.cli.UsageException;
import dev.ginger.objects.Commit;
import dev.ginger.objects.ObjectId;
import dev.ginger.objects.Snapshots;
import dev.ginger.ops.Checkout;
import dev.ginger.refs.Head;
import dev.ginger.repository.Repository;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

public final class CheckoutCommand implements Command {

    private final PrintStream out;
    private final Path workingDirectory;

    public CheckoutCommand(PrintStream out, Path workingDirectory) {
        this.out = out;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String name() {
        return "checkout";
    }

    @Override
    public String description() {
        return "Check out a commit with a detached HEAD";
    }

    @Override
    public String usage() {
        return "ginger checkout [--force] <commit-id>";
    }

    @Override
    public void validate(List<String> args) throws UsageException {
        SwitchCommand.validateTarget(args);
    }

    @Override
    public int execute(List<String> args) throws Exception {
        Repository repo = Repository.find(workingDirectory);
        String target = SwitchCommand.target(args);
        ObjectId id = repo.objects().resolve(target);
        Commit commit = Snapshots.readCommit(repo.objects(), id);

        Checkout.moveTo(repo, id, Head.detachedAt(id), SwitchCommand.force(args));
        out.println("HEAD is now at " + id.abbreviate() + " " + commit.summary());
        return 0;
    }
}
