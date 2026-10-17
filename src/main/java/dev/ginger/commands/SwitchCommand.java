package dev.ginger.commands;

import dev.ginger.cli.UsageException;
import dev.ginger.objects.ObjectId;
import dev.ginger.ops.Checkout;
import dev.ginger.refs.Head;
import dev.ginger.refs.Refs;
import dev.ginger.repository.Repository;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

public final class SwitchCommand implements Command {

    private final PrintStream out;
    private final Path workingDirectory;

    public SwitchCommand(PrintStream out, Path workingDirectory) {
        this.out = out;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String name() {
        return "switch";
    }

    @Override
    public String description() {
        return "Switch to a branch";
    }

    @Override
    public String usage() {
        return "ginger switch [--force] <branch>";
    }

    @Override
    public void validate(List<String> args) throws UsageException {
        validateTarget(args);
    }

    @Override
    public int execute(List<String> args) throws Exception {
        Repository repo = Repository.find(workingDirectory);
        Refs refs = repo.refs();
        String branch = target(args);
        try {
            Refs.validateBranchName(branch);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage());
        }

        Head head = refs.head();
        if (branch.equals(head.branch())) {
            out.println("Already on '" + branch + "'");
            return 0;
        }
        ObjectId target = refs.branch(branch)
                .orElseThrow(() -> new IOException("invalid reference: " + branch));

        Checkout.moveTo(repo, target, Head.onBranch(branch), force(args));
        out.println("Switched to branch '" + branch + "'");
        return 0;
    }

    static void validateTarget(List<String> args) throws UsageException {
        List<String> rest = args.stream().filter(a -> !a.equals("--force") && !a.equals("-f")).toList();
        if (rest.size() != 1 || rest.get(0).startsWith("-")) {
            throw new UsageException(rest.isEmpty() ? "missing target" : "unexpected arguments: " + String.join(" ", rest));
        }
    }

    static String target(List<String> args) {
        return args.stream().filter(a -> !a.startsWith("-")).findFirst().orElseThrow();
    }

    static boolean force(List<String> args) {
        return args.contains("--force") || args.contains("-f");
    }
}
