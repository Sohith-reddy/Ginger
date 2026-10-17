package dev.ginger.cli;

import java.util.List;

public final class CommandLineParser {

    public ParsedCommand parse(String[] args) throws UsageException {
        boolean debug = false;
        int i = 0;
        while (i < args.length && args[i].startsWith("-")) {
            switch (args[i]) {
                case "-h", "--help" -> {
                    return new ParsedCommand(ParsedCommand.Action.HELP, null, List.of(), debug);
                }
                case "-v", "--version" -> {
                    return new ParsedCommand(ParsedCommand.Action.VERSION, null, List.of(), debug);
                }
                case "--debug" -> debug = true;
                default -> throw new UsageException("unknown option: " + args[i]);
            }
            i++;
        }
        if (i == args.length || args[i].equals("help")) {
            return new ParsedCommand(ParsedCommand.Action.HELP, null, List.of(), debug);
        }
        List<String> rest = List.of(args).subList(i + 1, args.length);
        return new ParsedCommand(ParsedCommand.Action.RUN, args[i], List.copyOf(rest), debug);
    }
}
