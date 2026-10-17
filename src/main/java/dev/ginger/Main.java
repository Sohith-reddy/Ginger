package dev.ginger;

import dev.ginger.cli.CommandLineParser;
import dev.ginger.cli.CommandRegistry;
import dev.ginger.cli.ParsedCommand;
import dev.ginger.cli.UsageException;
import dev.ginger.commands.AddCommand;
import dev.ginger.commands.Command;
import dev.ginger.commands.CommitCommand;
import dev.ginger.commands.DiffCommand;
import dev.ginger.commands.InitCommand;
import dev.ginger.commands.LogCommand;
import dev.ginger.commands.StatusCommand;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

public final class Main {

    public static final String VERSION = "0.1.0";

    private final CommandLineParser parser;
    private final CommandRegistry registry;
    private final PrintStream out;
    private final PrintStream err;

    public Main(CommandLineParser parser, CommandRegistry registry, PrintStream out, PrintStream err) {
        this.parser = parser;
        this.registry = registry;
        this.out = out;
        this.err = err;
    }

    public int run(String[] args) {
        ParsedCommand parsed;
        try {
            parsed = parser.parse(args);
        } catch (UsageException e) {
            err.println("ginger: " + e.getMessage());
            err.println("See 'ginger --help'.");
            return 2;
        }

        return switch (parsed.action()) {
            case HELP -> {
                printHelp();
                yield 0;
            }
            case VERSION -> {
                out.println("ginger version " + VERSION);
                yield 0;
            }
            case RUN -> dispatch(parsed);
        };
    }

    private int dispatch(ParsedCommand parsed) {
        Optional<Command> found = registry.find(parsed.name());
        if (found.isEmpty()) {
            err.println("ginger: '" + parsed.name() + "' is not a ginger command. See 'ginger --help'.");
            return 1;
        }
        Command command = found.get();
        List<String> args = parsed.arguments();

        if (args.contains("-h") || args.contains("--help")) {
            out.println("usage: " + command.usage());
            return 0;
        }

        try {
            command.validate(args);
            return command.execute(args);
        } catch (UsageException e) {
            err.println("error: " + e.getMessage());
            err.println("usage: " + command.usage());
            return 2;
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            err.println("fatal: " + message);
            if (parsed.debug()) {
                e.printStackTrace(err);
            }
            return 128;
        }
    }

    private void printHelp() {
        out.println("usage: ginger [--debug] <command> [<args>]");
        out.println("       ginger --help | --version");
        out.println();
        out.println("Commands:");
        registry.all().forEach(c -> out.printf("   %-10s %s%n", c.name(), c.description()));
    }

    public static void main(String[] args) {
        Path cwd = Path.of("").toAbsolutePath();
        CommandRegistry registry = new CommandRegistry()
                .register(new InitCommand(System.out, cwd))
                .register(new AddCommand(cwd))
                .register(new CommitCommand(System.out, cwd, System.getenv(), Clock.systemUTC()))
                .register(new StatusCommand(System.out, cwd))
                .register(new LogCommand(System.out, cwd))
                .register(new DiffCommand(System.out, cwd));
        Main main = new Main(new CommandLineParser(), registry, System.out, System.err);
        System.exit(main.run(args));
    }
}
