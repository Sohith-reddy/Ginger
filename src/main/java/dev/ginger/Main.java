package dev.ginger;

import dev.ginger.command.Command;
import dev.ginger.command.InitCommand;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Entry point for the Ginger command-line interface.
 */
public final class Main {

    public static final String VERSION = "0.1.0";

    private final Map<String, Command> commands = new LinkedHashMap<>();

    public Main() {
        register(new InitCommand());
    }

    private void register(Command command) {
        commands.put(command.name(), command);
    }

    public int run(String[] args) {
        if (args.length == 0) {
            printUsage();
            return 0;
        }

        return switch (args[0]) {
            case "help", "--help", "-h" -> {
                printUsage();
                yield 0;
            }
            case "--version", "-v" -> {
                System.out.println("ginger version " + VERSION);
                yield 0;
            }
            default -> dispatch(args[0], Arrays.copyOfRange(args, 1, args.length));
        };
    }

    private int dispatch(String name, String[] args) {
        Command command = commands.get(name);
        if (command == null) {
            System.err.println("ginger: '" + name + "' is not a ginger command. See 'ginger help'.");
            return 1;
        }
        try {
            return command.execute(args);
        } catch (Exception e) {
            System.err.println("fatal: " + e.getMessage());
            return 128;
        }
    }

    private void printUsage() {
        System.out.println("usage: ginger <command> [<args>]");
        System.out.println();
        System.out.println("Commands:");
        commands.values().forEach(c -> System.out.printf("   %-10s %s%n", c.name(), c.description()));
    }

    public static void main(String[] args) {
        System.exit(new Main().run(args));
    }
}
